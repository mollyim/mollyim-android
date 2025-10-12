import os
import uuid
from datetime import datetime, timezone
from typing import Optional, List, Dict, Any

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from motor.motor_asyncio import AsyncIOMotorClient

# -----------------------------------------------------------------------------
# Environment & App
# -----------------------------------------------------------------------------
MONGO_URL = os.environ.get("MONGO_URL")
USE_GOOGLE_MAPS_LINKS = os.environ.get("USE_GOOGLE_MAPS_LINKS", "false").lower() == "true"

app = FastAPI(title="Conversations API", openapi_url="/api/openapi.json")

# All routes must be prefixed with /api per ingress rules
API_PREFIX = "/api"

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# -----------------------------------------------------------------------------
# Database (lazy init to avoid crash if env missing)
# -----------------------------------------------------------------------------
_client: AsyncIOMotorClient | None = None
_db = None
conversations_col = None
reactions_col = None

async def ensure_db():
    global _client, _db, conversations_col, reactions_col
    if _client is not None:
        return
    if not MONGO_URL:
        return
    _client = AsyncIOMotorClient(MONGO_URL)
    _db = _client["app_db"]
    conversations_col = _db["conversations"]
    reactions_col = _db["reactions"]

# -----------------------------------------------------------------------------
# Models
# -----------------------------------------------------------------------------
class ConversationCreate(BaseModel):
    title: str
    address: Optional[str] = None

class ConversationOut(BaseModel):
    id: str = Field(alias="_id")
    title: str
    address: Optional[str] = None
    createdAt: str
    lastReactionAt: Optional[str] = None
    unseenCount: Optional[int] = 0

class ReactionCreate(BaseModel):
    conversationId: str
    type: str
    createdBy: str

class MarkSeenRequest(BaseModel):
    conversationId: str
    userId: str

# -----------------------------------------------------------------------------
# Helpers
# -----------------------------------------------------------------------------

def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()

async def serialize_conversation(doc: Dict[str, Any]) -> Dict[str, Any]:
    if not doc:
        return {}
    return {
        "_id": doc.get("_id"),
        "title": doc.get("title"),
        "address": doc.get("address"),
        "createdAt": doc.get("createdAt"),
        "lastReactionAt": doc.get("lastReactionAt"),
    }

async def compute_unseen_count(conversation_id: str, user_id: str) -> int:
    # Determine last seen time for the user
    conv = await conversations_col.find_one({"_id": conversation_id})
    if not conv:
        return 0
    last_seen_map: Dict[str, str] = conv.get("lastSeenBy", {}) or {}
    last_seen_str = last_seen_map.get(user_id)

    if last_seen_str:
        last_seen_dt = datetime.fromisoformat(last_seen_str)
@app.get(f"{API_PREFIX}/health")
async def health():
    db_ok = bool(MONGO_URL)
    return {"ok": True, "useGoogleMapsLinks": USE_GOOGLE_MAPS_LINKS, "dbConfigured": db_ok}


        q = {
            "conversationId": conversation_id,
            "createdAt": {"$gt": last_seen_dt.isoformat()},
            # optionally exclude user's own reactions from unseen
            "createdBy": {"$ne": user_id},
        }
    else:
        # If never seen, all reactions by others are unseen
        q = {
            "conversationId": conversation_id,
            "createdBy": {"$ne": user_id},
        }

    return await reactions_col.count_documents(q)

# -----------------------------------------------------------------------------
# Routes
# -----------------------------------------------------------------------------
@app.get(f"{API_PREFIX}/health")
async def health():
    return {"ok": True, "useGoogleMapsLinks": USE_GOOGLE_MAPS_LINKS, "dbConfigured": bool(MONGO_URL)}

@app.post(f"{API_PREFIX}/conversations", response_model=ConversationOut)
async def create_conversation(payload: ConversationCreate):
    await ensure_db()
    if conversations_col is None:
        raise HTTPException(status_code=500, detail="Database not configured")

    conv_id = str(uuid.uuid4())
    doc = {
        "_id": conv_id,
        "title": payload.title,
        "address": payload.address,
        "createdAt": now_iso(),
        "lastReactionAt": None,
        # Track last seen per user: { userId: iso-string }
        "lastSeenBy": {},
    }
    await conversations_col.insert_one(doc)
    out = await serialize_conversation(doc)
    out["unseenCount"] = 0
    return out

@app.get(f"{API_PREFIX}/conversations", response_model=List[ConversationOut])
async def list_conversations(
    userId: Optional[str] = Query(None, description="User ID to compute unseen counts for"),
    folder: str = Query("all", description="Folder filter: all | unseen"),
):
    await ensure_db()
    if conversations_col is None:
        raise HTTPException(status_code=500, detail="Database not configured")

    cursor = conversations_col.find({}, sort=[("createdAt", -1)])
    results: List[Dict[str, Any]] = []
    async for doc in cursor:
        item = await serialize_conversation(doc)
        if userId:
            unseen = await compute_unseen_count(doc["_id"], userId)
            item["unseenCount"] = unseen
        else:
            item["unseenCount"] = 0
        results.append(item)

    if folder == "unseen" and userId:
        results = [c for c in results if (c.get("unseenCount", 0) or 0) > 0]

    return results

@app.get(f"{API_PREFIX}/conversations/{{conversation_id}}/unseen-count")
async def get_unseen_count(conversation_id: str, userId: str):
    await ensure_db()
    if conversations_col is None:
        raise HTTPException(status_code=500, detail="Database not configured")
    count = await compute_unseen_count(conversation_id, userId)
    return {"conversationId": conversation_id, "userId": userId, "unseenCount": count}

@app.post(f"{API_PREFIX}/reactions")
async def add_reaction(payload: ReactionCreate):
    await ensure_db()
    if conversations_col is None or reactions_col is None:
        raise HTTPException(status_code=500, detail="Database not configured")
    # Validate conversation exists
    conv = await conversations_col.find_one({"_id": payload.conversationId})
    if not conv:
        raise HTTPException(status_code=404, detail="Conversation not found")

    react_id = str(uuid.uuid4())
    doc = {
        "_id": react_id,
        "conversationId": payload.conversationId,
        "type": payload.type,
        "createdBy": payload.createdBy,
        "createdAt": now_iso(),
    }
    await reactions_col.insert_one(doc)

    await conversations_col.update_one(
        {"_id": payload.conversationId}, {"$set": {"lastReactionAt": now_iso()}}
    )

    return {"ok": True, "reactionId": react_id}

@app.post(f"{API_PREFIX}/reactions/mark-seen")
async def mark_seen(payload: MarkSeenRequest):
    await ensure_db()
    if conversations_col is None:
        raise HTTPException(status_code=500, detail="Database not configured")
    conv = await conversations_col.find_one({"_id": payload.conversationId})
    if not conv:
        raise HTTPException(status_code=404, detail="Conversation not found")
    last_seen = conv.get("lastSeenBy", {}) or {}
    last_seen[payload.userId] = now_iso()
    await conversations_col.update_one({"_id": payload.conversationId}, {"$set": {"lastSeenBy": last_seen}})
    return {"ok": True}

# Optional endpoint to expose config to frontend if needed
@app.get(f"{API_PREFIX}/config")
async def get_config():
    return {
        "useGoogleMapsLinks": USE_GOOGLE_MAPS_LINKS,
    }

# The server must be bound to 0.0.0.0:8001 via supervisor; no uvicorn.run() here.

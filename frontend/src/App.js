import React, { useEffect, useMemo, useState } from "react";
import axios from "axios";
import "./App.css";

function joinUrl(base, path) {
  if (!base) return path; // rely on ingress mapping if env is injected at deployment
  const trimmed = base.endsWith("/") ? base.slice(0, -1) : base;
  return `${trimmed}${path}`;
}

const BACKEND_URL = process.env.REACT_APP_BACKEND_URL; // MUST be used per ingress rules

function App() {
  const [userId, setUserId] = useState("user-1");
  const [folder, setFolder] = useState("all"); // all | unseen
  const [loading, setLoading] = useState(false);
  const [conversations, setConversations] = useState([]);
  const [title, setTitle] = useState("");
  const [address, setAddress] = useState("");
  const [mapsEnabled, setMapsEnabled] = useState(false);
  const [error, setError] = useState("");

  const api = useMemo(() => {
    return axios.create({
      baseURL: BACKEND_URL,
      headers: { "Content-Type": "application/json" },
    });
  }, []);

  const fetchConfig = async () => {
    try {
      const url = joinUrl(BACKEND_URL, "/config");
      const { data } = await api.get(url);
      setMapsEnabled(Boolean(data?.useGoogleMapsLinks));
    } catch (e) {
      // default false per requirement
      setMapsEnabled(false);
    }
  };

  const loadConversations = async () => {
    setLoading(true);
    setError("");
    try {
      const url = joinUrl(BACKEND_URL, `/conversations?folder=${folder}&userId=${encodeURIComponent(userId)}`);
      const { data } = await api.get(url);
      setConversations(data || []);
    } catch (e) {
      setError("Failed to load conversations");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchConfig();
  }, []);

  useEffect(() => {
    loadConversations();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [folder, userId]);

  const createConversation = async () => {
    setError("");
    try {
      const url = joinUrl(BACKEND_URL, "/conversations");
      await api.post(url, { title, address });
      setTitle("");
      setAddress("");
      await loadConversations();
    } catch (e) {
      setError("Failed to create conversation");
    }
  };

  const addReaction = async (conversationId) => {
    setError("");
    try {
      const url = joinUrl(BACKEND_URL, "/reactions");
      await api.post(url, { conversationId, type: "like", createdBy: userId });
      await loadConversations();
    } catch (e) {
      setError("Failed to add reaction");
    }
  };

  const markSeen = async (conversationId) => {
    setError("");
    try {
      const url = joinUrl(BACKEND_URL, "/reactions/mark-seen");
      await api.post(url, { conversationId, userId });
      await loadConversations();
    } catch (e) {
      setError("Failed to mark seen");
    }
  };

  return (
    <div data-testid="app-root" className="min-h-screen bg-slate-50 text-slate-900">
      <div className="flex h-screen">
        <aside className="w-64 bg-white border-r border-slate-200 p-4 space-y-4">
          <div className="space-y-2">
            <label className="text-sm font-medium">User ID</label>
            <input
              data-testid="user-id-input"
              className="w-full px-3 py-2 border rounded"
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              placeholder="user-1"
            />
          </div>
          <nav className="space-y-1">
            <button
              data-testid="folder-all"
              className={`w-full text-left px-3 py-2 rounded ${folder === "all" ? "bg-slate-900 text-white" : "hover:bg-slate-100"}`}
              onClick={() => setFolder("all")}
            >
              All Conversations
            </button>
            <button
              data-testid="folder-unseen-reactions"
              className={`w-full text-left px-3 py-2 rounded ${folder === "unseen" ? "bg-slate-900 text-white" : "hover:bg-slate-100"}`}
              onClick={() => setFolder("unseen")}
            >
              Unseen Reactions
            </button>
          </nav>
          <div className="pt-4 border-t border-slate-200">
            <div className="text-xs text-slate-500">Maps Links: {mapsEnabled ? "Google Maps ON" : "OFF by default"}</div>
          </div>
        </aside>
        <main className="flex-1 p-6 overflow-y-auto space-y-6">
          <h1 className="text-2xl font-semibold" data-testid="page-title">Conversations</h1>

          <div className="bg-white border rounded p-4 space-y-3">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <input
                data-testid="new-conv-title"
                className="px-3 py-2 border rounded"
                placeholder="Conversation title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
              />
              <input
                data-testid="new-conv-address"
                className="px-3 py-2 border rounded"
                placeholder="Optional address (for maps link)"
                value={address}
                onChange={(e) => setAddress(e.target.value)}
              />
              <button
                data-testid="create-conversation-btn"
                className="px-3 py-2 bg-slate-900 text-white rounded"
                onClick={createConversation}
              >
                Create Conversation
              </button>
            </div>
          </div>

          {error && (
            <div data-testid="error-banner" className="bg-red-50 text-red-800 px-4 py-2 rounded border border-red-200">{error}</div>
          )}

          <div className="bg-white border rounded">
            <div className="px-4 py-2 border-b flex items-center justify-between">
              <div className="text-sm text-slate-600">Folder: {folder}</div>
              {loading && <div data-testid="loading-indicator" className="text-sm text-slate-500">Loading...</div>}
            </div>
            <ul className="divide-y">
              {conversations.map((c) => (
                <li key={c.id} className="p-4 flex items-center justify-between" data-testid={`conversation-row-${c.id}`}>
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium">{c.title}</span>
                      {(c.unseenCount || 0) > 0 && (
                        <span data-testid={`unseen-count-${c.id}`} className="text-xs bg-amber-100 text-amber-800 px-2 py-0.5 rounded-full">
                          {c.unseenCount} unseen
                        </span>
                      )}
                    </div>
                    {c.address && (
                      <div className="text-xs text-slate-500">
                        {mapsEnabled ? (
                          <a
                            data-testid={`maps-link-${c.id}`}
                            className="text-blue-600 hover:underline"
                            href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(c.address)}`}
                            target="_blank" rel="noreferrer"
                          >
                            View on Google Maps
                          </a>
                        ) : (
                          <span data-testid={`maps-disabled-${c.id}`}>Maps link disabled by config</span>
                        )}
                      </div>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      data-testid={`add-reaction-${c.id}`}
                      className="px-3 py-1.5 bg-slate-100 hover:bg-slate-200 rounded"
                      onClick={() => addReaction(c.id)}
                    >
                      Add Reaction
                    </button>
                    <button
                      data-testid={`mark-seen-${c.id}`}
                      className="px-3 py-1.5 bg-slate-900 text-white rounded"
                      onClick={() => markSeen(c.id)}
                    >
                      Mark Seen
                    </button>
                  </div>
                </li>
              ))}
              {!loading && conversations.length === 0 && (
                <li className="p-6 text-slate-500" data-testid="empty-state">No conversations</li>
              )}
            </ul>
          </div>
        </main>
      </div>
    </div>
  );
}

export default App;

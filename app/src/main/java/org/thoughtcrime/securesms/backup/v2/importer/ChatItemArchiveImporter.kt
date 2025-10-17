/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.importer

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import org.signal.core.util.Base64
import org.signal.core.util.Hex
import org.signal.core.util.SqlUtil
import org.signal.core.util.asList
import org.signal.core.util.forEach
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.core.util.requireLong
import org.signal.core.util.toInt
import org.signal.core.util.update
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.attachments.TombstoneAttachment
import org.thoughtcrime.securesms.backup.v2.ImportSkips
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.proto.BodyRange
import org.thoughtcrime.securesms.backup.v2.proto.ChatItem
import org.thoughtcrime.securesms.backup.v2.proto.ChatUpdateMessage
import org.thoughtcrime.securesms.backup.v2.proto.ContactAttachment
import org.thoughtcrime.securesms.backup.v2.proto.DirectStoryReplyMessage
import org.thoughtcrime.securesms.backup.v2.proto.GroupCall
import org.thoughtcrime.securesms.backup.v2.proto.IndividualCall
import org.thoughtcrime.securesms.backup.v2.proto.LinkPreview
import org.thoughtcrime.securesms.backup.v2.proto.MessageAttachment
import org.thoughtcrime.securesms.backup.v2.proto.Quote
import org.thoughtcrime.securesms.backup.v2.proto.Reaction
import org.thoughtcrime.securesms.backup.v2.proto.SendStatus
import org.thoughtcrime.securesms.backup.v2.proto.SimpleChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.StandardMessage
import org.thoughtcrime.securesms.backup.v2.proto.Sticker
import org.thoughtcrime.securesms.backup.v2.proto.ViewOnceMessage
import org.thoughtcrime.securesms.backup.v2.util.toLocalAttachment
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.GroupReceiptTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.ReactionTable
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.recipients
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchSet
import org.thoughtcrime.securesms.database.documents.NetworkFailure
import org.thoughtcrime.securesms.database.documents.NetworkFailureSet
import org.thoughtcrime.securesms.database.model.GroupCallUpdateDetailsUtil
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.GV2UpdateDescription
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras
import org.thoughtcrime.securesms.database.model.databaseprotos.PollTerminate
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails
import org.thoughtcrime.securesms.database.model.databaseprotos.SessionSwitchoverEvent
import org.thoughtcrime.securesms.database.model.databaseprotos.ThreadMergeEvent
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.polls.Voter
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.MessageUtil
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.push.DataMessage
import java.math.BigInteger
import java.util.Optional
import java.util.UUID
import org.thoughtcrime.securesms.backup.v2.proto.GiftBadge as BackupGiftBadge

/**
 * An object that will ingest all of the [ChatItem]s you want to write, buffer them until hitting a specified batch size, and then batch insert them
 * for fast throughput.
 */
class ChatItemArchiveImporter(
  private val db: SQLiteDatabase,
  private val importState: ImportState,
  private val batchSize: Int
) {
  companion object {
    private val TAG = Log.tag(ChatItemArchiveImporter::class.java)

    private val MESSAGE_COLUMNS = arrayOf(
      MessageTable.DATE_SENT,
      MessageTable.DATE_RECEIVED,
      MessageTable.DATE_SERVER,
      MessageTable.RECEIPT_TIMESTAMP,
      MessageTable.TYPE,
      MessageTable.THREAD_ID,
      MessageTable.READ,
      MessageTable.BODY,
      MessageTable.FROM_RECIPIENT_ID,
      MessageTable.TO_RECIPIENT_ID,
      MessageTable.HAS_DELIVERY_RECEIPT,
      MessageTable.HAS_READ_RECEIPT,
      MessageTable.VIEWED_COLUMN,
      MessageTable.MISMATCHED_IDENTITIES,
      MessageTable.EXPIRES_IN,
      MessageTable.EXPIRE_STARTED,
      MessageTable.UNIDENTIFIED,
      MessageTable.REMOTE_DELETED,
      MessageTable.NETWORK_FAILURES,
      MessageTable.QUOTE_ID,
      MessageTable.QUOTE_AUTHOR,
      MessageTable.QUOTE_BODY,
      MessageTable.QUOTE_MISSING,
      MessageTable.QUOTE_BODY_RANGES,
      MessageTable.QUOTE_TYPE,
      MessageTable.SHARED_CONTACTS,
      MessageTable.LINK_PREVIEWS,
      MessageTable.MESSAGE_RANGES,
      MessageTable.VIEW_ONCE,
      MessageTable.MESSAGE_EXTRAS,
      MessageTable.ORIGINAL_MESSAGE_ID,
      MessageTable.LATEST_REVISION_ID,
      MessageTable.REVISION_NUMBER,
      MessageTable.PARENT_STORY_ID,
      MessageTable.NOTIFIED
    )

    private val REACTION_COLUMNS = arrayOf(
      ReactionTable.MESSAGE_ID,
      ReactionTable.AUTHOR_ID,
      ReactionTable.EMOJI,
      ReactionTable.DATE_SENT,
      ReactionTable.DATE_RECEIVED
    )

    private val GROUP_RECEIPT_COLUMNS = arrayOf(
      GroupReceiptTable.MMS_ID,
      GroupReceiptTable.RECIPIENT_ID,
      GroupReceiptTable.STATUS,
      GroupReceiptTable.TIMESTAMP,
      GroupReceiptTable.UNIDENTIFIED
    )
  }

  private val selfId = Recipient.self().id
  private val buffer: Buffer = Buffer()
  private var messageId: Long = SqlUtil.getNextAutoIncrementId(db, MessageTable.TABLE_NAME)

  /**
   * Indicate that you want to insert the [ChatItem] into the database.
   * If this item causes the buffer to hit the batch size, then a batch of items will actually be inserted.
   */
  fun import(chatItem: ChatItem) {
    val fromLocalRecipientId: RecipientId? = importState.remoteToLocalRecipientId[chatItem.authorId]
    if (fromLocalRecipientId == null) {
      Log.w(TAG, ImportSkips.fromRecipientNotFound(chatItem.dateSent))
      return
    }

    val chatLocalRecipientId: RecipientId? = importState.chatIdToLocalRecipientId[chatItem.chatId]
    if (chatLocalRecipientId == null) {
      Log.w(TAG, ImportSkips.chatIdLocalRecipientNotFound(chatItem.dateSent, chatItem.chatId))
      return
    }

    val localThreadId: Long? = importState.chatIdToLocalThreadId[chatItem.chatId]
    if (localThreadId == null) {
      Log.w(TAG, ImportSkips.chatIdThreadNotFound(chatItem.dateSent, chatItem.chatId))
      return
    }

    val chatBackupRecipientId: Long? = importState.chatIdToBackupRecipientId[chatItem.chatId]
    if (chatBackupRecipientId == null) {
      Log.w(TAG, ImportSkips.chatIdRemoteRecipientNotFound(chatItem.dateSent, chatItem.chatId))
      return
    }
    val messageInsert = chatItem.toMessageInsert(fromLocalRecipientId, chatLocalRecipientId, localThreadId)
    if (chatItem.revisions.isNotEmpty()) {
      // Flush to avoid having revisions cross batch boundaries, which will cause a foreign key failure
      flush()
      val originalId = messageId
      val latestRevisionId = originalId + chatItem.revisions.size
      val sortedRevisions = chatItem.revisions.sortedBy { it.dateSent }.map { it.toMessageInsert(fromLocalRecipientId, chatLocalRecipientId, localThreadId) }
      for (revision in sortedRevisions) {
        val revisionNumber = messageId - originalId
        if (revisionNumber > 0) {
          revision.contentValues.put(MessageTable.ORIGINAL_MESSAGE_ID, originalId)
        }
        revision.contentValues.put(MessageTable.LATEST_REVISION_ID, latestRevisionId)
        revision.contentValues.put(MessageTable.REVISION_NUMBER, revisionNumber)
        buffer.messages += revision
        messageId++
      }

      messageInsert.contentValues.put(MessageTable.ORIGINAL_MESSAGE_ID, originalId)
      messageInsert.contentValues.put(MessageTable.REVISION_NUMBER, (messageId - originalId))
    }
    buffer.messages += messageInsert
    buffer.reactions += chatItem.toReactionContentValues(messageId)
    buffer.groupReceipts += chatItem.toGroupReceiptContentValues(messageId, chatBackupRecipientId)

    messageId++

    if (buffer.size >= batchSize) {
      flush()
    }
  }

  /** Returns true if something was written to the db, otherwise false. */
  fun flush(): Boolean {
    if (buffer.size == 0) {
      return false
    }

    var messageInsertIndex = 0
    SqlUtil.buildBulkInsert(MessageTable.TABLE_NAME, MESSAGE_COLUMNS, buffer.messages.map { it.contentValues }).forEach { query ->
      db.rawQuery("${query.where} RETURNING ${MessageTable.ID}", query.whereArgs).forEach { cursor ->
        val finalMessageId = cursor.requireLong(MessageTable.ID)
        val relatedInsert = buffer.messages[messageInsertIndex++]
        relatedInsert.followUp?.invoke(finalMessageId)
      }
    }

    SqlUtil.buildBulkInsert(ReactionTable.TABLE_NAME, REACTION_COLUMNS, buffer.reactions).forEach {
      db.execSQL(it.where, it.whereArgs)
    }

    SqlUtil.buildBulkInsert(GroupReceiptTable.TABLE_NAME, GROUP_RECEIPT_COLUMNS, buffer.groupReceipts).forEach {
      db.execSQL(it.where, it.whereArgs)
    }

    messageId = SqlUtil.getNextAutoIncrementId(db, MessageTable.TABLE_NAME)

    buffer.reset()

    return true
  }

  private fun ChatItem.toMessageInsert(fromRecipientId: RecipientId, chatRecipientId: RecipientId, threadId: Long): MessageInsert {
    val contentValues = this.toMessageContentValues(fromRecipientId, chatRecipientId, threadId)

    val followUps: MutableList<(Long) -> Unit> = mutableListOf()

    if (this.updateMessage != null) {
      if (this.updateMessage.individualCall != null && this.updateMessage.individualCall.callId != null) {
        followUps += { messageRowId ->
          val values = contentValuesOf(
            CallTable.CALL_ID to updateMessage.individualCall.callId,
            CallTable.MESSAGE_ID to messageRowId,
            CallTable.PEER to chatRecipientId.serialize(),
            CallTable.TYPE to CallTable.Type.serialize(if (updateMessage.individualCall.type == IndividualCall.Type.VIDEO_CALL) CallTable.Type.VIDEO_CALL else CallTable.Type.AUDIO_CALL),
            CallTable.DIRECTION to CallTable.Direction.serialize(if (updateMessage.individualCall.direction == IndividualCall.Direction.OUTGOING) CallTable.Direction.OUTGOING else CallTable.Direction.INCOMING),
            CallTable.EVENT to CallTable.Event.serialize(
              when (updateMessage.individualCall.state) {
                IndividualCall.State.MISSED -> CallTable.Event.MISSED
                IndividualCall.State.MISSED_NOTIFICATION_PROFILE -> CallTable.Event.MISSED_NOTIFICATION_PROFILE
                IndividualCall.State.ACCEPTED -> CallTable.Event.ACCEPTED
                IndividualCall.State.NOT_ACCEPTED -> CallTable.Event.NOT_ACCEPTED
                else -> CallTable.Event.MISSED
              }
            ),
            CallTable.TIMESTAMP to updateMessage.individualCall.startedCallTimestamp,
            CallTable.READ to updateMessage.individualCall.read
          )
          db.insert(CallTable.TABLE_NAME, SQLiteDatabase.CONFLICT_IGNORE, values)
        }
      } else if (this.updateMessage.groupCall != null && this.updateMessage.groupCall.callId != null) {
        followUps += { messageRowId ->
          val ringer: RecipientId? = this.updateMessage.groupCall.ringerRecipientId?.let { importState.remoteToLocalRecipientId[it] }

          val values = contentValuesOf(
            CallTable.CALL_ID to updateMessage.groupCall.callId,
            CallTable.MESSAGE_ID to messageRowId,
            CallTable.PEER to chatRecipientId.serialize(),
            CallTable.RINGER to ringer?.serialize(),
            CallTable.TYPE to CallTable.Type.serialize(CallTable.Type.GROUP_CALL),
            CallTable.DIRECTION to CallTable.Direction.serialize(if (ringer == selfId) CallTable.Direction.OUTGOING else CallTable.Direction.INCOMING),
            CallTable.EVENT to CallTable.Event.serialize(
              when (updateMessage.groupCall.state) {
                GroupCall.State.ACCEPTED -> CallTable.Event.ACCEPTED
                GroupCall.State.MISSED -> CallTable.Event.MISSED
                GroupCall.State.MISSED_NOTIFICATION_PROFILE -> CallTable.Event.MISSED_NOTIFICATION_PROFILE
                GroupCall.State.GENERIC -> CallTable.Event.GENERIC_GROUP_CALL
                GroupCall.State.JOINED -> CallTable.Event.JOINED
                GroupCall.State.RINGING -> CallTable.Event.RINGING
                GroupCall.State.OUTGOING_RING -> CallTable.Event.OUTGOING_RING
                GroupCall.State.DECLINED -> CallTable.Event.DECLINED
                else -> CallTable.Event.GENERIC_GROUP_CALL
              }
            ),
            CallTable.TIMESTAMP to updateMessage.groupCall.startedCallTimestamp,
            CallTable.READ to CallTable.ReadState.serialize(CallTable.ReadState.READ)
          )
          db.insert(CallTable.TABLE_NAME, SQLiteDatabase.CONFLICT_IGNORE, values)
        }
      } else if (this.updateMessage.pollTerminate != null) {
        followUps += { endPollMessageId ->
          val pollMessageId = SignalDatabase.messages.getMessageFor(updateMessage.pollTerminate.targetSentTimestamp, fromRecipientId)?.id ?: -1
          val pollId = SignalDatabase.polls.getPollId(pollMessageId)

          val messageExtras = MessageExtras(pollTerminate = PollTerminate(question = updateMessage.pollTerminate.question, messageId = pollMessageId, targetTimestamp = updateMessage.pollTerminate.targetSentTimestamp))
          db.update(MessageTable.TABLE_NAME)
            .values(MessageTable.MESSAGE_EXTRAS to messageExtras.encode())
            .where("${MessageTable.ID} = ?", endPollMessageId)
            .run()

          if (pollId != null) {
            SignalDatabase.polls.endPoll(pollId = pollId, endingMessageId = endPollMessageId)
          }
        }
      }
    }

    if (this.contactMessage != null) {
      val contact = this.contactMessage.contact?.let { backupContact ->
        Contact(
          backupContact.name.toLocal(),
          backupContact.organization,
          backupContact.number.map { phone ->
            Contact.Phone(
              phone.value_ ?: "",
              phone.type.toLocal(),
              phone.label
            )
          },
          backupContact.email.map { email ->
            Contact.Email(
              email.value_ ?: "",
              email.type.toLocal(),
              email.label
            )
          },
          backupContact.address.map { address ->
            Contact.PostalAddress(
              address.type.toLocal(),
              address.label,
              address.street,
              address.pobox,
              address.neighborhood,
              address.city,
              address.region,
              address.postcode,
              address.country
            )
          },
          Contact.Avatar(null, backupContact.avatar.toLocalAttachment(voiceNote = false, borderless = false, gif = false, wasDownloaded = true), true)
        )
      }

      if (contact != null) {
        val contactAttachment: Attachment? = contact.avatarAttachment
        followUps += { messageRowId ->
          val attachmentMap = if (contactAttachment != null) {
            SignalDatabase.attachments.insertAttachmentsForMessage(messageRowId, listOf(contactAttachment), emptyList())
          } else {
            emptyMap()
          }
          db.update(
            MessageTable.TABLE_NAME,
            contentValuesOf(
              MessageTable.SHARED_CONTACTS to SignalDatabase.messages.getSerializedSharedContacts(attachmentMap, listOf(contact))
            ),
            "${MessageTable.ID} = ?",
            SqlUtil.buildArgs(messageRowId)
          )
        }
      }
    }

    if (this.directStoryReplyMessage != null) {
      val (trimmedBodyText, longTextAttachment) = this.directStoryReplyMessage.parseBodyText(importState)
      if (trimmedBodyText != null) {
        contentValues.put(MessageTable.BODY, trimmedBodyText)
      }

      if (longTextAttachment != null) {
        followUps += { messageRowId ->
          val ids = SignalDatabase.attachments.insertAttachmentsForMessage(messageRowId, listOf(longTextAttachment), emptyList())
          ids.values.firstOrNull()?.let { attachmentId ->
            SignalDatabase.attachments.setTransferState(messageRowId, attachmentId, AttachmentTable.TRANSFER_PROGRESS_DONE)
          }
        }
      }
    }

    if (this.standardMessage != null) {
      val mentions = this.standardMessage.text?.bodyRanges.filterToLocalMentions()
      if (mentions.isNotEmpty()) {
        followUps += { messageId ->
          SignalDatabase.mentions.insert(threadId, messageId, mentions)
        }
      }
      val linkPreviews = this.standardMessage.linkPreview.map { it.toLocalLinkPreview() }
      val linkPreviewAttachments: List<Attachment> = linkPreviews.mapNotNull { it.thumbnail.orNull() }
      val attachments: List<Attachment> = this.standardMessage.attachments.mapNotNull { attachment ->
        attachment.toLocalAttachment()
      }

      val (trimmedBodyText, longTextAttachment) = this.standardMessage.parseBodyText(importState)
      if (trimmedBodyText != null) {
        contentValues.put(MessageTable.BODY, trimmedBodyText)
      }

      val quoteAttachments: List<Attachment> = this.standardMessage.quote?.toLocalAttachments() ?: emptyList()

      val hasAttachments = attachments.isNotEmpty() || linkPreviewAttachments.isNotEmpty() || quoteAttachments.isNotEmpty() || longTextAttachment != null

      if (hasAttachments || linkPreviews.isNotEmpty()) {
        followUps += { messageRowId ->
          val attachmentMap = if (hasAttachments) {
            SignalDatabase.attachments.insertAttachmentsForMessage(messageRowId, attachments + linkPreviewAttachments + longTextAttachment.asList(), quoteAttachments)
          } else {
            emptyMap()
          }

          if (longTextAttachment != null) {
            attachmentMap[longTextAttachment]?.let { attachmentId ->
              SignalDatabase.attachments.setTransferState(messageRowId, attachmentId, AttachmentTable.TRANSFER_PROGRESS_DONE)
            }
          }

          if (linkPreviews.isNotEmpty()) {
            db.update(MessageTable.TABLE_NAME)
              .values(MessageTable.LINK_PREVIEWS to SignalDatabase.messages.getSerializedLinkPreviews(attachmentMap, linkPreviews))
              .where("${MessageTable.ID} = ?", messageRowId)
              .run()
          }
        }
      }
    }

    if (this.stickerMessage != null) {
      val sticker = this.stickerMessage.sticker
      val attachment = sticker.toLocalAttachment()
      if (attachment != null) {
        followUps += { messageRowId ->
          SignalDatabase.attachments.insertAttachmentsForMessage(messageRowId, listOf(attachment), emptyList())
        }
      }
    }

    if (this.viewOnceMessage != null) {
      val attachment = this.viewOnceMessage.attachment?.toLocalAttachment()
      if (attachment != null) {
        followUps += { messageRowId ->
          SignalDatabase.attachments.insertAttachmentsForMessage(messageRowId, listOf(attachment), emptyList())
        }
      }
    }

    if (this.poll != null) {
      contentValues.put(MessageTable.BODY, poll.question)
      contentValues.put(MessageTable.VOTES_LAST_SEEN, System.currentTimeMillis())

      followUps += { messageRowId ->
        val pollId = SignalDatabase.polls.insertPoll(
          question = poll.question,
          allowMultipleVotes = poll.allowMultiple,
          options = poll.options.map { it.option },
          authorId = fromRecipientId.toLong(),
          messageId = messageRowId
        )

        val localOptionIds = SignalDatabase.polls.getPollOptionIds(pollId)
        poll.options.forEachIndexed { index, option ->
          val localVoterIds = option.votes.map { importState.remoteToLocalRecipientId[it.voterId]?.toLong() }
          val voteCounts = option.votes.map { it.voteCount }
          val localVoters = localVoterIds.mapIndexedNotNull { index, id -> id?.let { Voter(id = id, voteCount = voteCounts[index]) } }
          SignalDatabase.polls.addPollVotes(pollId = pollId, optionId = localOptionIds[index], voters = localVoters)
        }

        if (poll.hasEnded) {
          // At this point, we don't know what message ended the poll. Instead, we set it to -1 to indicate that it
          // is ended and will update endingMessageId when we process the poll terminate message (if it exists).
          SignalDatabase.polls.endPoll(pollId = pollId, endingMessageId = -1)
        }
      }
    }

    val followUp: ((Long) -> Unit)? = if (followUps.isNotEmpty()) {
      { messageId ->
        followUps.forEach { it(messageId) }
      }
    } else {
      null
    }

    return MessageInsert(contentValues, followUp)
  }

  /**
   * Text that we import from the [StandardMessage.text] field may be too long to put in a database column, needing to instead be broken into a separate
   * attachment. This handles looking at the state of the frame and giving back the components we need to insert.
   *
   * @return If the returned String is non-null, then that means you should replace what we currently have stored as the body with this new, trimmed string.
   *   If the attachment is non-null, then you should store it along with the message, as it contains the long text.
   */
  private fun StandardMessage.parseBodyText(importState: ImportState): Pair<String?, Attachment?> {
    if (this.longText != null) {
      return null to this.longText.toLocalAttachment(contentType = "text/x-signal-plain")
    }

    if (this.text?.body == null) {
      return null to null
    }

    val splitResult = MessageUtil.getSplitMessage(AppDependencies.application, this.text.body)
    if (splitResult.textSlide.isPresent) {
      return splitResult.body to splitResult.textSlide.get().asAttachment()
    }

    return null to null
  }

  /**
   * Text that we import from the [DirectStoryReplyMessage.textReply] field may be too long to put in a database column, needing to instead be broken into a separate
   * attachment. This handles looking at the state of the frame and giving back the components we need to insert.
   *
   * @return If the returned String is non-null, then that means you should replace what we currently have stored as the body with this new, trimmed string.
   *   If the attachment is non-null, then you should store it along with the message, as it contains the long text.
   */
  private fun DirectStoryReplyMessage.parseBodyText(importState: ImportState): Pair<String?, Attachment?> {
    if (this.textReply?.longText != null) {
      return null to this.textReply.longText.toLocalAttachment(contentType = "text/x-signal-plain")
    }

    if (this.textReply?.text == null) {
      return null to null
    }

    val splitResult = MessageUtil.getSplitMessage(AppDependencies.application, this.textReply.text.body)
    if (splitResult.textSlide.isPresent) {
      return splitResult.body to splitResult.textSlide.get().asAttachment()
    }

    return null to null
  }

  private fun ChatItem.toMessageContentValues(fromRecipientId: RecipientId, chatRecipientId: RecipientId, threadId: Long): ContentValues {
    val contentValues = ContentValues()

    val toRecipientId = if (this.outgoing != null) chatRecipientId else selfId

    contentValues.put(MessageTable.TYPE, this.getMessageType())
    contentValues.put(MessageTable.DATE_SENT, this.dateSent)
    contentValues.put(MessageTable.DATE_SERVER, this.incoming?.dateServerSent ?: -1)
    contentValues.put(MessageTable.FROM_RECIPIENT_ID, fromRecipientId.serialize())
    contentValues.put(MessageTable.TO_RECIPIENT_ID, toRecipientId.serialize())
    contentValues.put(MessageTable.THREAD_ID, threadId)
    contentValues.put(MessageTable.DATE_RECEIVED, this.incoming?.dateReceived ?: this.outgoing?.dateReceived?.takeUnless { it == 0L } ?: this.dateSent)
    contentValues.put(MessageTable.RECEIPT_TIMESTAMP, this.outgoing?.sendStatus?.maxOfOrNull { it.timestamp } ?: 0)
    contentValues.putNull(MessageTable.LATEST_REVISION_ID)
    contentValues.putNull(MessageTable.ORIGINAL_MESSAGE_ID)
    contentValues.put(MessageTable.REVISION_NUMBER, 0)
    contentValues.put(MessageTable.EXPIRES_IN, this.expiresInMs ?: 0)
    contentValues.put(MessageTable.EXPIRE_STARTED, this.expireStartDate ?: 0)

    when {
      this.outgoing != null -> {
        val viewed = this.outgoing.sendStatus.any { it.viewed != null }
        val hasReadReceipt = viewed || this.outgoing.sendStatus.any { it.read != null }
        val hasDeliveryReceipt = viewed || hasReadReceipt || this.outgoing.sendStatus.any { it.delivered != null }

        contentValues.put(MessageTable.VIEWED_COLUMN, viewed.toInt())
        contentValues.put(MessageTable.HAS_READ_RECEIPT, hasReadReceipt.toInt())
        contentValues.put(MessageTable.HAS_DELIVERY_RECEIPT, hasDeliveryReceipt.toInt())
        contentValues.put(MessageTable.UNIDENTIFIED, this.outgoing.sendStatus.count { it.sealedSender })
        contentValues.put(MessageTable.READ, 1)

        contentValues.addNetworkFailures(this, importState)
        contentValues.addIdentityKeyMismatches(this, importState)
      }
      this.incoming != null -> {
        contentValues.put(MessageTable.VIEWED_COLUMN, 0)
        contentValues.put(MessageTable.HAS_READ_RECEIPT, 0)
        contentValues.put(MessageTable.HAS_DELIVERY_RECEIPT, 0)
        contentValues.put(MessageTable.UNIDENTIFIED, this.incoming.sealedSender.toInt())
        contentValues.put(MessageTable.READ, this.incoming.read.toInt())
        contentValues.put(MessageTable.NOTIFIED, 1)
      }
      this.directionless != null -> {
        contentValues.put(MessageTable.VIEWED_COLUMN, 0)
        contentValues.put(MessageTable.HAS_READ_RECEIPT, 0)
        contentValues.put(MessageTable.HAS_DELIVERY_RECEIPT, 0)
        contentValues.put(MessageTable.READ, 1)
        contentValues.put(MessageTable.NOTIFIED, 1)
      }
    }

    contentValues.put(MessageTable.QUOTE_ID, 0)
    contentValues.put(MessageTable.QUOTE_AUTHOR, 0)
    contentValues.put(MessageTable.QUOTE_MISSING, 0)
    contentValues.put(MessageTable.QUOTE_TYPE, 0)
    contentValues.put(MessageTable.VIEW_ONCE, 0)
    contentValues.put(MessageTable.REMOTE_DELETED, 0)
    contentValues.put(MessageTable.PARENT_STORY_ID, 0)

    when {
      this.standardMessage != null -> contentValues.addStandardMessage(this.standardMessage)
      this.remoteDeletedMessage != null -> contentValues.put(MessageTable.REMOTE_DELETED, 1)
      this.updateMessage != null -> contentValues.addUpdateMessage(this.updateMessage, fromRecipientId, toRecipientId)
      this.giftBadge != null -> contentValues.addGiftBadge(this.giftBadge)
      this.viewOnceMessage != null -> contentValues.addViewOnce(this.viewOnceMessage)
      this.directStoryReplyMessage != null -> contentValues.addDirectStoryReply(this.directStoryReplyMessage, toRecipientId)
    }

    return contentValues
  }

  private fun ChatItem.toReactionContentValues(messageId: Long): List<ContentValues> {
    val reactions: List<Reaction> = when {
      this.standardMessage != null -> this.standardMessage.reactions
      this.contactMessage != null -> this.contactMessage.reactions
      this.stickerMessage != null -> this.stickerMessage.reactions
      this.viewOnceMessage != null -> this.viewOnceMessage.reactions
      this.directStoryReplyMessage != null -> this.directStoryReplyMessage.reactions
      else -> emptyList()
    }

    return reactions
      .mapNotNull {
        val authorId: Long? = importState.remoteToLocalRecipientId[it.authorId]?.toLong()

        if (authorId != null) {
          contentValuesOf(
            ReactionTable.MESSAGE_ID to messageId,
            ReactionTable.AUTHOR_ID to authorId,
            ReactionTable.DATE_SENT to it.sentTimestamp,
            ReactionTable.DATE_RECEIVED to it.sortOrder,
            ReactionTable.EMOJI to it.emoji
          )
        } else {
          Log.w(TAG, "[Reaction] Could not find a local recipient for backup recipient ID ${it.authorId}! Skipping.")
          null
        }
      }
  }

  private fun ChatItem.toGroupReceiptContentValues(messageId: Long, chatBackupRecipientId: Long): List<ContentValues> {
    if (this.outgoing == null) {
      return emptyList()
    }

    // TODO [backup] This seems like an indirect/bad way to detect if this is a 1:1 or group convo
    if (this.outgoing.sendStatus.size == 1 && this.outgoing.sendStatus[0].recipientId == chatBackupRecipientId) {
      return emptyList()
    }

    return this.outgoing.sendStatus.mapNotNull { sendStatus ->
      val recipientId = importState.remoteToLocalRecipientId[sendStatus.recipientId]

      if (recipientId != null) {
        contentValuesOf(
          GroupReceiptTable.MMS_ID to messageId,
          GroupReceiptTable.RECIPIENT_ID to recipientId.serialize(),
          GroupReceiptTable.STATUS to sendStatus.toLocalSendStatus(),
          GroupReceiptTable.TIMESTAMP to sendStatus.timestamp,
          GroupReceiptTable.UNIDENTIFIED to sendStatus.sealedSender.toInt()
        )
      } else {
        Log.w(TAG, "[GroupReceipts] Could not find a local recipient for backup recipient ID ${sendStatus.recipientId}! Skipping.")
        null
      }
    }
  }

  private fun ChatItem.getMessageType(): Long {
    var type: Long = if (this.outgoing != null) {
      if (this.outgoing.sendStatus.any { it.failed?.reason == SendStatus.Failed.FailureReason.IDENTITY_KEY_MISMATCH }) {
        MessageTypes.BASE_SENT_FAILED_TYPE
      } else if (this.outgoing.sendStatus.any { it.failed?.reason == SendStatus.Failed.FailureReason.UNKNOWN }) {
        MessageTypes.BASE_SENT_FAILED_TYPE
      } else if (this.outgoing.sendStatus.any { it.failed?.reason == SendStatus.Failed.FailureReason.NETWORK }) {
        MessageTypes.BASE_SENT_FAILED_TYPE
      } else if (this.outgoing.sendStatus.any { it.pending != null }) {
        MessageTypes.BASE_SENDING_TYPE
      } else if (this.outgoing.sendStatus.all { it.skipped != null }) {
        MessageTypes.BASE_SENDING_SKIPPED_TYPE
      } else {
        MessageTypes.BASE_SENT_TYPE
      }
    } else {
      MessageTypes.BASE_INBOX_TYPE
    }

    if (!this.sms) {
      type = type or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT
    }

    if (this.giftBadge != null) {
      type = type or MessageTypes.SPECIAL_TYPE_GIFT_BADGE
    }

    if (this.directStoryReplyMessage?.emoji != null) {
      type = type or MessageTypes.SPECIAL_TYPE_STORY_REACTION
    }

    return type
  }

  private fun ContentValues.addStandardMessage(standardMessage: StandardMessage) {
    if (standardMessage.text != null) {
      this.put(MessageTable.BODY, standardMessage.text.body)

      if (standardMessage.text.bodyRanges.isNotEmpty()) {
        this.put(MessageTable.MESSAGE_RANGES, standardMessage.text.bodyRanges.toLocalBodyRanges()?.encode())
      }
    }

    if (standardMessage.quote != null) {
      this.addQuote(standardMessage.quote)
    }
  }

  private fun ContentValues.addUpdateMessage(updateMessage: ChatUpdateMessage, fromRecipientId: RecipientId, toRecipientId: RecipientId) {
    var typeFlags: Long = 0
    when {
      updateMessage.simpleUpdate != null -> {
        val typeWithoutBase = (getAsLong(MessageTable.TYPE) and MessageTypes.BASE_TYPE_MASK.inv())
        typeFlags = when (updateMessage.simpleUpdate.type) {
          SimpleChatUpdate.Type.UNKNOWN -> typeWithoutBase
          SimpleChatUpdate.Type.JOINED_SIGNAL -> MessageTypes.JOINED_TYPE or typeWithoutBase
          SimpleChatUpdate.Type.IDENTITY_UPDATE -> MessageTypes.KEY_EXCHANGE_IDENTITY_UPDATE_BIT or typeWithoutBase
          SimpleChatUpdate.Type.IDENTITY_VERIFIED -> MessageTypes.KEY_EXCHANGE_IDENTITY_VERIFIED_BIT or typeWithoutBase
          SimpleChatUpdate.Type.IDENTITY_DEFAULT -> MessageTypes.KEY_EXCHANGE_IDENTITY_DEFAULT_BIT or typeWithoutBase
          SimpleChatUpdate.Type.CHANGE_NUMBER -> MessageTypes.CHANGE_NUMBER_TYPE
          SimpleChatUpdate.Type.RELEASE_CHANNEL_DONATION_REQUEST -> MessageTypes.RELEASE_CHANNEL_DONATION_REQUEST_TYPE
          SimpleChatUpdate.Type.END_SESSION -> MessageTypes.END_SESSION_BIT or typeWithoutBase
          SimpleChatUpdate.Type.CHAT_SESSION_REFRESH -> MessageTypes.ENCRYPTION_REMOTE_FAILED_BIT or typeWithoutBase
          SimpleChatUpdate.Type.BAD_DECRYPT -> MessageTypes.BAD_DECRYPT_TYPE or typeWithoutBase
          SimpleChatUpdate.Type.PAYMENTS_ACTIVATED -> MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATED or typeWithoutBase
          SimpleChatUpdate.Type.PAYMENT_ACTIVATION_REQUEST -> MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST or typeWithoutBase
          SimpleChatUpdate.Type.UNSUPPORTED_PROTOCOL_MESSAGE -> MessageTypes.UNSUPPORTED_MESSAGE_TYPE or typeWithoutBase
          SimpleChatUpdate.Type.REPORTED_SPAM -> MessageTypes.SPECIAL_TYPE_REPORTED_SPAM or typeWithoutBase
          SimpleChatUpdate.Type.BLOCKED -> MessageTypes.SPECIAL_TYPE_BLOCKED or typeWithoutBase
          SimpleChatUpdate.Type.UNBLOCKED -> MessageTypes.SPECIAL_TYPE_UNBLOCKED or typeWithoutBase
          SimpleChatUpdate.Type.MESSAGE_REQUEST_ACCEPTED -> MessageTypes.SPECIAL_TYPE_MESSAGE_REQUEST_ACCEPTED or typeWithoutBase
        }

        // Identity verification changes have to/from swapped
        if (updateMessage.simpleUpdate.type == SimpleChatUpdate.Type.IDENTITY_VERIFIED || updateMessage.simpleUpdate.type == SimpleChatUpdate.Type.IDENTITY_DEFAULT) {
          put(MessageTable.FROM_RECIPIENT_ID, toRecipientId.serialize())
          put(MessageTable.TO_RECIPIENT_ID, fromRecipientId.serialize())
        }
      }
      updateMessage.expirationTimerChange != null -> {
        typeFlags = getAsLong(MessageTable.TYPE) or MessageTypes.EXPIRATION_TIMER_UPDATE_BIT
        put(MessageTable.EXPIRES_IN, updateMessage.expirationTimerChange.expiresInMs)
      }
      updateMessage.profileChange != null -> {
        typeFlags = MessageTypes.PROFILE_CHANGE_TYPE
        val profileChangeDetails = ProfileChangeDetails(profileNameChange = ProfileChangeDetails.StringChange(previous = updateMessage.profileChange.previousName, newValue = updateMessage.profileChange.newName))
        val messageExtras = MessageExtras(profileChangeDetails = profileChangeDetails).encode()
        put(MessageTable.MESSAGE_EXTRAS, messageExtras)
      }
      updateMessage.learnedProfileChange != null -> {
        typeFlags = MessageTypes.PROFILE_CHANGE_TYPE
        val profileChangeDetails = ProfileChangeDetails(learnedProfileName = ProfileChangeDetails.LearnedProfileName(e164 = updateMessage.learnedProfileChange.e164?.toString(), username = updateMessage.learnedProfileChange.username))
        val messageExtras = MessageExtras(profileChangeDetails = profileChangeDetails).encode()
        put(MessageTable.MESSAGE_EXTRAS, messageExtras)
      }
      updateMessage.pollTerminate != null -> {
        typeFlags = MessageTypes.SPECIAL_TYPE_POLL_TERMINATE or (getAsLong(MessageTable.TYPE) and MessageTypes.BASE_TYPE_MASK.inv())
      }
      updateMessage.sessionSwitchover != null -> {
        typeFlags = MessageTypes.SESSION_SWITCHOVER_TYPE or (getAsLong(MessageTable.TYPE) and MessageTypes.BASE_TYPE_MASK.inv())
        val sessionSwitchoverDetails = SessionSwitchoverEvent(e164 = updateMessage.sessionSwitchover.e164.toString()).encode()
        put(MessageTable.BODY, Base64.encodeWithPadding(sessionSwitchoverDetails))
      }
      updateMessage.threadMerge != null -> {
        typeFlags = MessageTypes.THREAD_MERGE_TYPE or (getAsLong(MessageTable.TYPE) and MessageTypes.BASE_TYPE_MASK.inv())
        val threadMergeDetails = ThreadMergeEvent(previousE164 = updateMessage.threadMerge.previousE164.toString()).encode()
        put(MessageTable.BODY, Base64.encodeWithPadding(threadMergeDetails))
      }
      updateMessage.individualCall != null -> {
        if (updateMessage.individualCall.state == IndividualCall.State.MISSED || updateMessage.individualCall.state == IndividualCall.State.MISSED_NOTIFICATION_PROFILE) {
          typeFlags = if (updateMessage.individualCall.type == IndividualCall.Type.AUDIO_CALL) {
            MessageTypes.MISSED_AUDIO_CALL_TYPE
          } else {
            MessageTypes.MISSED_VIDEO_CALL_TYPE
          }
        } else {
          typeFlags = if (updateMessage.individualCall.direction == IndividualCall.Direction.OUTGOING) {
            if (updateMessage.individualCall.type == IndividualCall.Type.AUDIO_CALL) {
              MessageTypes.OUTGOING_AUDIO_CALL_TYPE
            } else {
              MessageTypes.OUTGOING_VIDEO_CALL_TYPE
            }
          } else {
            if (updateMessage.individualCall.type == IndividualCall.Type.AUDIO_CALL) {
              MessageTypes.INCOMING_AUDIO_CALL_TYPE
            } else {
              MessageTypes.INCOMING_VIDEO_CALL_TYPE
            }
          }
        }
        this.put(MessageTable.READ, 1)
      }
      updateMessage.groupCall != null -> {
        val startedCallRecipientId = if (updateMessage.groupCall.startedCallRecipientId != null) {
          importState.remoteToLocalRecipientId[updateMessage.groupCall.startedCallRecipientId]
        } else {
          null
        }
        val startedCall = if (startedCallRecipientId != null) {
          recipients.getRecord(startedCallRecipientId).aci
        } else {
          null
        }
        this.put(MessageTable.BODY, GroupCallUpdateDetailsUtil.createBodyFromBackup(updateMessage.groupCall, startedCall))
        this.put(MessageTable.READ, updateMessage.groupCall.read.toInt())
        typeFlags = MessageTypes.GROUP_CALL_TYPE
      }
      updateMessage.groupChange != null -> {
        put(MessageTable.BODY, "")
        put(
          MessageTable.MESSAGE_EXTRAS,
          MessageExtras(
            gv2UpdateDescription =
            GV2UpdateDescription(groupChangeUpdate = updateMessage.groupChange)
          ).encode()
        )
        typeFlags = getAsLong(MessageTable.TYPE) or MessageTypes.GROUP_V2_BIT or MessageTypes.GROUP_UPDATE_BIT
      }
    }
    this.put(MessageTable.TYPE, typeFlags)
  }

  private fun ContentValues.addGiftBadge(giftBadge: BackupGiftBadge) {
    val dbGiftBadge = GiftBadge(
      redemptionToken = giftBadge.receiptCredentialPresentation,
      redemptionState = when (giftBadge.state) {
        BackupGiftBadge.State.UNOPENED -> GiftBadge.RedemptionState.PENDING
        BackupGiftBadge.State.OPENED -> GiftBadge.RedemptionState.STARTED
        BackupGiftBadge.State.REDEEMED -> GiftBadge.RedemptionState.REDEEMED
        BackupGiftBadge.State.FAILED -> GiftBadge.RedemptionState.FAILED
      }
    )

    put(MessageTable.BODY, Base64.encodeWithPadding(GiftBadge.ADAPTER.encode(dbGiftBadge)))
  }

  private fun ContentValues.addViewOnce(viewOnce: ViewOnceMessage) {
    put(MessageTable.VIEW_ONCE, true.toInt())
  }

  private fun ContentValues.addDirectStoryReply(directStoryReply: DirectStoryReplyMessage, toRecipientId: RecipientId) {
    put(MessageTable.PARENT_STORY_ID, MessageTable.PARENT_STORY_MISSING_ID)
    put(MessageTable.QUOTE_MISSING, 1)
    put(MessageTable.QUOTE_ID, MessageTable.QUOTE_TARGET_MISSING_ID)
    put(MessageTable.QUOTE_AUTHOR, toRecipientId.serialize())

    if (directStoryReply.emoji != null) {
      put(MessageTable.BODY, directStoryReply.emoji)
    }

    if (directStoryReply.textReply != null) {
      put(MessageTable.BODY, directStoryReply.textReply.text?.body)
      put(MessageTable.MESSAGE_RANGES, directStoryReply.textReply.text?.bodyRanges?.toLocalBodyRanges()?.encode())
    }
  }

  private fun ContentValues.addQuote(quote: Quote) {
    this.put(MessageTable.QUOTE_ID, quote.targetSentTimestamp ?: MessageTable.QUOTE_TARGET_MISSING_ID)
    this.put(MessageTable.QUOTE_AUTHOR, importState.requireLocalRecipientId(quote.authorId).serialize())
    this.put(MessageTable.QUOTE_BODY, quote.text?.body)
    this.put(MessageTable.QUOTE_TYPE, quote.type.toLocalQuoteType())
    this.put(MessageTable.QUOTE_BODY_RANGES, quote.text?.bodyRanges?.toLocalBodyRanges(includeMentions = true)?.encode())
    this.put(MessageTable.QUOTE_MISSING, (quote.targetSentTimestamp == null).toInt())
  }

  private fun Quote.Type.toLocalQuoteType(): Int {
    return when (this) {
      Quote.Type.UNKNOWN -> QuoteModel.Type.NORMAL.code
      Quote.Type.NORMAL -> QuoteModel.Type.NORMAL.code
      Quote.Type.GIFT_BADGE -> QuoteModel.Type.GIFT_BADGE.code
      Quote.Type.VIEW_ONCE -> QuoteModel.Type.NORMAL.code
    }
  }

  private fun ContentValues.addNetworkFailures(chatItem: ChatItem, importState: ImportState) {
    if (chatItem.outgoing == null) {
      return
    }

    val networkFailures = chatItem.outgoing.sendStatus
      .filter { status -> status.failed?.reason == SendStatus.Failed.FailureReason.NETWORK }
      .mapNotNull { status -> importState.remoteToLocalRecipientId[status.recipientId] }
      .map { recipientId -> NetworkFailure(recipientId) }
      .toSet()

    if (networkFailures.isNotEmpty()) {
      this.put(MessageTable.NETWORK_FAILURES, JsonUtils.toJson(NetworkFailureSet(networkFailures)))
    }
  }

  private fun ContentValues.addIdentityKeyMismatches(chatItem: ChatItem, importState: ImportState) {
    if (chatItem.outgoing == null) {
      return
    }

    val mismatches = chatItem.outgoing.sendStatus
      .filter { status -> status.failed?.reason == SendStatus.Failed.FailureReason.IDENTITY_KEY_MISMATCH }
      .mapNotNull { status -> importState.remoteToLocalRecipientId[status.recipientId] }
      .map { recipientId -> IdentityKeyMismatch(recipientId, null) } // TODO We probably want the actual identity key in this status situation?
      .toSet()

    if (mismatches.isNotEmpty()) {
      this.put(MessageTable.MISMATCHED_IDENTITIES, JsonUtils.toJson(IdentityKeyMismatchSet(mismatches)))
    }
  }

  private fun List<BodyRange>.toLocalBodyRanges(includeMentions: Boolean = false): BodyRangeList? {
    if (this.isEmpty()) {
      return null
    }

    return BodyRangeList(
      ranges = this.filter { includeMentions || it.mentionAci == null }.map { bodyRange ->
        BodyRangeList.BodyRange(
          mentionUuid = bodyRange.mentionAci?.let { UuidUtil.fromByteString(it) }?.toString(),
          style = bodyRange.style?.let {
            when (bodyRange.style) {
              BodyRange.Style.BOLD -> BodyRangeList.BodyRange.Style.BOLD
              BodyRange.Style.ITALIC -> BodyRangeList.BodyRange.Style.ITALIC
              BodyRange.Style.MONOSPACE -> BodyRangeList.BodyRange.Style.MONOSPACE
              BodyRange.Style.SPOILER -> BodyRangeList.BodyRange.Style.SPOILER
              BodyRange.Style.STRIKETHROUGH -> BodyRangeList.BodyRange.Style.STRIKETHROUGH
              else -> null
            }
          },
          start = bodyRange.start,
          length = bodyRange.length
        )
      }
    )
  }

  private fun SendStatus.toLocalSendStatus(): Int {
    return when {
      this.pending != null -> GroupReceiptTable.STATUS_UNKNOWN
      this.sent != null -> GroupReceiptTable.STATUS_UNDELIVERED
      this.delivered != null -> GroupReceiptTable.STATUS_DELIVERED
      this.read != null -> GroupReceiptTable.STATUS_READ
      this.viewed != null -> GroupReceiptTable.STATUS_VIEWED
      this.skipped != null -> GroupReceiptTable.STATUS_SKIPPED
      this.failed != null -> GroupReceiptTable.STATUS_FAILED
      else -> GroupReceiptTable.STATUS_UNKNOWN
    }
  }

  private val SendStatus.sealedSender: Boolean
    get() {
      return this.sent?.sealedSender
        ?: this.delivered?.sealedSender
        ?: this.read?.sealedSender
        ?: this.viewed?.sealedSender
        ?: false
    }

  private fun Quote.toLocalAttachments(): List<Attachment> {
    if (this.type == Quote.Type.VIEW_ONCE) {
      return listOf(TombstoneAttachment.forQuote())
    }

    return this.attachments.mapNotNull { attachment ->
      val thumbnail = attachment.thumbnail?.toLocalAttachment(quote = true, quoteTargetContentType = attachment.contentType)

      if (thumbnail != null) {
        return@mapNotNull thumbnail
      }

      if (attachment.contentType == null) {
        return@mapNotNull null
      }

      return@mapNotNull PointerAttachment.forPointer(
        quotedAttachment = DataMessage.Quote.QuotedAttachment(
          contentType = attachment.contentType,
          fileName = attachment.fileName,
          thumbnail = null
        )
      ).orNull()
    }
  }

  private fun Sticker?.toLocalAttachment(): Attachment? {
    if (this == null) return null

    return data_.toLocalAttachment(
      voiceNote = false,
      borderless = false,
      gif = false,
      wasDownloaded = true,
      stickerLocator = StickerLocator(
        packId = Hex.toStringCondensed(packId.toByteArray()),
        packKey = Hex.toStringCondensed(packKey.toByteArray()),
        stickerId = stickerId,
        emoji = emoji
      )
    )
  }

  private fun LinkPreview.toLocalLinkPreview(): org.thoughtcrime.securesms.linkpreview.LinkPreview {
    return org.thoughtcrime.securesms.linkpreview.LinkPreview(
      this.url,
      this.title ?: "",
      this.description ?: "",
      this.date ?: 0,
      Optional.ofNullable(this.image?.toLocalAttachment(voiceNote = false, borderless = false, gif = false, wasDownloaded = true))
    )
  }

  private fun MessageAttachment.toLocalAttachment(quote: Boolean = false, quoteTargetContentType: String? = null, contentType: String? = pointer?.contentType): Attachment? {
    return pointer?.toLocalAttachment(
      voiceNote = flag == MessageAttachment.Flag.VOICE_MESSAGE,
      borderless = flag == MessageAttachment.Flag.BORDERLESS,
      gif = flag == MessageAttachment.Flag.GIF,
      wasDownloaded = wasDownloaded,
      contentType = contentType,
      fileName = pointer.fileName,
      uuid = clientUuid,
      quote = quote,
      quoteTargetContentType = quoteTargetContentType
    )
  }

  private fun ContactAttachment.Name?.toLocal(): Contact.Name {
    return Contact.Name(this?.givenName, this?.familyName, this?.prefix, this?.suffix, this?.middleName, this?.nickname)
  }

  private fun ContactAttachment.Phone.Type?.toLocal(): Contact.Phone.Type {
    return when (this) {
      ContactAttachment.Phone.Type.HOME -> Contact.Phone.Type.HOME
      ContactAttachment.Phone.Type.MOBILE -> Contact.Phone.Type.MOBILE
      ContactAttachment.Phone.Type.WORK -> Contact.Phone.Type.WORK
      ContactAttachment.Phone.Type.CUSTOM,
      ContactAttachment.Phone.Type.UNKNOWN,
      null -> Contact.Phone.Type.CUSTOM
    }
  }

  private fun ContactAttachment.Email.Type?.toLocal(): Contact.Email.Type {
    return when (this) {
      ContactAttachment.Email.Type.HOME -> Contact.Email.Type.HOME
      ContactAttachment.Email.Type.MOBILE -> Contact.Email.Type.MOBILE
      ContactAttachment.Email.Type.WORK -> Contact.Email.Type.WORK
      ContactAttachment.Email.Type.CUSTOM,
      ContactAttachment.Email.Type.UNKNOWN,
      null -> Contact.Email.Type.CUSTOM
    }
  }

  private fun ContactAttachment.PostalAddress.Type?.toLocal(): Contact.PostalAddress.Type {
    return when (this) {
      ContactAttachment.PostalAddress.Type.HOME -> Contact.PostalAddress.Type.HOME
      ContactAttachment.PostalAddress.Type.WORK -> Contact.PostalAddress.Type.WORK
      ContactAttachment.PostalAddress.Type.CUSTOM,
      ContactAttachment.PostalAddress.Type.UNKNOWN,
      null -> Contact.PostalAddress.Type.CUSTOM
    }
  }

  private fun List<BodyRange>?.filterToLocalMentions(): List<Mention> {
    if (this == null) {
      return emptyList()
    }

    return this.filter { it.mentionAci != null && it.start != null && it.length != null }
      .mapNotNull {
        val aci = ServiceId.ACI.parseOrNull(it.mentionAci!!)

        if (aci != null && !aci.isUnknown) {
          val id = RecipientId.from(aci)
          Mention(id, it.start!!, it.length!!)
        } else {
          null
        }
      }
  }

  private class MessageInsert(
    val contentValues: ContentValues,
    val followUp: ((Long) -> Unit)?,
    val edits: List<MessageInsert>? = null
  )

  private class Buffer(
    val messages: MutableList<MessageInsert> = mutableListOf(),
    val reactions: MutableList<ContentValues> = mutableListOf(),
    val groupReceipts: MutableList<ContentValues> = mutableListOf()
  ) {
    val size: Int
      get() = listOf(messages.size, reactions.size, groupReceipts.size).max()

    fun reset() {
      messages.clear()
      reactions.clear()
      groupReceipts.clear()
    }
  }
}

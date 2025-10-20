package org.thoughtcrime.securesms.database.helpers

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteException
import org.signal.core.util.areForeignKeyConstraintsEnabled
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.helpers.migration.SignalDatabaseMigration
import org.thoughtcrime.securesms.database.helpers.migration.V149_LegacyMigrations
import org.thoughtcrime.securesms.database.helpers.migration.V150_UrgentMslFlagMigration
import org.thoughtcrime.securesms.database.helpers.migration.V151_MyStoryMigration
import org.thoughtcrime.securesms.database.helpers.migration.V152_StoryGroupTypesMigration
import org.thoughtcrime.securesms.database.helpers.migration.V153_MyStoryMigration
import org.thoughtcrime.securesms.database.helpers.migration.V154_PniSignaturesMigration
import org.thoughtcrime.securesms.database.helpers.migration.V155_SmsExporterMigration
import org.thoughtcrime.securesms.database.helpers.migration.V156_RecipientUnregisteredTimestampMigration
import org.thoughtcrime.securesms.database.helpers.migration.V157_RecipeintHiddenMigration
import org.thoughtcrime.securesms.database.helpers.migration.V158_GroupsLastForceUpdateTimestampMigration
import org.thoughtcrime.securesms.database.helpers.migration.V159_ThreadUnreadSelfMentionCount
import org.thoughtcrime.securesms.database.helpers.migration.V160_SmsMmsExportedIndexMigration
import org.thoughtcrime.securesms.database.helpers.migration.V161_StorySendMessageIdIndex
import org.thoughtcrime.securesms.database.helpers.migration.V162_ThreadUnreadSelfMentionCountFixup
import org.thoughtcrime.securesms.database.helpers.migration.V163_RemoteMegaphoneSnoozeSupportMigration
import org.thoughtcrime.securesms.database.helpers.migration.V164_ThreadDatabaseReadIndexMigration
import org.thoughtcrime.securesms.database.helpers.migration.V165_MmsMessageBoxPaymentTransactionIndexMigration
import org.thoughtcrime.securesms.database.helpers.migration.V166_ThreadAndMessageForeignKeys
import org.thoughtcrime.securesms.database.helpers.migration.V167_RecreateReactionTriggers
import org.thoughtcrime.securesms.database.helpers.migration.V168_SingleMessageTableMigration
import org.thoughtcrime.securesms.database.helpers.migration.V169_EmojiSearchIndexRank
import org.thoughtcrime.securesms.database.helpers.migration.V170_CallTableMigration
import org.thoughtcrime.securesms.database.helpers.migration.V171_ThreadForeignKeyFix
import org.thoughtcrime.securesms.database.helpers.migration.V172_GroupMembershipMigration
import org.thoughtcrime.securesms.database.helpers.migration.V173_ScheduledMessagesMigration
import org.thoughtcrime.securesms.database.helpers.migration.V174_ReactionForeignKeyMigration
import org.thoughtcrime.securesms.database.helpers.migration.V175_FixFullTextSearchLink
import org.thoughtcrime.securesms.database.helpers.migration.V176_AddScheduledDateToQuoteIndex
import org.thoughtcrime.securesms.database.helpers.migration.V177_MessageSendLogTableCleanupMigration
import org.thoughtcrime.securesms.database.helpers.migration.V178_ReportingTokenColumnMigration
import org.thoughtcrime.securesms.database.helpers.migration.V179_CleanupDanglingMessageSendLogMigration
import org.thoughtcrime.securesms.database.helpers.migration.V180_RecipientNicknameMigration
import org.thoughtcrime.securesms.database.helpers.migration.V181_ThreadTableForeignKeyCleanup
import org.thoughtcrime.securesms.database.helpers.migration.V182_CallTableMigration
import org.thoughtcrime.securesms.database.helpers.migration.V183_CallLinkTableMigration
import org.thoughtcrime.securesms.database.helpers.migration.V184_CallLinkReplaceIndexMigration
import org.thoughtcrime.securesms.database.helpers.migration.V185_MessageRecipientsAndEditMessageMigration
import org.thoughtcrime.securesms.database.helpers.migration.V186_ForeignKeyIndicesMigration
import org.thoughtcrime.securesms.database.helpers.migration.V187_MoreForeignKeyIndexesMigration
import org.thoughtcrime.securesms.database.helpers.migration.V188_FixMessageRecipientsAndEditMessageMigration
import org.thoughtcrime.securesms.database.helpers.migration.V189_CreateCallLinkTableColumnsAndRebuildFKReference
import org.thoughtcrime.securesms.database.helpers.migration.V190_UpdatePendingSelfDataMigration
import org.thoughtcrime.securesms.database.helpers.migration.V191_UniqueMessageMigrationV2
import org.thoughtcrime.securesms.database.helpers.migration.V192_CallLinkTableNullableRootKeys
import org.thoughtcrime.securesms.database.helpers.migration.V193_BackCallLinksWithRecipient
import org.thoughtcrime.securesms.database.helpers.migration.V194_KyberPreKeyMigration
import org.thoughtcrime.securesms.database.helpers.migration.V195_GroupMemberForeignKeyMigration
import org.thoughtcrime.securesms.database.helpers.migration.V196_BackCallLinksWithRecipientV2
import org.thoughtcrime.securesms.database.helpers.migration.V197_DropAvatarColorFromCallLinks
import org.thoughtcrime.securesms.database.helpers.migration.V198_AddMacDigestColumn
import org.thoughtcrime.securesms.database.helpers.migration.V199_AddThreadActiveColumn
import org.thoughtcrime.securesms.database.helpers.migration.V200_ResetPniColumn
import org.thoughtcrime.securesms.database.helpers.migration.V201_RecipientTableValidations
import org.thoughtcrime.securesms.database.helpers.migration.V202_DropMessageTableThreadDateIndex
import org.thoughtcrime.securesms.database.helpers.migration.V203_PreKeyStaleTimestamp
import org.thoughtcrime.securesms.database.helpers.migration.V204_GroupForeignKeyMigration
import org.thoughtcrime.securesms.database.helpers.migration.V205_DropPushTable
import org.thoughtcrime.securesms.database.helpers.migration.V206_AddConversationCountIndex
import org.thoughtcrime.securesms.database.helpers.migration.V207_AddChunkSizeColumn
import org.thoughtcrime.securesms.database.helpers.migration.V209_ClearRecipientPniFromAciColumn
import org.thoughtcrime.securesms.database.helpers.migration.V210_FixPniPossibleColumns
import org.thoughtcrime.securesms.database.helpers.migration.V211_ReceiptColumnRenames
import org.thoughtcrime.securesms.database.helpers.migration.V212_RemoveDistributionListUniqueConstraint
import org.thoughtcrime.securesms.database.helpers.migration.V213_FixUsernameInE164Column
import org.thoughtcrime.securesms.database.helpers.migration.V214_PhoneNumberSharingColumn
import org.thoughtcrime.securesms.database.helpers.migration.V215_RemoveAttachmentUniqueId
import org.thoughtcrime.securesms.database.helpers.migration.V216_PhoneNumberDiscoverable
import org.thoughtcrime.securesms.database.helpers.migration.V217_MessageTableExtrasColumn
import org.thoughtcrime.securesms.database.helpers.migration.V218_RecipientPniSignatureVerified
import org.thoughtcrime.securesms.database.helpers.migration.V219_PniPreKeyStores
import org.thoughtcrime.securesms.database.helpers.migration.V220_PreKeyConstraints
import org.thoughtcrime.securesms.database.helpers.migration.V221_AddReadColumnToCallEventsTable
import org.thoughtcrime.securesms.database.helpers.migration.V222_DataHashRefactor
import org.thoughtcrime.securesms.database.helpers.migration.V223_AddNicknameAndNoteFieldsToRecipientTable
import org.thoughtcrime.securesms.database.helpers.migration.V224_AddAttachmentArchiveColumns
import org.thoughtcrime.securesms.database.helpers.migration.V225_AddLocalUserJoinedStateAndGroupCallActiveState
import org.thoughtcrime.securesms.database.helpers.migration.V226_AddAttachmentMediaIdIndex
import org.thoughtcrime.securesms.database.helpers.migration.V227_AddAttachmentArchiveTransferState
import org.thoughtcrime.securesms.database.helpers.migration.V228_AddNameCollisionTables
import org.thoughtcrime.securesms.database.helpers.migration.V229_MarkMissedCallEventsNotified
import org.thoughtcrime.securesms.database.helpers.migration.V230_UnreadCountIndices
import org.thoughtcrime.securesms.database.helpers.migration.V231_ArchiveThumbnailColumns
import org.thoughtcrime.securesms.database.helpers.migration.V232_CreateInAppPaymentTable
import org.thoughtcrime.securesms.database.helpers.migration.V233_FixInAppPaymentTableDefaultNotifiedValue
import org.thoughtcrime.securesms.database.helpers.migration.V234_ThumbnailRestoreStateColumn
import org.thoughtcrime.securesms.database.helpers.migration.V235_AttachmentUuidColumn
import org.thoughtcrime.securesms.database.helpers.migration.V236_FixInAppSubscriberCurrencyIfAble
import org.thoughtcrime.securesms.database.helpers.migration.V237_ResetGroupForceUpdateTimestamps
import org.thoughtcrime.securesms.database.helpers.migration.V238_AddGroupSendEndorsementsColumns
import org.thoughtcrime.securesms.database.helpers.migration.V238_FixAttachmentIdJsonSerialization
import org.thoughtcrime.securesms.database.helpers.migration.V239_MessageFullTextSearchEmojiSupport
import org.thoughtcrime.securesms.database.helpers.migration.V240_MessageFullTextSearchSecureDelete
import org.thoughtcrime.securesms.database.helpers.migration.V241_ExpireTimerVersion
import org.thoughtcrime.securesms.database.helpers.migration.V242_MessageFullTextSearchEmojiSupportV2
import org.thoughtcrime.securesms.database.helpers.migration.V243_MessageFullTextSearchDisableSecureDelete
import org.thoughtcrime.securesms.database.helpers.migration.V244_AttachmentRemoteIv
import org.thoughtcrime.securesms.database.helpers.migration.V245_DeletionTimestampOnCallLinks
import org.thoughtcrime.securesms.database.helpers.migration.V246_DropThumbnailCdnFromAttachments
import org.thoughtcrime.securesms.database.helpers.migration.V247_ClearUploadTimestamp
import org.thoughtcrime.securesms.database.helpers.migration.V250_ClearUploadTimestampV2
import org.thoughtcrime.securesms.database.helpers.migration.V251_ArchiveTransferStateIndex
import org.thoughtcrime.securesms.database.helpers.migration.V252_AttachmentOffloadRestoredAtColumn
import org.thoughtcrime.securesms.database.helpers.migration.V253_CreateChatFolderTables
import org.thoughtcrime.securesms.database.helpers.migration.V254_AddChatFolderConstraint
import org.thoughtcrime.securesms.database.helpers.migration.V255_AddCallTableLogIndex
import org.thoughtcrime.securesms.database.helpers.migration.V256_FixIncrementalDigestColumns
import org.thoughtcrime.securesms.database.helpers.migration.V257_CreateBackupMediaSyncTable
import org.thoughtcrime.securesms.database.helpers.migration.V258_FixGroupRevokedInviteeUpdate
import org.thoughtcrime.securesms.database.helpers.migration.V259_AdjustNotificationProfileMidnightEndTimes
import org.thoughtcrime.securesms.database.helpers.migration.V260_RemapQuoteAuthors
import org.thoughtcrime.securesms.database.helpers.migration.V261_RemapCallRingers
import org.thoughtcrime.securesms.database.helpers.migration.V263_InAppPaymentsSubscriberTableRebuild
import org.thoughtcrime.securesms.database.helpers.migration.V264_FixGroupAddMemberUpdate
import org.thoughtcrime.securesms.database.helpers.migration.V265_FixFtsTriggers
import org.thoughtcrime.securesms.database.helpers.migration.V266_UniqueThreadPinOrder
import org.thoughtcrime.securesms.database.helpers.migration.V267_FixGroupInvitationDeclinedUpdate
import org.thoughtcrime.securesms.database.helpers.migration.V268_FixInAppPaymentsErrorStateConsistency
import org.thoughtcrime.securesms.database.helpers.migration.V268_RestorePaymentTable
import org.thoughtcrime.securesms.database.helpers.migration.V269_BackupMediaSnapshotChanges
import org.thoughtcrime.securesms.database.helpers.migration.V270_FixChatFolderColumnsForStorageSync
import org.thoughtcrime.securesms.database.helpers.migration.V271_AddNotificationProfileIdColumn
import org.thoughtcrime.securesms.database.helpers.migration.V272_UpdateUnreadCountIndices
import org.thoughtcrime.securesms.database.helpers.migration.V273_FixUnreadOriginalMessages
import org.thoughtcrime.securesms.database.helpers.migration.V274_BackupMediaSnapshotLastSeenOnRemote
import org.thoughtcrime.securesms.database.helpers.migration.V275_EnsureDefaultAllChatsFolder
import org.thoughtcrime.securesms.database.helpers.migration.V276_AttachmentCdnDefaultValueMigration
import org.thoughtcrime.securesms.database.helpers.migration.V277_AddNotificationProfileStorageSync
import org.thoughtcrime.securesms.database.helpers.migration.V278_BackupSnapshotTableVersions
import org.thoughtcrime.securesms.database.helpers.migration.V279_AddNotificationProfileForeignKey
import org.thoughtcrime.securesms.database.helpers.migration.V280_RemoveAttachmentIv
import org.thoughtcrime.securesms.database.helpers.migration.V281_RemoveArchiveTransferFile
import org.thoughtcrime.securesms.database.helpers.migration.V282_AddSnippetMessageIdColumnToThreadTable
import org.thoughtcrime.securesms.database.helpers.migration.V283_ViewOnceRemoteDataCleanup
import org.thoughtcrime.securesms.database.helpers.migration.V284_SetPlaceholderGroupFlag
import org.thoughtcrime.securesms.database.helpers.migration.V285_AddEpochToCallLinksTable
import org.thoughtcrime.securesms.database.helpers.migration.V286_FixRemoteKeyEncoding
import org.thoughtcrime.securesms.database.helpers.migration.V287_FixInvalidArchiveState
import org.thoughtcrime.securesms.database.helpers.migration.V288_CopyStickerDataHashStartToEnd
import org.thoughtcrime.securesms.database.helpers.migration.V289_AddQuoteTargetContentTypeColumn
import org.thoughtcrime.securesms.database.helpers.migration.V290_AddArchiveThumbnailTransferStateColumn
import org.thoughtcrime.securesms.database.helpers.migration.V291_NullOutRemoteKeyIfEmpty
import org.thoughtcrime.securesms.database.helpers.migration.V292_AddPollTables
import org.thoughtcrime.securesms.database.helpers.migration.V294_RemoveLastResortKeyTupleColumnConstraintMigration
import org.thoughtcrime.securesms.database.helpers.migration.V295_AddLastRestoreKeyTypeTableIfMissingMigration
import org.thoughtcrime.securesms.database.SQLiteDatabase as SignalSqliteDatabase

/**
 * Contains all of the database migrations for [SignalDatabase]. Broken into a separate file for cleanliness.
 */
object SignalDatabaseMigrations {

  val TAG: String = Log.tag(SignalDatabaseMigrations.javaClass)

  private val signalMigrations: List<Pair<Int, SignalDatabaseMigration>> = listOf(
    149 to V149_LegacyMigrations,
    150 to V150_UrgentMslFlagMigration,
    151 to V151_MyStoryMigration,
    152 to V152_StoryGroupTypesMigration,
    153 to V153_MyStoryMigration,
    154 to V154_PniSignaturesMigration,
    155 to V155_SmsExporterMigration,
    156 to V156_RecipientUnregisteredTimestampMigration,
    157 to V157_RecipeintHiddenMigration,
    158 to V158_GroupsLastForceUpdateTimestampMigration,
    159 to V159_ThreadUnreadSelfMentionCount,
    160 to V160_SmsMmsExportedIndexMigration,
    161 to V161_StorySendMessageIdIndex,
    162 to V162_ThreadUnreadSelfMentionCountFixup,
    163 to V163_RemoteMegaphoneSnoozeSupportMigration,
    164 to V164_ThreadDatabaseReadIndexMigration,
    165 to V165_MmsMessageBoxPaymentTransactionIndexMigration,
    166 to V166_ThreadAndMessageForeignKeys,
    167 to V167_RecreateReactionTriggers,
    168 to V168_SingleMessageTableMigration,
    169 to V169_EmojiSearchIndexRank,
    170 to V170_CallTableMigration,
    171 to V171_ThreadForeignKeyFix,
    172 to V172_GroupMembershipMigration,
    173 to V173_ScheduledMessagesMigration,
    174 to V174_ReactionForeignKeyMigration,
    175 to V175_FixFullTextSearchLink,
    176 to V176_AddScheduledDateToQuoteIndex,
    177 to V177_MessageSendLogTableCleanupMigration,
    178 to V178_ReportingTokenColumnMigration,
    179 to V179_CleanupDanglingMessageSendLogMigration,
    180 to V180_RecipientNicknameMigration,
    181 to V181_ThreadTableForeignKeyCleanup,
    182 to V182_CallTableMigration,
    183 to V183_CallLinkTableMigration,
    184 to V184_CallLinkReplaceIndexMigration,
    185 to V185_MessageRecipientsAndEditMessageMigration,
    186 to V186_ForeignKeyIndicesMigration,
    187 to V187_MoreForeignKeyIndexesMigration,
    188 to V188_FixMessageRecipientsAndEditMessageMigration,
    189 to V189_CreateCallLinkTableColumnsAndRebuildFKReference,
    191 to V191_UniqueMessageMigrationV2,
    192 to V192_CallLinkTableNullableRootKeys,
    193 to V193_BackCallLinksWithRecipient,
    194 to V194_KyberPreKeyMigration,
    195 to V195_GroupMemberForeignKeyMigration,
    196 to V196_BackCallLinksWithRecipientV2,
    197 to V197_DropAvatarColorFromCallLinks,
    198 to V198_AddMacDigestColumn,
    199 to V199_AddThreadActiveColumn,
    200 to V200_ResetPniColumn,
    201 to V201_RecipientTableValidations,
    202 to V202_DropMessageTableThreadDateIndex,
    203 to V203_PreKeyStaleTimestamp,
    204 to V204_GroupForeignKeyMigration,
    205 to V205_DropPushTable,
    206 to V206_AddConversationCountIndex,
    207 to V207_AddChunkSizeColumn,
    // 208 was a bad migration that only manipulated data and did not change schema, replaced by 209
    209 to V209_ClearRecipientPniFromAciColumn,
    210 to V210_FixPniPossibleColumns,
    211 to V211_ReceiptColumnRenames,
    212 to V212_RemoveDistributionListUniqueConstraint,
    213 to V213_FixUsernameInE164Column,
    214 to V214_PhoneNumberSharingColumn,
    215 to V215_RemoveAttachmentUniqueId,
    216 to V216_PhoneNumberDiscoverable,
    217 to V217_MessageTableExtrasColumn,
    218 to V218_RecipientPniSignatureVerified,
    219 to V219_PniPreKeyStores,
    220 to V220_PreKeyConstraints,
    221 to V221_AddReadColumnToCallEventsTable,
    222 to V222_DataHashRefactor,
    223 to V223_AddNicknameAndNoteFieldsToRecipientTable,
    224 to V224_AddAttachmentArchiveColumns,
    225 to V225_AddLocalUserJoinedStateAndGroupCallActiveState,
    226 to V226_AddAttachmentMediaIdIndex,
    227 to V227_AddAttachmentArchiveTransferState,
    228 to V228_AddNameCollisionTables,
    229 to V229_MarkMissedCallEventsNotified,
    230 to V230_UnreadCountIndices,
    231 to V231_ArchiveThumbnailColumns,
    232 to V232_CreateInAppPaymentTable,
    233 to V233_FixInAppPaymentTableDefaultNotifiedValue,
    234 to V234_ThumbnailRestoreStateColumn,
    235 to V235_AttachmentUuidColumn,
    236 to V236_FixInAppSubscriberCurrencyIfAble,
    237 to V237_ResetGroupForceUpdateTimestamps,
    238 to V238_AddGroupSendEndorsementsColumns,
    239 to V239_MessageFullTextSearchEmojiSupport,
    240 to V240_MessageFullTextSearchSecureDelete,
    241 to V241_ExpireTimerVersion,
    242 to V242_MessageFullTextSearchEmojiSupportV2,
    243 to V243_MessageFullTextSearchDisableSecureDelete,
    244 to V244_AttachmentRemoteIv,
    245 to V245_DeletionTimestampOnCallLinks,
    246 to V246_DropThumbnailCdnFromAttachments,
    247 to V247_ClearUploadTimestamp,
    // 248 and 249 were originally in 7.18.0, but are now skipped because we needed to hotfix 7.17.6 after 7.18.0 was already released.
    250 to V250_ClearUploadTimestampV2,
    251 to V251_ArchiveTransferStateIndex,
    252 to V252_AttachmentOffloadRestoredAtColumn,
    253 to V253_CreateChatFolderTables,
    254 to V254_AddChatFolderConstraint,
    255 to V255_AddCallTableLogIndex,
    256 to V256_FixIncrementalDigestColumns,
    257 to V257_CreateBackupMediaSyncTable,
    258 to V258_FixGroupRevokedInviteeUpdate,
    259 to V259_AdjustNotificationProfileMidnightEndTimes,
    260 to V260_RemapQuoteAuthors,
    261 to V261_RemapCallRingers,
    // V263 was originally V262, but a typo in the version mapping caused it not to be run.
    263 to V263_InAppPaymentsSubscriberTableRebuild,
    264 to V264_FixGroupAddMemberUpdate,
    265 to V265_FixFtsTriggers,
    266 to V266_UniqueThreadPinOrder,
    267 to V267_FixGroupInvitationDeclinedUpdate,
    268 to V268_FixInAppPaymentsErrorStateConsistency,
    269 to V269_BackupMediaSnapshotChanges,
    270 to V270_FixChatFolderColumnsForStorageSync,
    271 to V271_AddNotificationProfileIdColumn,
    272 to V272_UpdateUnreadCountIndices,
    273 to V273_FixUnreadOriginalMessages,
    274 to V274_BackupMediaSnapshotLastSeenOnRemote,
    275 to V275_EnsureDefaultAllChatsFolder,
    276 to V276_AttachmentCdnDefaultValueMigration,
    277 to V277_AddNotificationProfileStorageSync,
    278 to V278_BackupSnapshotTableVersions,
    279 to V279_AddNotificationProfileForeignKey,
    280 to V280_RemoveAttachmentIv,
    281 to V281_RemoveArchiveTransferFile,
    282 to V282_AddSnippetMessageIdColumnToThreadTable,
    283 to V283_ViewOnceRemoteDataCleanup,
    284 to V284_SetPlaceholderGroupFlag,
    285 to V285_AddEpochToCallLinksTable,
    286 to V286_FixRemoteKeyEncoding,
    287 to V287_FixInvalidArchiveState,
    288 to V288_CopyStickerDataHashStartToEnd,
    289 to V289_AddQuoteTargetContentTypeColumn,
    290 to V290_AddArchiveThumbnailTransferStateColumn,
    291 to V291_NullOutRemoteKeyIfEmpty,
    292 to V292_AddPollTables,
    // 293 to V293_LastResortKeyTupleTableMigration, - removed due to crashing on some devices.
    294 to V294_RemoveLastResortKeyTupleColumnConstraintMigration,
    295 to V295_AddLastRestoreKeyTypeTableIfMissingMigration
  )

  const val DATABASE_VERSION = 295

  // MOLLY: Optional additional migrations specific to Molly
  private val extraMigrations: List<Pair<Int, SignalDatabaseMigration>> = listOf(
    190 to V190_UpdatePendingSelfDataMigration,
    238 to V238_FixAttachmentIdJsonSerialization,
    268 to V268_RestorePaymentTable,
  )

  @JvmStatic
  fun migrate(context: Application, db: SignalSqliteDatabase, oldVersion: Int, newVersion: Int) {
    val initialForeignKeyState = db.areForeignKeyConstraintsEnabled()

    // Merge migrations by version, with `signalMigrations` first
    val migrations = mutableMapOf<Int, MutableList<SignalDatabaseMigration>>()
      .apply {
        for ((v, migration) in signalMigrations) {
          require(putIfAbsent(v, mutableListOf(migration)) == null) {
            "Duplicated migration for version $v"
          }
        }
        for ((v, migration) in extraMigrations) {
          getOrPut(v) { mutableListOf() }.add(migration)
        }
      }
      .toSortedMap()
      .also { sortedMap ->
        sortedMap.forEach { (v, migrationList) ->
          check(migrationList.map { it.enableForeignKeys }.distinct().size == 1) {
            "Inconsistent foreign key constraints for version $v"
          }
        }
      }

    val eligibleMigrations = if (newVersion < 0) {
      migrations.filter { (version, _) -> version > oldVersion }
    } else {
      migrations.filter { (version, _) -> version > oldVersion && version <= newVersion }
    }

    for (migrationData in eligibleMigrations) {
      val (version, migrationList) = migrationData
      val enableForeignKeys = migrationList.first().enableForeignKeys
      val migrationNames = migrationList.joinToString(", ") { it.javaClass.simpleName }

      Log.i(TAG, "Running migration for version $version: $migrationNames. Foreign keys: $enableForeignKeys")
      val startTime = System.currentTimeMillis()

      var ftsException: SQLiteException? = null

      db.setForeignKeyConstraintsEnabled(enableForeignKeys)
      db.beginTransaction()
      try {
        migrationList.forEach { migration ->
          migration.migrate(context, db, oldVersion, newVersion)
        }
        db.version = version
        db.setTransactionSuccessful()
      } catch (e: SQLiteException) {
        if (e.message?.contains("invalid fts5 file format") == true || e.message?.contains("vtable constructor failed") == true) {
          ftsException = e
        } else {
          throw e
        }
      } finally {
        db.endTransaction()
      }

      if (ftsException != null) {
        Log.w(TAG, "Encountered FTS format issue! Attempting to repair.", ftsException)
        SignalDatabase.messageSearch.fullyResetTables(db)
        throw ftsException
      }

      Log.i(TAG, "Successfully completed migration for version $version in ${System.currentTimeMillis() - startTime} ms")
    }

    db.setForeignKeyConstraintsEnabled(initialForeignKeyState)
  }

  @JvmStatic
  fun migratePostTransaction(context: Context, oldVersion: Int) {
    // MOLLY: MIGRATE_PREKEYS_VERSION was removed
  }
}

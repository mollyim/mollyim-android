package org.thoughtcrime.securesms.groups;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.groups.ClientZkGroupCipher;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.libsignal.zkgroup.groups.UuidCiphertext;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.GroupChangeResponse;
import org.signal.storageservice.protos.groups.GroupExternalCredential;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.database.model.databaseprotos.GV2UpdateDescription;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.groups.v2.GroupCandidateHelper;
import org.thoughtcrime.securesms.groups.v2.GroupInviteLinkUrl;
import org.thoughtcrime.securesms.groups.v2.GroupLinkPassword;
import org.thoughtcrime.securesms.groups.v2.processing.GroupUpdateResult;
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor;
import org.thoughtcrime.securesms.jobs.ProfileUploadJob;
import org.thoughtcrime.securesms.jobs.PushGroupSilentUpdateSendJob;
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingMessage;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.ProfileUtil;
import org.whispersystems.signalservice.api.groupsv2.DecryptChangeVerificationMode;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupResponse;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupCandidate;
import org.whispersystems.signalservice.api.groupsv2.GroupChangeReconstruct;
import org.whispersystems.signalservice.api.groupsv2.GroupChangeUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.groupsv2.NotAbleToApplyGroupV2ChangeException;
import org.whispersystems.signalservice.api.groupsv2.ReceivedGroupSendEndorsements;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.ServiceIds;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.ConflictException;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.exceptions.GroupExistsException;
import org.whispersystems.signalservice.internal.push.exceptions.GroupPatchNotAcceptedException;
import org.whispersystems.signalservice.internal.push.exceptions.NotInGroupException;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import okio.ByteString;

final class GroupManagerV2 {

  private static final String TAG = Log.tag(GroupManagerV2.class);

  private final Context                context;
  private final GroupTable             groupDatabase;
  private final GroupsV2Api            groupsV2Api;
  private final GroupsV2Operations     groupsV2Operations;
  private final GroupsV2Authorization  authorization;
  private final ServiceIds             serviceIds;
  private final ACI                    selfAci;
  private final PNI                    selfPni;
  private final GroupCandidateHelper   groupCandidateHelper;
  private final SendGroupUpdateHelper  sendGroupUpdateHelper;

  GroupManagerV2(@NonNull Context context) {
    this(context,
         SignalDatabase.groups(),
         AppDependencies.getSignalServiceAccountManager().getGroupsV2Api(),
         AppDependencies.getGroupsV2Operations(),
         AppDependencies.getGroupsV2Authorization(),
         SignalStore.account().getServiceIds(),
         new GroupCandidateHelper(),
         new SendGroupUpdateHelper(context));
  }

  @VisibleForTesting GroupManagerV2(Context context,
                                    GroupTable groupDatabase,
                                    GroupsV2Api groupsV2Api,
                                    GroupsV2Operations groupsV2Operations,
                                    GroupsV2Authorization authorization,
                                    ServiceIds serviceIds,
                                    GroupCandidateHelper groupCandidateHelper,
                                    SendGroupUpdateHelper sendGroupUpdateHelper)
  {
    this.context                = context;
    this.groupDatabase          = groupDatabase;
    this.groupsV2Api            = groupsV2Api;
    this.groupsV2Operations     = groupsV2Operations;
    this.authorization          = authorization;
    this.serviceIds             = serviceIds;
    this.selfAci                = serviceIds.getAci();
    this.selfPni                = serviceIds.requirePni();
    this.groupCandidateHelper   = groupCandidateHelper;
    this.sendGroupUpdateHelper  = sendGroupUpdateHelper;
  }

  @NonNull DecryptedGroupJoinInfo getGroupJoinInfoFromServer(@NonNull GroupMasterKey groupMasterKey, @Nullable GroupLinkPassword password)
      throws IOException, VerificationFailedException, GroupLinkNotActiveException
  {
    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);

    return groupsV2Api.getGroupJoinInfo(groupSecretParams,
                                        Optional.ofNullable(password).map(GroupLinkPassword::serialize),
                                        authorization.getAuthorizationForToday(serviceIds, groupSecretParams));
  }

  @WorkerThread
  @NonNull GroupExternalCredential getGroupExternalCredential(@NonNull GroupId.V2 groupId)
      throws IOException, VerificationFailedException
  {
    GroupMasterKey groupMasterKey = SignalDatabase.groups()
                                                   .requireGroup(groupId)
                                                   .requireV2GroupProperties()
                                                   .getGroupMasterKey();

    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);

    return groupsV2Api.getGroupExternalCredential(authorization.getAuthorizationForToday(serviceIds, groupSecretParams));
  }

  @WorkerThread
  @NonNull Map<UUID, UuidCiphertext> getUuidCipherTexts(@NonNull GroupId.V2 groupId) {
    GroupRecord                  groupRecord       = SignalDatabase.groups().requireGroup(groupId);
    GroupTable.V2GroupProperties v2GroupProperties = groupRecord.requireV2GroupProperties();
    GroupMasterKey               groupMasterKey      = v2GroupProperties.getGroupMasterKey();
    ClientZkGroupCipher          clientZkGroupCipher = new ClientZkGroupCipher(GroupSecretParams.deriveFromMasterKey(groupMasterKey));
    List<Recipient>              recipients          = v2GroupProperties.getMemberRecipients(GroupTable.MemberSet.FULL_MEMBERS_INCLUDING_SELF);

    Map<UUID, UuidCiphertext> uuidCipherTexts = new HashMap<>();
    for (Recipient recipient : recipients) {
      uuidCipherTexts.put(recipient.requireServiceId().getRawUuid(), clientZkGroupCipher.encrypt(recipient.requireServiceId().getLibSignalServiceId()));
    }

    return uuidCipherTexts;
  }

  @WorkerThread
  GroupCreator create() throws GroupChangeBusyException {
    return new GroupCreator(GroupsV2ProcessingLock.acquireGroupProcessingLock());
  }

  @WorkerThread
  GroupEditor edit(@NonNull GroupId.V2 groupId) throws GroupChangeBusyException {
    return new GroupEditor(groupId, GroupsV2ProcessingLock.acquireGroupProcessingLock());
  }

  @WorkerThread
  GroupJoiner join(@NonNull GroupMasterKey groupMasterKey, @NonNull GroupLinkPassword password) throws GroupChangeBusyException {
    return new GroupJoiner(groupMasterKey, password, GroupsV2ProcessingLock.acquireGroupProcessingLock());
  }

  @WorkerThread
  GroupJoiner cancelRequest(@NonNull GroupId.V2 groupId) throws GroupChangeBusyException {
    GroupMasterKey groupMasterKey = SignalDatabase.groups()
                                                  .requireGroup(groupId)
                                                  .requireV2GroupProperties()
                                                  .getGroupMasterKey();

    return new GroupJoiner(groupMasterKey, null, GroupsV2ProcessingLock.acquireGroupProcessingLock());
  }

  @WorkerThread
  GroupUpdater updater(@NonNull GroupMasterKey groupId) throws GroupChangeBusyException {
    return new GroupUpdater(groupId, GroupsV2ProcessingLock.acquireGroupProcessingLock());
  }

  final class GroupCreator extends LockOwner {

    GroupCreator(@NonNull Closeable lock) {
      super(lock);
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult createGroup(@NonNull Collection<RecipientId> members,
                                                        @Nullable String name,
                                                        @Nullable byte[] avatar,
                                                        int disappearingMessagesTimer)
        throws GroupChangeFailedException, IOException, MembershipNotSuitableForV2Exception
    {
      GroupSecretParams groupSecretParams = GroupSecretParams.generate();
      DecryptedGroupResponse createGroupResponse;

      try {
        createGroupResponse = createGroupOnServer(groupSecretParams, name, avatar, members, disappearingMessagesTimer);
      } catch (GroupAlreadyExistsException e) {
        throw new GroupChangeFailedException(e);
      }

      DecryptedGroup                decryptedGroup        = createGroupResponse.getGroup();
      GroupMasterKey                masterKey             = groupSecretParams.getMasterKey();
      ReceivedGroupSendEndorsements groupSendEndorsements = groupsV2Operations.forGroup(groupSecretParams).receiveGroupSendEndorsements(selfAci, decryptedGroup, createGroupResponse.getGroupSendEndorsementsResponse());
      GroupId.V2                    groupId               = groupDatabase.create(masterKey, decryptedGroup, groupSendEndorsements);

      if (groupId == null) {
        throw new GroupChangeFailedException("Unable to create group, group already exists");
      }

      RecipientId              groupRecipientId = SignalDatabase.recipients().getOrInsertFromGroupId(groupId);
      Recipient                groupRecipient   = Recipient.resolved(groupRecipientId);

      AvatarHelper.setAvatar(context, groupRecipientId, avatar != null ? new ByteArrayInputStream(avatar) : null);
      groupDatabase.onAvatarUpdated(groupId, avatar != null);
      SignalDatabase.recipients().setProfileSharing(groupRecipient.getId(), true);

      DecryptedGroupChange groupChange = GroupChangeReconstruct.reconstructGroupChange(new DecryptedGroup(), decryptedGroup)
                                                               .newBuilder()
                                                               .editorServiceIdBytes(selfAci.toByteString())
                                                               .build();

      RecipientAndThread recipientAndThread = sendGroupUpdateHelper.sendGroupUpdate(masterKey, new GroupMutation(null, groupChange, decryptedGroup), null);

      return new GroupManager.GroupActionResult(recipientAndThread.groupRecipient,
                                                recipientAndThread.threadId,
                                                decryptedGroup.members.size() - 1,
                                                getPendingMemberRecipientIds(decryptedGroup.pendingMembers));
    }
  }

  @SuppressWarnings("UnusedReturnValue")
  final class GroupEditor extends LockOwner {

    private final GroupId.V2                   groupId;
    private final GroupTable.V2GroupProperties v2GroupProperties;
    private final GroupMasterKey               groupMasterKey;
    private final GroupSecretParams                  groupSecretParams;
    private final GroupsV2Operations.GroupOperations groupOperations;

    GroupEditor(@NonNull GroupId.V2 groupId, @NonNull Closeable lock) {
      super(lock);

      GroupRecord groupRecord = groupDatabase.requireGroup(groupId);

      this.groupId           = groupId;
      this.v2GroupProperties = groupRecord.requireV2GroupProperties();
      this.groupMasterKey    = v2GroupProperties.getGroupMasterKey();
      this.groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
      this.groupOperations   = groupsV2Operations.forGroup(groupSecretParams);
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult addMembers(@NonNull Collection<RecipientId> newMembers, @NonNull Set<ServiceId> bannedMembers)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, MembershipNotSuitableForV2Exception
    {
      if (!GroupsV2CapabilityChecker.allHaveServiceId(newMembers)) {
        throw new MembershipNotSuitableForV2Exception("At least one potential new member does not support GV2 or UUID capabilities");
      }

      Set<GroupCandidate> groupCandidates = groupCandidateHelper.recipientIdsToCandidates(new HashSet<>(newMembers));

      if (SignalStore.internal().getGv2ForceInvites()) {
        groupCandidates = GroupCandidate.withoutExpiringProfileKeyCredentials(groupCandidates);
      }

      return commitChangeWithConflictResolution(selfAci, groupOperations.createModifyGroupMembershipChange(groupCandidates, bannedMembers, selfAci));
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult updateGroupTimer(int expirationTime)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      return commitChangeWithConflictResolution(selfAci, groupOperations.createModifyGroupTimerChange(expirationTime));
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult updateAttributesRights(@NonNull GroupAccessControl newRights)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      return commitChangeWithConflictResolution(selfAci, groupOperations.createChangeAttributesRights(rightsToAccessControl(newRights)));
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult updateMembershipRights(@NonNull GroupAccessControl newRights)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      return commitChangeWithConflictResolution(selfAci, groupOperations.createChangeMembershipRights(rightsToAccessControl(newRights)));
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult updateAnnouncementGroup(boolean isAnnouncementGroup)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      return commitChangeWithConflictResolution(selfAci, groupOperations.createAnnouncementGroupChange(isAnnouncementGroup));
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult updateGroupTitleDescriptionAndAvatar(@Nullable String title, @Nullable String description, @Nullable byte[] avatarBytes, boolean avatarChanged)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      try {
        GroupChange.Actions.Builder change = title != null ? groupOperations.createModifyGroupTitle(title)
                                                           : new GroupChange.Actions.Builder();

        if (description != null) {
          change.modifyDescription(groupOperations.createModifyGroupDescriptionAction(description).build());
        }

        if (avatarChanged) {
          String cdnKey = avatarBytes != null ? groupsV2Api.uploadAvatar(avatarBytes, groupSecretParams, authorization.getAuthorizationForToday(serviceIds, groupSecretParams))
                                              : "";
          change.modifyAvatar(new GroupChange.Actions.ModifyAvatarAction.Builder().avatar(cdnKey).build());
        }

        GroupManager.GroupActionResult groupActionResult = commitChangeWithConflictResolution(selfAci, change);

        if (avatarChanged) {
          AvatarHelper.setAvatar(context, Recipient.externalGroupExact(groupId).getId(), avatarBytes != null ? new ByteArrayInputStream(avatarBytes) : null);
          groupDatabase.onAvatarUpdated(groupId, avatarBytes != null);
        }

        return groupActionResult;
      } catch (VerificationFailedException e) {
        throw new GroupChangeFailedException(e);
      }
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult revokeInvites(@NonNull ServiceId authServiceId, @NonNull Collection<UuidCiphertext> uuidCipherTexts, boolean sendToMembers)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      return commitChangeWithConflictResolution(authServiceId, groupOperations.createRemoveInvitationChange(new HashSet<>(uuidCipherTexts)), false, sendToMembers);
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult approveRequests(@NonNull Collection<RecipientId> recipientIds)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      Set<UUID> uuids = recipientIds.stream()
                                    .map(r -> Recipient.resolved(r).requireServiceId().getRawUuid())
                                    .collect(Collectors.toSet());

      return commitChangeWithConflictResolution(selfAci, groupOperations.createApproveGroupJoinRequest(uuids));
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult denyRequests(@NonNull Collection<RecipientId> recipientIds)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      Set<ACI> uuids = recipientIds.stream()
                                   .map(r -> Recipient.resolved(r).requireAci())
                                   .collect(Collectors.toSet());

      return commitChangeWithConflictResolution(selfAci, groupOperations.createRefuseGroupJoinRequest(uuids, true, v2GroupProperties.getDecryptedGroup().bannedMembers));
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult setMemberAdmin(@NonNull RecipientId recipientId,
                                                           boolean admin)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      Recipient recipient = Recipient.resolved(recipientId);
      return commitChangeWithConflictResolution(selfAci, groupOperations.createChangeMemberRole(recipient.requireAci(), admin ? Member.Role.ADMINISTRATOR : Member.Role.DEFAULT));
    }

    @WorkerThread
    void leaveGroup(boolean sendToMembers)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      GroupRecord    groupRecord    = groupDatabase.requireGroup(groupId);
      DecryptedGroup decryptedGroup = groupRecord.requireV2GroupProperties().getDecryptedGroup();
      Optional<DecryptedMember>        selfMember        = DecryptedGroupUtil.findMemberByAci(decryptedGroup.members, selfAci);
      Optional<DecryptedPendingMember> aciPendingMember  = DecryptedGroupUtil.findPendingByServiceId(decryptedGroup.pendingMembers, selfAci);
      Optional<DecryptedPendingMember> pniPendingMember  = DecryptedGroupUtil.findPendingByServiceId(decryptedGroup.pendingMembers, selfPni);
      Optional<DecryptedPendingMember> selfPendingMember = Optional.empty();
      ServiceId                        serviceId         = selfAci;

      if (aciPendingMember.isPresent()) {
        selfPendingMember = aciPendingMember;
      } else if (pniPendingMember.isPresent() && selfMember.isEmpty()) {
        selfPendingMember = pniPendingMember;
        serviceId         = selfPni;
      }

      if (selfPendingMember.isPresent()) {
        try {
          revokeInvites(serviceId, Collections.singleton(new UuidCiphertext(selfPendingMember.get().serviceIdCipherText.toByteArray())), false);
        } catch (InvalidInputException e) {
          throw new AssertionError(e);
        }
      } else if (selfMember.isPresent()) {
        ejectMember(selfAci, true, false, sendToMembers);
      } else {
        Log.i(TAG, "Unable to leave group we are not pending or in");
      }
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult ejectMember(@NonNull ACI aci, boolean allowWhenBlocked, boolean ban, boolean sendToMembers)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      return commitChangeWithConflictResolution(selfAci,
                                                groupOperations.createRemoveMembersChange(Collections.singleton(aci),
                                                                                          ban,
                                                                                          ban ? v2GroupProperties.getDecryptedGroup().bannedMembers
                                                                                              : Collections.emptyList()),
                                                allowWhenBlocked,
                                                sendToMembers);
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult addMemberAdminsAndLeaveGroup(Collection<RecipientId> newAdmins)
        throws GroupChangeFailedException, GroupNotAMemberException, GroupInsufficientRightsException, IOException
    {
      List<UUID> newAdminRecipients = newAdmins.stream().map(id -> Recipient.resolved(id).requireServiceId().getRawUuid()).collect(Collectors.toList());

      return commitChangeWithConflictResolution(selfAci, groupOperations.createLeaveAndPromoteMembersToAdmin(selfAci,
                                                                                                             newAdminRecipients));
    }

    @WorkerThread
    @Nullable GroupManager.GroupActionResult updateSelfProfileKeyInGroup()
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      ProfileKey                profileKey  = ProfileKeyUtil.getSelfProfileKey();
      DecryptedGroup            group       = groupDatabase.requireGroup(groupId).requireV2GroupProperties().getDecryptedGroup();
      Optional<DecryptedMember> selfInGroup = DecryptedGroupUtil.findMemberByAci(group.members, selfAci);

      if (selfInGroup.isEmpty()) {
        Log.w(TAG, "Self not in group " + groupId);
        return null;
      }

      if (Arrays.equals(profileKey.serialize(), selfInGroup.get().profileKey.toByteArray())) {
        Log.i(TAG, "Own Profile Key is already up to date in group " + groupId);
        return null;
      } else {
        Log.i(TAG, "Profile Key does not match that in group " + groupId);
      }

      GroupCandidate selfGroupCandidate = groupCandidateHelper.recipientIdToCandidate(Recipient.self().getId());

      if (!selfGroupCandidate.hasValidProfileKeyCredential()) {
        Log.w(TAG, "[updateSelfProfileKeyInGroup] No credential available, repairing");
        ProfileUtil.uploadProfile(context);
        selfGroupCandidate = groupCandidateHelper.recipientIdToCandidate(Recipient.self().getId());

        if (!selfGroupCandidate.hasValidProfileKeyCredential()) {
          Log.w(TAG, "[updateSelfProfileKeyInGroup] Still unable to get credential");
          AppDependencies.getJobManager().add(new ProfileUploadJob());
          throw new GroupChangeFailedException("No profile credential for self, unable to update profile key in group");
        }
      }

      return commitChangeWithConflictResolution(selfAci, groupOperations.createUpdateProfileKeyCredentialChange(selfGroupCandidate.requireExpiringProfileKeyCredential()));
    }

    @WorkerThread
    @Nullable GroupManager.GroupActionResult acceptInvite()
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      DecryptedGroup            group       = groupDatabase.requireGroup(groupId).requireV2GroupProperties().getDecryptedGroup();
      Optional<DecryptedMember> selfInGroup = DecryptedGroupUtil.findMemberByAci(group.members, selfAci);

      if (selfInGroup.isPresent()) {
        Log.w(TAG, "Self already in group");
        return null;
      }

      Optional<DecryptedPendingMember> aciInPending = DecryptedGroupUtil.findPendingByServiceId(group.pendingMembers, selfAci);
      Optional<DecryptedPendingMember> pniInPending = DecryptedGroupUtil.findPendingByServiceId(group.pendingMembers, selfPni);

      GroupCandidate selfGroupCandidate = groupCandidateHelper.recipientIdToCandidate(Recipient.self().getId());

      if (!selfGroupCandidate.hasValidProfileKeyCredential()) {
        Log.w(TAG, "[acceptInvite] No credential available, repairing");
        ProfileUtil.uploadProfile(context);
        selfGroupCandidate = groupCandidateHelper.recipientIdToCandidate(Recipient.self().getId());

        if (!selfGroupCandidate.hasValidProfileKeyCredential()) {
          Log.w(TAG, "[acceptInvite] Still unable to get credential");
          AppDependencies.getJobManager().add(new ProfileUploadJob());
          throw new GroupChangeFailedException("No profile credential for self, unable to accept invite");
        }
      }

      if (aciInPending.isPresent()) {
        return commitChangeWithConflictResolution(selfAci, groupOperations.createAcceptInviteChange(selfGroupCandidate.requireExpiringProfileKeyCredential()));
      } else if (pniInPending.isPresent()) {
        return commitChangeWithConflictResolution(selfPni, groupOperations.createAcceptPniInviteChange(selfGroupCandidate.requireExpiringProfileKeyCredential()));
      }

      throw new GroupChangeFailedException("Unable to accept invite when not in pending list");
    }

    public GroupManager.GroupActionResult ban(ServiceId serviceId)
        throws GroupChangeFailedException, GroupNotAMemberException, GroupInsufficientRightsException, IOException
    {
      ByteString serviceIdByteString = serviceId.toByteString();
      boolean    rejectJoinRequest   = v2GroupProperties.getDecryptedGroup().requestingMembers.stream().anyMatch(m -> m.aciBytes.equals(serviceIdByteString));

      return commitChangeWithConflictResolution(selfAci, groupOperations.createBanServiceIdsChange(Collections.singleton(serviceId), rejectJoinRequest, v2GroupProperties.getDecryptedGroup().bannedMembers));
    }

    @WorkerThread
    public GroupManager.GroupActionResult cycleGroupLinkPassword()
        throws GroupChangeFailedException, GroupNotAMemberException, GroupInsufficientRightsException, IOException
    {
      return commitChangeWithConflictResolution(selfAci, groupOperations.createModifyGroupLinkPasswordChange(GroupLinkPassword.createNew().serialize()));
    }

    @WorkerThread
    public @Nullable GroupInviteLinkUrl setJoinByGroupLinkState(@NonNull GroupManager.GroupLinkState state)
        throws GroupChangeFailedException, GroupNotAMemberException, GroupInsufficientRightsException, IOException
    {
      AccessControl.AccessRequired access;

      switch (state) {
        case DISABLED             : access = AccessControl.AccessRequired.UNSATISFIABLE; break;
        case ENABLED              : access = AccessControl.AccessRequired.ANY;           break;
        case ENABLED_WITH_APPROVAL: access = AccessControl.AccessRequired.ADMINISTRATOR; break;
        default:                    throw new AssertionError();
      }

      GroupChange.Actions.Builder change = groupOperations.createChangeJoinByLinkRights(access);

      if (state != GroupManager.GroupLinkState.DISABLED) {
        DecryptedGroup group = groupDatabase.requireGroup(groupId).requireV2GroupProperties().getDecryptedGroup();

        if (group.inviteLinkPassword.size() == 0) {
          Log.d(TAG, "First time enabling group links for group and password empty, generating");
          change = groupOperations.createModifyGroupLinkPasswordAndRightsChange(GroupLinkPassword.createNew().serialize(), access);
        }
      }

      commitChangeWithConflictResolution(selfAci, change);

      if (state != GroupManager.GroupLinkState.DISABLED) {
        GroupTable.V2GroupProperties v2GroupProperties = groupDatabase.requireGroup(groupId).requireV2GroupProperties();
        GroupMasterKey               groupMasterKey    = v2GroupProperties.getGroupMasterKey();
        DecryptedGroup                  decryptedGroup    = v2GroupProperties.getDecryptedGroup();

        return GroupInviteLinkUrl.forGroup(groupMasterKey, decryptedGroup);
      } else {
        return null;
      }
    }

    private @NonNull GroupManager.GroupActionResult commitChangeWithConflictResolution(@NonNull ServiceId authServiceId, @NonNull GroupChange.Actions.Builder change)
        throws GroupChangeFailedException, GroupNotAMemberException, GroupInsufficientRightsException, IOException
    {
      return commitChangeWithConflictResolution(authServiceId, change, false, true);
    }

    private @NonNull GroupManager.GroupActionResult commitChangeWithConflictResolution(@NonNull ServiceId authServiceId, @NonNull GroupChange.Actions.Builder change, boolean allowWhenBlocked, boolean sendToMembers)
        throws GroupChangeFailedException, GroupNotAMemberException, GroupInsufficientRightsException, IOException
    {
      boolean refetchedAddMemberCredentials = false;
      change.sourceServiceId(UuidUtil.toByteString(authServiceId.getRawUuid()));

      for (int attempt = 0; attempt < 5; attempt++) {
        try {
          return commitChange(change, allowWhenBlocked, sendToMembers);
        } catch (GroupPatchNotAcceptedException e) {
          if (change.addMembers.size() > 0 && !refetchedAddMemberCredentials) {
            refetchedAddMemberCredentials = true;
            change = refetchAddMemberCredentials(change);
          } else {
            throw new GroupChangeFailedException(e);
          }
        } catch (ConflictException e) {
          Log.w(TAG, "Invalid group patch or conflict", e);

          change = resolveConflict(authServiceId, change);

          if (GroupChangeUtil.changeIsEmpty(change.build())) {
            Log.i(TAG, "Change is empty after conflict resolution");
            Recipient groupRecipient = Recipient.externalGroupExact(groupId);
            long      threadId       = SignalDatabase.threads().getOrCreateThreadIdFor(groupRecipient);

            return new GroupManager.GroupActionResult(groupRecipient, threadId, 0, Collections.emptyList());
          }
        }
      }

      throw new GroupChangeFailedException("Unable to apply change to group after conflicts");
    }

    private GroupChange.Actions.Builder resolveConflict(@NonNull ServiceId authServiceId, @NonNull GroupChange.Actions.Builder change)
          throws IOException, GroupNotAMemberException, GroupChangeFailedException
    {
      GroupUpdateResult groupUpdateResult = GroupsV2StateProcessor.forGroup(serviceIds, groupMasterKey)
                                                                  .updateLocalGroupToRevision(GroupsV2StateProcessor.LATEST, System.currentTimeMillis());

      if (groupUpdateResult.getLatestServer() == null) {
        Log.w(TAG, "Latest server state null.");
        throw new GroupChangeFailedException();
      }

      if (groupUpdateResult.getUpdateStatus() != GroupUpdateResult.UpdateStatus.GROUP_UPDATED) {
        int serverRevision = groupUpdateResult.getLatestServer().revision;
        int localRevision  = groupDatabase.requireGroup(groupId).requireV2GroupProperties().getGroupRevision();
        int revisionDelta  = serverRevision - localRevision;
        Log.w(TAG, String.format(Locale.US, "Server is ahead by %d revisions", revisionDelta));
        throw new GroupChangeFailedException();
      }

      Log.w(TAG, "Group has been updated");
      try {
        GroupChange.Actions changeActions = change.build();

        return GroupChangeUtil.resolveConflict(groupUpdateResult.getLatestServer(),
                                               groupOperations.decryptChange(changeActions, authServiceId),
                                               changeActions);
      } catch (VerificationFailedException | InvalidGroupStateException ex) {
        throw new GroupChangeFailedException(ex);
      }
    }

    private GroupChange.Actions.Builder refetchAddMemberCredentials(@NonNull GroupChange.Actions.Builder change) {
      try {
        List<RecipientId> ids = groupOperations.decryptAddMembers(change.addMembers)
                                               .stream()
                                               .map(RecipientId::from)
                                               .collect(Collectors.toList());

        for (RecipientId id : ids) {
          ProfileUtil.updateExpiringProfileKeyCredential(Recipient.resolved(id));
        }

        List<GroupCandidate> groupCandidates = groupCandidateHelper.recipientIdsToCandidatesList(ids);

        return groupOperations.replaceAddMembers(change, groupCandidates);
      } catch (InvalidGroupStateException | InvalidInputException | VerificationFailedException | IOException e) {
        Log.w(TAG, "Unable to refetch credentials for added members, failing change", e);
      }

      return change;
    }

    private GroupManager.GroupActionResult commitChange(@NonNull GroupChange.Actions.Builder change, boolean allowWhenBlocked, boolean sendToMembers)
        throws GroupNotAMemberException, GroupChangeFailedException, IOException, GroupInsufficientRightsException
    {
      final GroupRecord                  groupRecord       = groupDatabase.requireGroup(groupId);
      final GroupTable.V2GroupProperties v2GroupProperties = groupRecord.requireV2GroupProperties();
      final int                          nextRevision      = v2GroupProperties.getGroupRevision() + 1;
      final GroupChange.Actions          changeActions     = change.revision(nextRevision).build();
      final DecryptedGroupChange         decryptedChange;
      final DecryptedGroup               decryptedGroupState;
      final DecryptedGroup               previousGroupState;

      if (!allowWhenBlocked && Recipient.externalGroupExact(groupId).isBlocked()) {
        throw new GroupChangeFailedException("Group is blocked.");
      }

      previousGroupState  = v2GroupProperties.getDecryptedGroup();

      GroupChangeResponse changeResponse    = commitToServer(changeActions);
      GroupChange         signedGroupChange = changeResponse.groupChange;
      try {
        decryptedChange     = groupOperations.decryptChange(signedGroupChange, DecryptChangeVerificationMode.alreadyTrusted()).get();
        decryptedGroupState = DecryptedGroupUtil.apply(previousGroupState, decryptedChange);
      } catch (VerificationFailedException | InvalidGroupStateException | NotAbleToApplyGroupV2ChangeException e) {
        Log.w(TAG, e);
        throw new IOException(e);
      }

      groupDatabase.update(groupId, decryptedGroupState, groupsV2Operations.forGroup(groupSecretParams).receiveGroupSendEndorsements(selfAci, decryptedGroupState, changeResponse.groupSendEndorsementsResponse));

      GroupMutation      groupMutation      = new GroupMutation(previousGroupState, decryptedChange, decryptedGroupState);
      RecipientAndThread recipientAndThread = sendGroupUpdateHelper.sendGroupUpdate(groupMasterKey, groupMutation, signedGroupChange, sendToMembers);
      int                newMembersCount    = decryptedChange.newMembers.size();
      List<RecipientId>  newPendingMembers  = getPendingMemberRecipientIds(decryptedChange.newPendingMembers);

      return new GroupManager.GroupActionResult(recipientAndThread.groupRecipient, recipientAndThread.threadId, newMembersCount, newPendingMembers);
    }

    private @NonNull GroupChangeResponse commitToServer(@NonNull GroupChange.Actions change)
        throws GroupNotAMemberException, GroupChangeFailedException, IOException, GroupInsufficientRightsException
    {
      try {
        return groupsV2Api.patchGroup(change, authorization.getAuthorizationForToday(serviceIds, groupSecretParams), Optional.empty());
      } catch (NotInGroupException e) {
        Log.w(TAG, e);
        throw new GroupNotAMemberException(e);
      } catch (AuthorizationFailedException e) {
        Log.w(TAG, e);
        throw new GroupInsufficientRightsException(e);
      } catch (VerificationFailedException e) {
        Log.w(TAG, e);
        throw new GroupChangeFailedException(e);
      }
    }
  }

  final class GroupUpdater extends LockOwner {

    private final GroupMasterKey groupMasterKey;

    GroupUpdater(@NonNull GroupMasterKey groupMasterKey, @NonNull Closeable lock) {
      super(lock);

      this.groupMasterKey = groupMasterKey;
    }

    @WorkerThread
    void updateLocalToServerRevision(int revision, long timestamp)
        throws IOException, GroupNotAMemberException
    {
      GroupsV2StateProcessor.forGroup(serviceIds, groupMasterKey)
                            .updateLocalGroupToRevision(revision, timestamp);
    }

    @WorkerThread
    GroupUpdateResult updateLocalToServerRevision(int revision,
                                                  long timestamp,
                                                  @NonNull Optional<GroupRecord> localRecord,
                                                  @Nullable GroupSecretParams groupSecretParams,
                                                  @Nullable byte[] signedGroupChange,
                                                  @Nullable String serverGuid)
        throws IOException, GroupNotAMemberException
    {
      return GroupsV2StateProcessor.forGroup(serviceIds, groupMasterKey, groupSecretParams)
                                   .updateLocalGroupToRevision(revision, timestamp, getDecryptedGroupChange(signedGroupChange), localRecord, serverGuid);
    }

    @WorkerThread
    void forceSanityUpdateFromServer(long timestamp)
        throws IOException, GroupNotAMemberException
    {
      GroupsV2StateProcessor.forGroup(serviceIds, groupMasterKey)
                            .forceSanityUpdateFromServer(timestamp);
    }

    @WorkerThread
    void updateGroupSendEndorsements()
        throws IOException, GroupNotAMemberException
    {
      GroupsV2StateProcessor.forGroup(serviceIds, groupMasterKey)
                            .updateGroupSendEndorsements();
    }

    private DecryptedGroupChange getDecryptedGroupChange(@Nullable byte[] signedGroupChange) {
      if (signedGroupChange != null && signedGroupChange.length > 0) {
        GroupsV2Operations.GroupOperations groupOperations = groupsV2Operations.forGroup(GroupSecretParams.deriveFromMasterKey(groupMasterKey));

        try {
          return groupOperations.decryptChange(GroupChange.ADAPTER.decode(signedGroupChange), DecryptChangeVerificationMode.verify(GroupId.getIdentifierForMasterKey(groupMasterKey)))
                                .orElse(null);
        } catch (VerificationFailedException | InvalidGroupStateException | IOException e) {
          Log.w(TAG, "Unable to verify supplied group change", e);
        }
      }

      return null;
    }
  }

  @WorkerThread
  private @NonNull DecryptedGroupResponse createGroupOnServer(@NonNull GroupSecretParams groupSecretParams,
                                                              @Nullable String name,
                                                              @Nullable byte[] avatar,
                                                              @NonNull Collection<RecipientId> members,
                                                              int disappearingMessageTimerSeconds)
      throws GroupChangeFailedException, IOException, MembershipNotSuitableForV2Exception, GroupAlreadyExistsException
  {
    if (!GroupsV2CapabilityChecker.allAndSelfHaveServiceId(members)) {
      throw new MembershipNotSuitableForV2Exception("At least one potential new member does not support GV2 capability or we don't have their UUID");
    }

    SignalDatabase.recipients().clearProfileKeyCredential(Recipient.self().getId());

    GroupCandidate      self       = groupCandidateHelper.recipientIdToCandidate(Recipient.self().getId());
    Set<GroupCandidate> candidates = new HashSet<>(groupCandidateHelper.recipientIdsToCandidates(members));

    if (SignalStore.internal().getGv2ForceInvites()) {
      Log.w(TAG, "Forcing GV2 invites due to internal setting");
      candidates = GroupCandidate.withoutExpiringProfileKeyCredentials(candidates);
    }

    if (!self.hasValidProfileKeyCredential()) {
      Log.w(TAG, "[createGroupOnServer] No profile credential for self, repairing");
      ProfileUtil.uploadProfile(context);

      self = groupCandidateHelper.recipientIdToCandidate(Recipient.self().getId());

      if (!self.hasValidProfileKeyCredential()) {
        Log.w(TAG, "[createGroupOnServer] Still unable to get credential");
        AppDependencies.getJobManager().add(new ProfileUploadJob());
        throw new MembershipNotSuitableForV2Exception("Cannot create a V2 group as self does not have a versioned profile");
      }
    }

    GroupsV2Operations.NewGroup newGroup = groupsV2Operations.createNewGroup(groupSecretParams,
                                                                             name,
                                                                             Optional.ofNullable(avatar),
                                                                             self,
                                                                             candidates,
                                                                             Member.Role.DEFAULT,
                                                                             disappearingMessageTimerSeconds);

    try {
      DecryptedGroupResponse groupResponse = groupsV2Api.putNewGroup(newGroup, authorization.getAuthorizationForToday(serviceIds, groupSecretParams));

      return groupResponse;
    } catch (VerificationFailedException | InvalidGroupStateException | InvalidInputException e) {
      throw new GroupChangeFailedException(e);
    } catch (GroupExistsException e) {
      throw new GroupAlreadyExistsException(e);
    }
  }

  final class GroupJoiner extends LockOwner {
    private final GroupId.V2                         groupId;
    private final GroupLinkPassword                  password;
    private final GroupSecretParams                  groupSecretParams;
    private final GroupsV2Operations.GroupOperations groupOperations;
    private final GroupMasterKey                     groupMasterKey;

    public GroupJoiner(@NonNull GroupMasterKey groupMasterKey,
                       @Nullable GroupLinkPassword password,
                       @NonNull Closeable lock)
    {
      super(lock);

      this.groupId           = GroupId.v2(groupMasterKey);
      this.password          = password;
      this.groupMasterKey    = groupMasterKey;
      this.groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
      this.groupOperations   = groupsV2Operations.forGroup(groupSecretParams);
    }

    @WorkerThread
    public GroupManager.GroupActionResult joinGroup(@NonNull DecryptedGroupJoinInfo joinInfo,
                                                    @Nullable byte[] avatar)
        throws GroupChangeFailedException, IOException, MembershipNotSuitableForV2Exception, GroupLinkNotActiveException
    {
      boolean requestToJoin      = joinInfo.addFromInviteLink == AccessControl.AccessRequired.ADMINISTRATOR;
      boolean alreadyAMember     = false;
      boolean groupAlreadyExists = false;

      if (requestToJoin) {
        Log.i(TAG, "Requesting to join " + groupId);
      } else {
        Log.i(TAG, "Joining " + groupId);
      }

      GroupChangeResponse  groupChangeResponse = null;
      GroupChange          signedGroupChange   = null;
      DecryptedGroupChange decryptedChange     = null;

      try {
        groupChangeResponse = joinGroupOnServer(requestToJoin, joinInfo.revision);
        signedGroupChange   = groupChangeResponse.groupChange;

        if (requestToJoin) {
          Log.i(TAG, String.format("Successfully requested to join %s on server", groupId));
        } else {
          Log.i(TAG, String.format("Successfully added self to %s on server", groupId));
        }

        decryptedChange = decryptChange(Objects.requireNonNull(signedGroupChange));
      } catch (GroupJoinAlreadyAMemberException e) {
        Log.i(TAG, "Server reports that we are already a member of " + groupId);
        alreadyAMember = true;
      }

      DecryptedGroup decryptedGroup = createPlaceholderGroup(joinInfo, requestToJoin);

      Optional<GroupRecord> group = groupDatabase.getGroup(groupId);

      if (group.isPresent()) {
        Log.i(TAG, "Group already present locally");
        groupAlreadyExists = true;
      } else {
        GroupId.V2 groupId = groupDatabase.create(groupMasterKey, decryptedGroup, null);

        if (groupId != null) {
          Log.i(TAG, "Created local group with placeholder");
        } else {
          Log.i(TAG, "Create placeholder failed, group suddenly present locally");
          groupAlreadyExists = true;
        }
      }

      if (groupAlreadyExists) {
        Log.i(TAG, "Attempting to update local group with change/server");
        try {
          GroupsV2StateProcessor.forGroup(SignalStore.account().getServiceIds(), groupMasterKey)
                                .updateLocalGroupToRevision(decryptedChange != null ? decryptedChange.revision : GroupsV2StateProcessor.LATEST, System.currentTimeMillis(), decryptedChange);
        } catch (GroupNotAMemberException e) {
          Log.w(TAG, "Despite adding self to group, change/server says we are not a member, scheduling refresh of group info " + groupId, e);

          AppDependencies.getJobManager()
                         .add(new RequestGroupV2InfoJob(groupId));

          throw new GroupChangeFailedException(e);
        } catch (IOException e) {
          Log.w(TAG, "Group data fetch failed, scheduling refresh of group info " + groupId, e);

          AppDependencies.getJobManager()
                         .add(new RequestGroupV2InfoJob(groupId));

          throw e;
        }
      }

      RecipientId groupRecipientId = SignalDatabase.recipients().getOrInsertFromGroupId(groupId);
      Recipient   groupRecipient   = Recipient.resolved(groupRecipientId);

      AvatarHelper.setAvatar(context, groupRecipientId, avatar != null ? new ByteArrayInputStream(avatar) : null);
      groupDatabase.onAvatarUpdated(groupId, avatar != null);
      SignalDatabase.recipients().setProfileSharing(groupRecipientId, true);

      if (alreadyAMember) {
        Log.i(TAG, "Already a member of the group");

        ThreadTable threadTable = SignalDatabase.threads();
        long        threadId    = threadTable.getOrCreateValidThreadId(groupRecipient, -1);

        return new GroupManager.GroupActionResult(groupRecipient,
                                                  threadId,
                                                  0,
                                                  Collections.emptyList());
      } else if (requestToJoin) {
        Log.i(TAG, "Requested to join, cannot send update");

        RecipientAndThread recipientAndThread = sendGroupUpdateHelper.sendGroupUpdate(groupMasterKey, new GroupMutation(null, decryptedChange, decryptedGroup), signedGroupChange, false);

        return new GroupManager.GroupActionResult(groupRecipient,
                                                  recipientAndThread.threadId,
                                                  0,
                                                  Collections.emptyList());
      } else {
        Log.i(TAG, "Joined group on server, fetching group state and sending update");

        return fetchGroupStateAndSendUpdate(groupRecipient, decryptedGroup, decryptedChange, signedGroupChange);
      }
    }

    private GroupManager.GroupActionResult fetchGroupStateAndSendUpdate(@NonNull Recipient groupRecipient,
                                                                        @NonNull DecryptedGroup decryptedGroup,
                                                                        @NonNull DecryptedGroupChange decryptedChange,
                                                                        @NonNull GroupChange signedGroupChange)
        throws GroupChangeFailedException, IOException
    {
      try {
        GroupsV2StateProcessor.forGroup(serviceIds, groupMasterKey)
                              .updateLocalGroupToRevision(decryptedChange.revision,
                                                          System.currentTimeMillis(),
                                                          decryptedChange);

        RecipientAndThread recipientAndThread = sendGroupUpdateHelper.sendGroupUpdate(groupMasterKey, new GroupMutation(null, decryptedChange, decryptedGroup), signedGroupChange);

        return new GroupManager.GroupActionResult(groupRecipient,
                                                  recipientAndThread.threadId,
                                                  1,
                                                  Collections.emptyList());
      } catch (GroupNotAMemberException e) {
        Log.w(TAG, "Despite adding self to group, server says we are not a member, scheduling refresh of group info " + groupId, e);

        AppDependencies.getJobManager()
                       .add(new RequestGroupV2InfoJob(groupId));

        throw new GroupChangeFailedException(e);
      } catch (IOException e) {
        Log.w(TAG, "Group data fetch failed, scheduling refresh of group info " + groupId, e);

        AppDependencies.getJobManager()
                       .add(new RequestGroupV2InfoJob(groupId));

        throw e;
      }
    }

    private @NonNull DecryptedGroupChange decryptChange(@NonNull GroupChange signedGroupChange)
        throws GroupChangeFailedException
    {
      try {
        //noinspection OptionalGetWithoutIsPresent
        return groupOperations.decryptChange(signedGroupChange, DecryptChangeVerificationMode.alreadyTrusted()).get();
      } catch (VerificationFailedException | InvalidGroupStateException | IOException e) {
        Log.w(TAG, e);
        throw new GroupChangeFailedException(e);
      }
    }

    /**
     * Creates a local group from what we know before joining.
     * <p>
     * Creates as a placeholder group so that we know not do do a full diff against this group once we learn more about this
     * group as that would create a large update message.
     */
    private DecryptedGroup createPlaceholderGroup(@NonNull DecryptedGroupJoinInfo joinInfo, boolean requestToJoin) {
      DecryptedGroup.Builder group = new DecryptedGroup.Builder()
                                                       .title(joinInfo.title)
                                                       .avatar(joinInfo.avatar)
                                                       .description(joinInfo.description)
                                                       .revision(joinInfo.revision)
                                                       .isPlaceholderGroup(true);

      Recipient  self         = Recipient.self();
      ByteString selfAciBytes = selfAci.toByteString();
      ByteString profileKey   = ByteString.of(Objects.requireNonNull(self.getProfileKey()));

      if (requestToJoin) {
        group.requestingMembers(Collections.singletonList(new DecryptedRequestingMember.Builder()
                                                                                       .aciBytes(selfAciBytes)
                                                                                       .profileKey(profileKey)
                                                                                       .build()));
      } else {
        group.members(Collections.singletonList(new DecryptedMember.Builder()
                                                                   .aciBytes(selfAciBytes)
                                                                   .profileKey(profileKey)
                                                                   .build()));
      }

      return group.build();
    }

    private @NonNull GroupChangeResponse joinGroupOnServer(boolean requestToJoin, int currentRevision)
        throws GroupChangeFailedException, IOException, MembershipNotSuitableForV2Exception, GroupLinkNotActiveException, GroupJoinAlreadyAMemberException
    {
      if (!GroupsV2CapabilityChecker.allAndSelfHaveServiceId(Collections.singleton(Recipient.self().getId()))) {
        throw new MembershipNotSuitableForV2Exception("Self does not support GV2 or UUID capabilities");
      }

      GroupCandidate self = groupCandidateHelper.recipientIdToCandidate(Recipient.self().getId());

      if (!self.hasValidProfileKeyCredential()) {
        Log.w(TAG, "[joinGroupOnServer] No profile credential for self, repairing");
        ProfileUtil.uploadProfile(context);
        self = groupCandidateHelper.recipientIdToCandidate(Recipient.self().getId());

        if (!self.hasValidProfileKeyCredential()) {
          Log.w(TAG, "[joinGroupOnServer] Still unable to get credential");
          AppDependencies.getJobManager().add(new ProfileUploadJob());
          throw new MembershipNotSuitableForV2Exception("Unable to join group, no profile key credential for self");
        }
      }

      ExpiringProfileKeyCredential expiringProfileKeyCredential = self.requireExpiringProfileKeyCredential();

      GroupChange.Actions.Builder change = requestToJoin ? groupOperations.createGroupJoinRequest(expiringProfileKeyCredential)
                                                         : groupOperations.createGroupJoinDirect(expiringProfileKeyCredential);

      change.sourceServiceId(selfAci.toByteString());

      return commitJoinChangeWithConflictResolution(currentRevision, change);
    }

    private @NonNull GroupChangeResponse commitJoinChangeWithConflictResolution(int currentRevision, @NonNull GroupChange.Actions.Builder change)
        throws GroupChangeFailedException, IOException, GroupLinkNotActiveException, GroupJoinAlreadyAMemberException
    {
      for (int attempt = 0; attempt < 5; attempt++) {
        try {
          GroupChange.Actions changeActions = change.revision(currentRevision + 1)
                                                    .build();

          Log.i(TAG, "Trying to join group at V" + changeActions.revision);
          GroupChangeResponse changeResponse = commitJoinToServer(changeActions);

          Log.i(TAG, "Successfully joined group at V" + changeActions.revision);
          return changeResponse;
        } catch (GroupPatchNotAcceptedException e) {
          Log.w(TAG, "Patch not accepted", e);

          try {
            if (alreadyPendingAdminApproval() || testGroupMembership()) {
              throw new GroupJoinAlreadyAMemberException(e);
            } else {
              throw new GroupChangeFailedException(e);
            }
          } catch (VerificationFailedException | InvalidGroupStateException | InvalidInputException ex) {
            throw new GroupChangeFailedException(ex);
          }
        } catch (ConflictException e) {
          Log.w(TAG, "Revision conflict", e);

          currentRevision = getCurrentGroupRevisionFromServer();
        }
      }

      throw new GroupChangeFailedException("Unable to join group after conflicts");
    }

    private @NonNull GroupChangeResponse commitJoinToServer(@NonNull GroupChange.Actions change)
      throws GroupChangeFailedException, IOException, GroupLinkNotActiveException
    {
      try {
        return groupsV2Api.patchGroup(change, authorization.getAuthorizationForToday(serviceIds, groupSecretParams), Optional.ofNullable(password).map(GroupLinkPassword::serialize));
      } catch (NotInGroupException | VerificationFailedException e) {
        Log.w(TAG, e);
        throw new GroupChangeFailedException(e);
      } catch (AuthorizationFailedException e) {
        Log.w(TAG, e);
        throw new GroupLinkNotActiveException(e, Optional.empty());
      }
    }

    private int getCurrentGroupRevisionFromServer()
        throws IOException, GroupLinkNotActiveException, GroupChangeFailedException
    {
      try {
        int currentRevision = getGroupJoinInfoFromServer(groupMasterKey, password).revision;

        Log.i(TAG, "Server now on V" + currentRevision);

        return currentRevision;
      } catch (VerificationFailedException ex) {
        throw new GroupChangeFailedException(ex);
      }
    }

    private boolean alreadyPendingAdminApproval()
        throws IOException, GroupLinkNotActiveException, GroupChangeFailedException
    {
      try {
        boolean pendingAdminApproval = getGroupJoinInfoFromServer(groupMasterKey, password).pendingAdminApproval;

        if (pendingAdminApproval) {
          Log.i(TAG, "User is already pending admin approval");
        }

        return pendingAdminApproval;
      } catch (VerificationFailedException ex) {
        throw new GroupChangeFailedException(ex);
      }
    }

    private boolean testGroupMembership()
        throws IOException, VerificationFailedException, InvalidGroupStateException, InvalidInputException
    {
      try {
        groupsV2Api.getGroup(groupSecretParams, authorization.getAuthorizationForToday(serviceIds, groupSecretParams));
        return true;
      } catch (NotInGroupException ex) {
        return false;
      }
    }

    @WorkerThread
    void cancelJoinRequest()
        throws GroupChangeFailedException, IOException
    {
      Set<ACI> uuids = Collections.singleton(selfAci);

      GroupChange signedGroupChange;
      try {
        signedGroupChange = commitCancelChangeWithConflictResolution(groupOperations.createRefuseGroupJoinRequest(uuids, false, Collections.emptyList()));
      } catch (GroupLinkNotActiveException e) {
        Log.d(TAG, "Unexpected unable to leave group due to group link off");
        throw new GroupChangeFailedException(e);
      }

      DecryptedGroup decryptedGroup = groupDatabase.requireGroup(groupId).requireV2GroupProperties().getDecryptedGroup();

      try {
        //noinspection OptionalGetWithoutIsPresent
        DecryptedGroupChange decryptedChange = groupOperations.decryptChange(signedGroupChange, DecryptChangeVerificationMode.alreadyTrusted()).get();
        DecryptedGroup       newGroup        = DecryptedGroupUtil.applyWithoutRevisionCheck(decryptedGroup, decryptedChange);

        groupDatabase.update(groupId, resetRevision(newGroup, decryptedGroup.revision), null);

        sendGroupUpdateHelper.sendGroupUpdate(groupMasterKey, new GroupMutation(decryptedGroup, decryptedChange, newGroup), signedGroupChange, false);
      } catch (VerificationFailedException | InvalidGroupStateException | NotAbleToApplyGroupV2ChangeException e) {
        throw new GroupChangeFailedException(e);
      }
    }

    private DecryptedGroup resetRevision(DecryptedGroup newGroup, int revision) {
      return newGroup.newBuilder()
                     .revision(revision)
                     .build();
    }

    private @NonNull GroupChange commitCancelChangeWithConflictResolution(@NonNull GroupChange.Actions.Builder change)
        throws GroupChangeFailedException, IOException, GroupLinkNotActiveException
    {
      int currentRevision = getCurrentGroupRevisionFromServer();

      for (int attempt = 0; attempt < 5; attempt++) {
        try {
          GroupChange.Actions changeActions = change.revision(currentRevision + 1)
                                                    .build();

          Log.i(TAG, "Trying to cancel request group at V" + changeActions.revision);
          GroupChange signedGroupChange = commitJoinToServer(changeActions).groupChange;

          Log.i(TAG, "Successfully cancelled group join at V" + changeActions.revision);
          return signedGroupChange;
        } catch (GroupPatchNotAcceptedException e) {
          throw new GroupChangeFailedException(e);
        } catch (ConflictException e) {
          Log.w(TAG, "Revision conflict", e);

          currentRevision = getCurrentGroupRevisionFromServer();
        }
      }

      throw new GroupChangeFailedException("Unable to cancel group join request after conflicts");
    }
}

  private abstract static class LockOwner implements Closeable {
    final Closeable lock;

    LockOwner(@NonNull Closeable lock) {
      this.lock = lock;
    }

    @Override
    public void close() throws IOException {
      lock.close();
    }
  }

  @VisibleForTesting
  static class SendGroupUpdateHelper {

    private final Context context;

    SendGroupUpdateHelper(Context context) {
      this.context = context;
    }

    @NonNull RecipientAndThread sendGroupUpdate(@NonNull GroupMasterKey masterKey,
                                                        @NonNull GroupMutation groupMutation,
                                                        @Nullable GroupChange signedGroupChange)
    {
      return sendGroupUpdate(masterKey, groupMutation, signedGroupChange, true);
    }

    @NonNull RecipientAndThread sendGroupUpdate(@NonNull GroupMasterKey masterKey,
                                                        @NonNull GroupMutation groupMutation,
                                                        @Nullable GroupChange signedGroupChange,
                                                        boolean sendToMembers)
    {
      GroupId.V2           groupId           = GroupId.v2(masterKey);
      Recipient            groupRecipient    = Recipient.externalGroupExact(groupId);
      GV2UpdateDescription updateDescription = GroupProtoUtil.createOutgoingGroupV2UpdateDescription(masterKey, groupMutation, signedGroupChange);
      OutgoingMessage      outgoingMessage   = OutgoingMessage.groupUpdateMessage(groupRecipient, updateDescription, System.currentTimeMillis(), false);


      DecryptedGroupChange plainGroupChange = groupMutation.getGroupChange();

      if (plainGroupChange != null && DecryptedGroupUtil.changeIsSilent(plainGroupChange)) {
        if (sendToMembers) {
          AppDependencies.getJobManager().add(PushGroupSilentUpdateSendJob.create(context, groupId, groupMutation.getNewGroupState(), outgoingMessage));
        }

        return new RecipientAndThread(groupRecipient, -1);
      } else {
        //noinspection IfStatementWithIdenticalBranches
        if (sendToMembers) {
          long threadId = MessageSender.send(context, outgoingMessage, -1, MessageSender.SendType.SIGNAL, null, null);
          return new RecipientAndThread(groupRecipient, threadId);
        } else {
          long threadId = SignalDatabase.threads().getOrCreateValidThreadId(outgoingMessage.getThreadRecipient(), -1, outgoingMessage.getDistributionType());
          try {
            long messageId = SignalDatabase.messages().insertMessageOutbox(outgoingMessage, threadId, false, null).getMessageId();
            SignalDatabase.messages().markAsSent(messageId, true);
            SignalDatabase.threads().update(threadId, true, true);
          } catch (MmsException e) {
            throw new AssertionError(e);
          }
          return new RecipientAndThread(groupRecipient, threadId);
        }
      }
    }
  }

  private static @NonNull List<RecipientId> getPendingMemberRecipientIds(@NonNull List<DecryptedPendingMember> newPendingMembersList) {
    return DecryptedGroupUtil.pendingToServiceIdList(newPendingMembersList)
                             .stream()
                             .map(RecipientId::from)
                             .collect(Collectors.toList());
  }

  private static @NonNull AccessControl.AccessRequired rightsToAccessControl(@NonNull GroupAccessControl rights) {
    switch (rights){
      case ALL_MEMBERS:
        return AccessControl.AccessRequired.MEMBER;
      case ONLY_ADMINS:
        return AccessControl.AccessRequired.ADMINISTRATOR;
      case NO_ONE:
        return AccessControl.AccessRequired.UNSATISFIABLE;
      default:
      throw new AssertionError();
    }
  }

  static class RecipientAndThread {
    private final Recipient groupRecipient;
    private final long      threadId;

    RecipientAndThread(@NonNull Recipient groupRecipient, long threadId) {
      this.groupRecipient = groupRecipient;
      this.threadId       = threadId;
    }
  }
}

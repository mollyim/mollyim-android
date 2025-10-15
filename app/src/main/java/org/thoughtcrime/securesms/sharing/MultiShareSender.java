package org.thoughtcrime.securesms.sharing;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import com.annimon.stream.Stream;

import org.signal.core.util.BreakIteratorCompat;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.UriAttachment;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.conversation.MessageSendType;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.keyvalue.StorySend;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mediasend.v2.text.TextStoryBackgroundColors;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.OutgoingMessage;
import org.thoughtcrime.securesms.mms.SentMediaQuality;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.SlideFactory;
import org.thoughtcrime.securesms.mms.StickerSlide;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.MessageSender.SendType;
import org.thoughtcrime.securesms.stories.Stories;
import org.signal.core.util.Base64;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.MessageUtil;
import org.thoughtcrime.securesms.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import okio.Utf8;

/**
 * MultiShareSender encapsulates send logic (stolen from {@link org.thoughtcrime.securesms.conversation.ConversationActivity}
 * and provides a means to:
 * <p>
 * 1. Send messages based off a {@link MultiShareArgs} object and
 * 1. Parse through the result of the send via a {@link MultiShareSendResultCollection}
 */
public final class MultiShareSender {

  private static final String TAG = Log.tag(MultiShareSender.class);

  private MultiShareSender() {
  }

  @MainThread
  public static void send(@NonNull MultiShareArgs multiShareArgs, @NonNull Consumer<MultiShareSendResultCollection> results) {
    SimpleTask.run(() -> sendSync(multiShareArgs), results::accept);
  }

  @WorkerThread
  public static MultiShareSendResultCollection sendSync(@NonNull MultiShareArgs multiShareArgs) {
    List<MultiShareSendResult> results                           = new ArrayList<>(multiShareArgs.getContactSearchKeys().size());
    Context                    context                           = AppDependencies.getApplication();
    String                     message                           = multiShareArgs.getDraftText();
    SlideDeck                  primarySlideDeck;
    List<OutgoingMessage>      storiesBatch                      = new LinkedList<>();
    ChatColors                 generatedTextStoryBackgroundColor = TextStoryBackgroundColors.getRandomBackgroundColor();

    try {
      primarySlideDeck = buildSlideDeck(context, multiShareArgs);
    } catch (SlideNotFoundException e) {
      Log.w(TAG, "Could not create slide for media message");
      for (ContactSearchKey.RecipientSearchKey recipientSearchKey : multiShareArgs.getRecipientSearchKeys()) {
        results.add(new MultiShareSendResult(recipientSearchKey, MultiShareSendResult.Type.GENERIC_ERROR));
      }

      return new MultiShareSendResultCollection(results);
    }

    DistributionListMultiShareTimestampProvider distributionListSentTimestamps = DistributionListMultiShareTimestampProvider.create();
    for (ContactSearchKey.RecipientSearchKey recipientSearchKey : multiShareArgs.getRecipientSearchKeys()) {
      Recipient recipient = Recipient.resolved(recipientSearchKey.getRecipientId());

      long            threadId           = SignalDatabase.threads().getOrCreateThreadIdFor(recipient);
      List<Mention>   mentions           = getValidMentionsForRecipient(recipient, multiShareArgs.getMentions());
      MessageSendType sendType           = MessageSendType.SignalMessageSendType.INSTANCE;
      long            expiresIn          = TimeUnit.SECONDS.toMillis(recipient.getExpiresInSeconds());
      int             expireTimerVersion = recipient.getExpireTimerVersion();
      List<Contact>   contacts           = multiShareArgs.getSharedContacts();
      SlideDeck       slideDeck          = new SlideDeck(primarySlideDeck);

      boolean needsSplit = message != null && Utf8.size(message) > MessageUtil.MAX_INLINE_BODY_SIZE_BYTES;
      boolean hasMmsMedia = !multiShareArgs.getMedia().isEmpty() ||
                            (multiShareArgs.getDataUri() != null && multiShareArgs.getDataUri() != Uri.EMPTY) ||
                            multiShareArgs.getStickerLocator() != null ||
                            recipient.isGroup() ||
                            recipient.getEmail().isPresent();
      boolean hasPushMedia = hasMmsMedia ||
                             multiShareArgs.getLinkPreview() != null ||
                             !mentions.isEmpty() ||
                             needsSplit ||
                             !contacts.isEmpty();

      MultiShareTimestampProvider sentTimestamp      = recipient.isDistributionList() ? distributionListSentTimestamps : MultiShareTimestampProvider.create();
      boolean                     canSendAsTextStory = recipientSearchKey.isStory() && multiShareArgs.isValidForTextStoryGeneration();

      if ((recipient.isMmsGroup() || recipient.getEmail().isPresent())) {
        results.add(new MultiShareSendResult(recipientSearchKey, MultiShareSendResult.Type.MMS_NOT_ENABLED));
      } else if (hasPushMedia || canSendAsTextStory) {
        sendMediaMessageOrCollectStoryToBatch(context,
                                              multiShareArgs,
                                              recipient,
                                              slideDeck,
                                              sendType,
                                              threadId,
                                              expiresIn,
                                              expireTimerVersion,
                                              multiShareArgs.isViewOnce(),
                                              mentions,
                                              recipientSearchKey.isStory(),
                                              sentTimestamp,
                                              canSendAsTextStory,
                                              storiesBatch,
                                              generatedTextStoryBackgroundColor,
                                              contacts);
        results.add(new MultiShareSendResult(recipientSearchKey, MultiShareSendResult.Type.SUCCESS));
      } else if (recipientSearchKey.isStory()) {
        results.add(new MultiShareSendResult(recipientSearchKey, MultiShareSendResult.Type.INVALID_SHARE_TO_STORY));
      } else {
        sendTextMessage(context, multiShareArgs, recipient, threadId, expiresIn);
        results.add(new MultiShareSendResult(recipientSearchKey, MultiShareSendResult.Type.SUCCESS));
      }

      if (!recipientSearchKey.isStory()) {
        SignalDatabase.threads().setRead(threadId);
      }

      // XXX We must do this to avoid sending out messages to the same recipient with the same
      //     sentTimestamp. If we do this, they'll be considered dupes by the receiver.
      ThreadUtil.sleep(5);
    }

    if (!storiesBatch.isEmpty()) {
      MessageSender.sendStories(context,
                                storiesBatch.stream()
                                            .map(OutgoingMessage::makeSecure)
                                            .collect(Collectors.toList()),
                                null,
                                null);
    }

    return new MultiShareSendResultCollection(results);
  }

  private static void sendMediaMessageOrCollectStoryToBatch(@NonNull Context context,
                                                            @NonNull MultiShareArgs multiShareArgs,
                                                            @NonNull Recipient recipient,
                                                            @NonNull SlideDeck slideDeck,
                                                            @NonNull MessageSendType sendType,
                                                            long threadId,
                                                            long expiresIn,
                                                            int expireTimerVersion,
                                                            boolean isViewOnce,
                                                            @NonNull List<Mention> validatedMentions,
                                                            boolean isStory,
                                                            @NonNull MultiShareTimestampProvider sentTimestamps,
                                                            boolean canSendAsTextStory,
                                                            @NonNull List<OutgoingMessage> storiesToBatchSend,
                                                            @NonNull ChatColors generatedTextStoryBackgroundColor,
                                                            @NonNull List<Contact> contacts)
  {
    String body = multiShareArgs.getDraftText();
    if (sendType.usesSignalTransport() && body != null) {
      MessageUtil.SplitResult splitMessage = MessageUtil.getSplitMessage(context, body);
      body = splitMessage.getBody();

      if (splitMessage.getTextSlide().isPresent()) {
        slideDeck.addSlide(splitMessage.getTextSlide().get());
      }
    }

    List<OutgoingMessage> outgoingMessages = new ArrayList<>();

    if (isStory) {
      final StoryType storyType;
      if (recipient.isDistributionList()) {
        storyType = SignalDatabase.distributionLists().getStoryType(recipient.requireDistributionListId());
      } else {
        storyType = StoryType.STORY_WITH_REPLIES;
      }

      if (!recipient.isMyStory()) {
        SignalStore.story().setLatestStorySend(StorySend.newSend(recipient));
      }

      if (multiShareArgs.isTextStory()) {
        OutgoingMessage outgoingMessage = new OutgoingMessage(recipient,
                                                              new SlideDeck(),
                                                              body,
                                                              sentTimestamps.getMillis(0),
                                                              0L,
                                                              1,
                                                              false,
                                                              storyType.toTextStoryType(),
                                                              buildLinkPreviews(context, multiShareArgs.getLinkPreview()),
                                                              Collections.emptyList(),
                                                              false,
                                                              multiShareArgs.getBodyRanges(),
                                                              contacts);

        outgoingMessages.add(outgoingMessage);
      } else if (canSendAsTextStory) {
        outgoingMessages.add(generateTextStory(context, recipient, multiShareArgs, sentTimestamps.getMillis(0), storyType, generatedTextStoryBackgroundColor));
      } else {
        List<Slide> storySupportedSlides = slideDeck.getSlides()
                                                    .stream()
                                                    .flatMap(slide -> {
                                                      if (slide instanceof VideoSlide) {
                                                        return expandToClips(context, (VideoSlide) slide).stream();
                                                      } else if (slide instanceof ImageSlide) {
                                                        return java.util.stream.Stream.of(ensureDefaultQuality(context, (ImageSlide) slide));
                                                      } else if (slide instanceof StickerSlide) {
                                                        return java.util.stream.Stream.empty();
                                                      } else {
                                                        return java.util.stream.Stream.of(slide);
                                                      }
                                                    })
                                                    .filter(it -> MediaUtil.isStorySupportedType(it.getContentType()))
                                                    .collect(Collectors.toList());

        for (int i = 0; i < storySupportedSlides.size(); i++) {
          Slide     slide         = storySupportedSlides.get(i);
          SlideDeck singletonDeck = new SlideDeck();

          singletonDeck.addSlide(slide);

          OutgoingMessage outgoingMessage = new OutgoingMessage(recipient,
                                                                singletonDeck,
                                                                body,
                                                                sentTimestamps.getMillis(i),
                                                                0L,
                                                                1,
                                                                false,
                                                                storyType,
                                                                Collections.emptyList(),
                                                                validatedMentions,
                                                                false,
                                                                multiShareArgs.getBodyRanges(),
                                                                contacts);

          outgoingMessages.add(outgoingMessage);
        }
      }
    } else {
      OutgoingMessage outgoingMessage = new OutgoingMessage(recipient,
                                                            slideDeck,
                                                            body,
                                                            sentTimestamps.getMillis(0),
                                                            expiresIn,
                                                            expireTimerVersion,
                                                            isViewOnce,
                                                            StoryType.NONE,
                                                            buildLinkPreviews(context, multiShareArgs.getLinkPreview()),
                                                            validatedMentions,
                                                            false,
                                                            multiShareArgs.getBodyRanges(),
                                                            contacts);

      outgoingMessages.add(outgoingMessage);
    }

    if (isStory) {
      storiesToBatchSend.addAll(outgoingMessages);
    } else if (shouldSendAsPush(recipient)) {
      for (final OutgoingMessage outgoingMessage : outgoingMessages) {
        MessageSender.send(context, outgoingMessage.makeSecure(), threadId, SendType.SIGNAL, null, null);
      }
    } else {
      for (final OutgoingMessage outgoingMessage : outgoingMessages) {
        MessageSender.send(context, outgoingMessage, threadId, SendType.MMS, null, null);
      }
    }
  }

  private static Collection<Slide> expandToClips(@NonNull Context context, @NonNull VideoSlide videoSlide) {
    long duration = Stories.MediaTransform.getVideoDuration(Objects.requireNonNull(videoSlide.getUri()));
    if (duration > Stories.MAX_VIDEO_DURATION_MILLIS) {
      return Stories.MediaTransform.clipMediaToStoryDuration(Stories.MediaTransform.videoSlideToMedia(videoSlide, duration))
                                   .stream()
                                   .map(media -> Stories.MediaTransform.mediaToVideoSlide(context, media))
                                   .collect(Collectors.toList());
    } else if (duration == 0L) {
      return Collections.emptyList();
    } else {
      return Collections.singletonList(videoSlide);
    }
  }

  private static List<LinkPreview> buildLinkPreviews(@NonNull Context context, @Nullable LinkPreview linkPreview) {
    if (linkPreview == null) {
      return Collections.emptyList();
    } else {
      return Collections.singletonList(new LinkPreview(
          linkPreview.getUrl(),
          linkPreview.getTitle(),
          linkPreview.getDescription(),
          linkPreview.getDate(),
          linkPreview.getThumbnail().map(thumbnail ->
                                             thumbnail instanceof UriAttachment ? thumbnail
                                                                                : thumbnail.getUri() == null
                                                                                  ? null
                                                                                  : new ImageSlide(context,
                                                                                                   thumbnail.getUri(),
                                                                                                   thumbnail.contentType,
                                                                                                   thumbnail.size,
                                                                                                   thumbnail.width,
                                                                                                   thumbnail.height,
                                                                                                   thumbnail.borderless,
                                                                                                   thumbnail.caption,
                                                                                                   thumbnail.blurHash,
                                                                                                   thumbnail.transformProperties).asAttachment()
          )
      ));
    }
  }

  private static Slide ensureDefaultQuality(@NonNull Context context, @NonNull ImageSlide imageSlide) {
    Attachment attachment = imageSlide.asAttachment();
    final AttachmentTable.TransformProperties transformProperties = attachment.transformProperties;
    if (transformProperties != null && transformProperties.sentMediaQuality == SentMediaQuality.HIGH.getCode()) {
      return new ImageSlide(
          context,
          attachment.getUri(),
          attachment.contentType,
          attachment.size,
          attachment.width,
          attachment.height,
          attachment.borderless,
          attachment.caption,
          attachment.blurHash,
          AttachmentTable.TransformProperties.empty()
      );
    } else {
      return imageSlide;
    }
  }

  private static void sendTextMessage(@NonNull Context context,
                                      @NonNull MultiShareArgs multiShareArgs,
                                      @NonNull Recipient recipient,
                                      long threadId,
                                      long expiresIn)
  {
    String body = multiShareArgs.getDraftText() == null ? "" : multiShareArgs.getDraftText();

    OutgoingMessage outgoingMessage;
    if (shouldSendAsPush(recipient)) {
      outgoingMessage = OutgoingMessage.text(recipient, body, expiresIn, System.currentTimeMillis(), multiShareArgs.getBodyRanges());
    } else {
      outgoingMessage = OutgoingMessage.sms(recipient, body);
    }

    MessageSender.send(context, outgoingMessage, threadId, SendType.SIGNAL, null, null);
  }

  private static @NonNull OutgoingMessage generateTextStory(@NonNull Context context,
                                                            @NonNull Recipient recipient,
                                                            @NonNull MultiShareArgs multiShareArgs,
                                                            long sentTimestamp,
                                                            @NonNull StoryType storyType,
                                                            @NonNull ChatColors background)
  {
    return OutgoingMessage.textStoryMessage(
        recipient,
        Base64.encodeWithPadding(new StoryTextPost.Builder()
                                            .body(getBodyForTextStory(multiShareArgs.getDraftText(), multiShareArgs.getLinkPreview()))
                                            .style(StoryTextPost.Style.DEFAULT)
                                            .background(background.serialize())
                                            .textBackgroundColor(0)
                                            .textForegroundColor(Color.WHITE)
                                            .build()
                                            .encode()),
        sentTimestamp,
        storyType.toTextStoryType(),
        buildLinkPreviews(context, multiShareArgs.getLinkPreview()),
        multiShareArgs.getBodyRanges());
  }

  private static @NonNull String getBodyForTextStory(@Nullable String draftText, @Nullable LinkPreview linkPreview) {
    if (Util.isEmpty(draftText)) {
      return "";
    }

    BreakIteratorCompat breakIteratorCompat = BreakIteratorCompat.getInstance();
    breakIteratorCompat.setText(draftText);

    String trimmed = breakIteratorCompat.take(Stories.MAX_TEXT_STORY_SIZE).toString();
    if (linkPreview == null) {
      return trimmed;
    }

    if (linkPreview.getUrl().equals(trimmed)) {
      return "";
    }

    return trimmed.replace(linkPreview.getUrl(), "").trim();
  }

  private static boolean shouldSendAsPush(@NonNull Recipient recipient) {
    return recipient.isDistributionList() ||
           recipient.isServiceIdOnly() ||
           recipient.isRegistered();
  }

  private static @NonNull SlideDeck buildSlideDeck(@NonNull Context context, @NonNull MultiShareArgs multiShareArgs) throws SlideNotFoundException {
    SlideDeck slideDeck = new SlideDeck();
    if (multiShareArgs.getStickerLocator() != null) {
      slideDeck.addSlide(new StickerSlide(context, multiShareArgs.getDataUri(), 0, multiShareArgs.getStickerLocator(), multiShareArgs.getDataType()));
    } else if (!multiShareArgs.getMedia().isEmpty()) {
      for (Media media : multiShareArgs.getMedia()) {
        Slide slide = SlideFactory.getSlide(context, media.getContentType(), media.getUri(), media.getWidth(), media.getHeight(), media.getTransformProperties());
        if (slide != null) {
          slideDeck.addSlide(slide);
        } else {
          throw new SlideNotFoundException();
        }
      }
    } else if (multiShareArgs.getDataUri() != null) {
      Slide slide = SlideFactory.getSlide(context, multiShareArgs.getDataType(), multiShareArgs.getDataUri(), 0, 0, null);
      if (slide != null) {
        slideDeck.addSlide(slide);
      } else {
        throw new SlideNotFoundException();
      }
    }

    return slideDeck;
  }

  private static @NonNull List<Mention> getValidMentionsForRecipient(@NonNull Recipient recipient, @NonNull List<Mention> mentions) {
    if (mentions.isEmpty() || !recipient.isPushV2Group() || !recipient.isActiveGroup()) {
      return Collections.emptyList();
    } else {
      Set<RecipientId> validRecipientIds = new HashSet<>(recipient.getParticipantIds());

      return mentions.stream()
                     .filter(mention -> validRecipientIds.contains(mention.getRecipientId()))
                     .collect(Collectors.toList());
    }
  }

  public static final class MultiShareSendResultCollection {
    private final List<MultiShareSendResult> results;

    private MultiShareSendResultCollection(List<MultiShareSendResult> results) {
      this.results = results;
    }

    public boolean containsFailures() {
      return Stream.of(results).anyMatch(result -> result.type != MultiShareSendResult.Type.SUCCESS);
    }

    public boolean containsOnlyFailures() {
      return Stream.of(results).allMatch(result -> result.type != MultiShareSendResult.Type.SUCCESS);
    }
  }

  private static final class MultiShareSendResult {
    private final ContactSearchKey.RecipientSearchKey recipientSearchKey;
    private final Type                                type;

    private MultiShareSendResult(ContactSearchKey.RecipientSearchKey contactSearchKey, Type type) {
      this.recipientSearchKey = contactSearchKey;
      this.type               = type;
    }

    public ContactSearchKey.RecipientSearchKey getContactSearchKey() {
      return recipientSearchKey;
    }

    public Type getType() {
      return type;
    }

    private enum Type {
      GENERIC_ERROR,
      INVALID_SHARE_TO_STORY,
      MMS_NOT_ENABLED,
      SUCCESS
    }
  }

  private static final class SlideNotFoundException extends Exception {
  }
}

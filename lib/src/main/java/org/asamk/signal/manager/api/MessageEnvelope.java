package org.asamk.signal.manager.api;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.signal.libsignal.metadata.ProtocolException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;
import org.whispersystems.signalservice.api.messages.SignalServicePreview;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTextAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.OpaqueMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.MessageRequestResponseMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewOnceOpenMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewedMessage;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.BodyRange;

public record MessageEnvelope(
        Optional<RecipientAddress> sourceAddress,
        int sourceDevice,
        long timestamp,
        long serverReceivedTimestamp,
        long serverDeliveredTimestamp,
        boolean isUnidentifiedSender,
        Optional<Receipt> receipt,
        Optional<Typing> typing,
        Optional<Data> data,
        Optional<Sync> sync,
        Optional<Call> call,
        Optional<Story> story
) {

    public record Receipt(long when, Type type, List<Long> timestamps) {

        static Receipt from(final SignalServiceReceiptMessage receiptMessage) {
            return new Receipt(receiptMessage.getWhen(),
                    Type.from(receiptMessage.getType()),
                    receiptMessage.getTimestamps());
        }

        public enum Type {
            DELIVERY,
            READ,
            VIEWED,
            UNKNOWN;

            static Type from(SignalServiceReceiptMessage.Type type) {
                return switch (type) {
                    case DELIVERY -> DELIVERY;
                    case READ -> READ;
                    case VIEWED -> VIEWED;
                    case UNKNOWN -> UNKNOWN;
                };
            }
        }
    }

    public record Typing(long timestamp, Type type, Optional<GroupId> groupId) {

        public static Typing from(final SignalServiceTypingMessage typingMessage) {
            return new Typing(typingMessage.getTimestamp(),
                    typingMessage.isTypingStarted() ? Type.STARTED : Type.STOPPED,
                    typingMessage.getGroupId().map(GroupId::unknownVersion));
        }

        public enum Type {
            STARTED,
            STOPPED,
        }
    }

    public record Data(
            long timestamp,
            Optional<GroupContext> groupContext,
            Optional<StoryContext> storyContext,
            Optional<GroupCallUpdate> groupCallUpdate,
            Optional<String> body,
            int expiresInSeconds,
            boolean isExpirationUpdate,
            boolean isViewOnce,
            boolean isEndSession,
            boolean isProfileKeyUpdate,
            boolean hasProfileKey,
            Optional<Reaction> reaction,
            Optional<Quote> quote,
            Optional<Payment> payment,
            List<Attachment> attachments,
            Optional<Long> remoteDeleteId,
            Optional<Sticker> sticker,
            List<SharedContact> sharedContacts,
            List<Mention> mentions,
            List<Preview> previews,
            List<TextStyle> textStyles
    ) {

        static Data from(
                final SignalServiceDataMessage dataMessage,
                RecipientResolver recipientResolver,
                RecipientAddressResolver addressResolver,
                final AttachmentFileProvider fileProvider
        ) {
            return new Data(dataMessage.getTimestamp(),
                    dataMessage.getGroupContext().map(GroupContext::from),
                    dataMessage.getStoryContext()
                            .map((SignalServiceDataMessage.StoryContext storyContext) -> StoryContext.from(storyContext,
                                    recipientResolver,
                                    addressResolver)),
                    dataMessage.getGroupCallUpdate().map(GroupCallUpdate::from),
                    dataMessage.getBody(),
                    dataMessage.getExpiresInSeconds(),
                    dataMessage.isExpirationUpdate(),
                    dataMessage.isViewOnce(),
                    dataMessage.isEndSession(),
                    dataMessage.isProfileKeyUpdate(),
                    dataMessage.getProfileKey().isPresent(),
                    dataMessage.getReaction().map(r -> Reaction.from(r, recipientResolver, addressResolver)),
                    dataMessage.getQuote().map(q -> Quote.from(q, recipientResolver, addressResolver, fileProvider)),
                    dataMessage.getPayment().map(p -> p.getPaymentNotification().isPresent() ? Payment.from(p) : null),
                    dataMessage.getAttachments()
                            .map(a -> a.stream().map(as -> Attachment.from(as, fileProvider)).toList())
                            .orElse(List.of()),
                    dataMessage.getRemoteDelete().map(SignalServiceDataMessage.RemoteDelete::getTargetSentTimestamp),
                    dataMessage.getSticker().map(Sticker::from),
                    dataMessage.getSharedContacts()
                            .map(a -> a.stream()
                                    .map(sharedContact -> SharedContact.from(sharedContact, fileProvider))
                                    .toList())
                            .orElse(List.of()),
                    dataMessage.getMentions()
                            .map(a -> a.stream().map(m -> Mention.from(m, recipientResolver, addressResolver)).toList())
                            .orElse(List.of()),
                    dataMessage.getPreviews()
                            .map(a -> a.stream().map(preview -> Preview.from(preview, fileProvider)).toList())
                            .orElse(List.of()),
                    dataMessage.getBodyRanges()
                            .map(a -> a.stream().filter(BodyRange::hasStyle).map(TextStyle::from).toList())
                            .orElse(List.of()));
        }

        public record GroupContext(GroupId groupId, boolean isGroupUpdate, int revision) {

            static GroupContext from(SignalServiceGroupContext groupContext) {
                if (groupContext.getGroupV1().isPresent()) {
                    return new GroupContext(GroupId.v1(groupContext.getGroupV1().get().getGroupId()),
                            groupContext.getGroupV1Type() == SignalServiceGroup.Type.UPDATE,
                            0);
                } else if (groupContext.getGroupV2().isPresent()) {
                    final var groupV2 = groupContext.getGroupV2().get();
                    return new GroupContext(GroupUtils.getGroupIdV2(groupV2.getMasterKey()),
                            groupV2.hasSignedGroupChange(),
                            groupV2.getRevision());
                } else {
                    throw new RuntimeException("Invalid group context state");
                }
            }
        }

        public record StoryContext(RecipientAddress author, long sentTimestamp) {

            static StoryContext from(
                    SignalServiceDataMessage.StoryContext storyContext,
                    RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver
            ) {
                return new StoryContext(addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(
                        storyContext.getAuthorServiceId())).toApiRecipientAddress(), storyContext.getSentTimestamp());
            }
        }

        public record GroupCallUpdate(String eraId) {

            static GroupCallUpdate from(SignalServiceDataMessage.GroupCallUpdate groupCallUpdate) {
                return new GroupCallUpdate(groupCallUpdate.getEraId());
            }
        }

        public record Reaction(
                long targetSentTimestamp, RecipientAddress targetAuthor, String emoji, boolean isRemove
        ) {

            static Reaction from(
                    SignalServiceDataMessage.Reaction reaction,
                    RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver
            ) {
                return new Reaction(reaction.getTargetSentTimestamp(),
                        addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(reaction.getTargetAuthor()))
                                .toApiRecipientAddress(),
                        reaction.getEmoji(),
                        reaction.isRemove());
            }
        }

        public record Quote(
                long id,
                RecipientAddress author,
                Optional<String> text,
                List<Mention> mentions,
                List<Attachment> attachments,
                List<TextStyle> textStyles
        ) {

            static Quote from(
                    SignalServiceDataMessage.Quote quote,
                    RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver,
                    final AttachmentFileProvider fileProvider
            ) {
                return new Quote(quote.getId(),
                        addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(quote.getAuthor()))
                                .toApiRecipientAddress(),
                        Optional.ofNullable(quote.getText()),
                        quote.getMentions() == null
                                ? List.of()
                                : quote.getMentions()
                                        .stream()
                                        .map(m -> Mention.from(m, recipientResolver, addressResolver))
                                        .toList(),
                        quote.getAttachments() == null
                                ? List.of()
                                : quote.getAttachments().stream().map(a -> Attachment.from(a, fileProvider)).toList(),
                        quote.getBodyRanges() == null
                                ? List.of()
                                : quote.getBodyRanges()
                                        .stream()
                                        .filter(BodyRange::hasStyle)
                                        .map(TextStyle::from)
                                        .toList());
            }
        }

        public record Payment(String note, byte[] receipt) {

            static Payment from(SignalServiceDataMessage.Payment payment) {
                return new Payment(payment.getPaymentNotification().get().getNote(),
                        payment.getPaymentNotification().get().getReceipt());
            }
        }

        public record Mention(RecipientAddress recipient, int start, int length) {

            static Mention from(
                    SignalServiceDataMessage.Mention mention,
                    RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver
            ) {
                return new Mention(addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(mention.getServiceId()))
                        .toApiRecipientAddress(), mention.getStart(), mention.getLength());
            }
        }

        public record Attachment(
                Optional<String> id,
                Optional<File> file,
                Optional<String> fileName,
                String contentType,
                Optional<Long> uploadTimestamp,
                Optional<Long> size,
                Optional<byte[]> preview,
                Optional<Attachment> thumbnail,
                Optional<String> caption,
                Optional<Integer> width,
                Optional<Integer> height,
                boolean isVoiceNote,
                boolean isGif,
                boolean isBorderless
        ) {

            static Attachment from(SignalServiceAttachment attachment, AttachmentFileProvider fileProvider) {
                if (attachment.isPointer()) {
                    final var a = attachment.asPointer();
                    final var attachmentFile = fileProvider.getFile(a);
                    return new Attachment(Optional.of(attachmentFile.getName()),
                            Optional.of(attachmentFile),
                            a.getFileName(),
                            a.getContentType(),
                            a.getUploadTimestamp() == 0 ? Optional.empty() : Optional.of(a.getUploadTimestamp()),
                            a.getSize().map(Integer::longValue),
                            a.getPreview(),
                            Optional.empty(),
                            a.getCaption().map(c -> c.isEmpty() ? null : c),
                            a.getWidth() == 0 ? Optional.empty() : Optional.of(a.getWidth()),
                            a.getHeight() == 0 ? Optional.empty() : Optional.of(a.getHeight()),
                            a.getVoiceNote(),
                            a.isGif(),
                            a.isBorderless());
                } else {
                    final var a = attachment.asStream();
                    return new Attachment(Optional.empty(),
                            Optional.empty(),
                            a.getFileName(),
                            a.getContentType(),
                            a.getUploadTimestamp() == 0 ? Optional.empty() : Optional.of(a.getUploadTimestamp()),
                            Optional.of(a.getLength()),
                            a.getPreview(),
                            Optional.empty(),
                            a.getCaption(),
                            a.getWidth() == 0 ? Optional.empty() : Optional.of(a.getWidth()),
                            a.getHeight() == 0 ? Optional.empty() : Optional.of(a.getHeight()),
                            a.getVoiceNote(),
                            a.isGif(),
                            a.isBorderless());
                }
            }

            static Attachment from(
                    SignalServiceDataMessage.Quote.QuotedAttachment a, final AttachmentFileProvider fileProvider
            ) {
                return new Attachment(Optional.empty(),
                        Optional.empty(),
                        Optional.ofNullable(a.getFileName()),
                        a.getContentType(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        a.getThumbnail() == null
                                ? Optional.empty()
                                : Optional.of(Attachment.from(a.getThumbnail(), fileProvider)),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        false,
                        false,
                        false);
            }
        }

        public record Sticker(StickerPackId packId, byte[] packKey, int stickerId) {

            static Sticker from(SignalServiceDataMessage.Sticker sticker) {
                return new Sticker(StickerPackId.deserialize(sticker.getPackId()),
                        sticker.getPackKey(),
                        sticker.getStickerId());
            }
        }

        public record SharedContact(
                Name name,
                Optional<Avatar> avatar,
                List<Phone> phone,
                List<Email> email,
                List<Address> address,
                Optional<String> organization
        ) {

            static SharedContact from(
                    org.whispersystems.signalservice.api.messages.shared.SharedContact sharedContact,
                    final AttachmentFileProvider fileProvider
            ) {
                return new SharedContact(Name.from(sharedContact.getName()),
                        sharedContact.getAvatar().map(avatar1 -> Avatar.from(avatar1, fileProvider)),
                        sharedContact.getPhone().map(p -> p.stream().map(Phone::from).toList()).orElse(List.of()),
                        sharedContact.getEmail().map(p -> p.stream().map(Email::from).toList()).orElse(List.of()),
                        sharedContact.getAddress().map(p -> p.stream().map(Address::from).toList()).orElse(List.of()),
                        sharedContact.getOrganization());
            }

            public record Name(
                    Optional<String> display,
                    Optional<String> given,
                    Optional<String> family,
                    Optional<String> prefix,
                    Optional<String> suffix,
                    Optional<String> middle
            ) {

                static Name from(org.whispersystems.signalservice.api.messages.shared.SharedContact.Name name) {
                    return new Name(name.getDisplay(),
                            name.getGiven(),
                            name.getFamily(),
                            name.getPrefix(),
                            name.getSuffix(),
                            name.getMiddle());
                }
            }

            public record Avatar(Attachment attachment, boolean isProfile) {

                static Avatar from(
                        org.whispersystems.signalservice.api.messages.shared.SharedContact.Avatar avatar,
                        final AttachmentFileProvider fileProvider
                ) {
                    return new Avatar(Attachment.from(avatar.getAttachment(), fileProvider), avatar.isProfile());
                }
            }

            public record Phone(
                    String value, Type type, Optional<String> label
            ) {

                static Phone from(org.whispersystems.signalservice.api.messages.shared.SharedContact.Phone phone) {
                    return new Phone(phone.getValue(), Type.from(phone.getType()), phone.getLabel());
                }

                public enum Type {
                    HOME,
                    WORK,
                    MOBILE,
                    CUSTOM;

                    static Type from(org.whispersystems.signalservice.api.messages.shared.SharedContact.Phone.Type type) {
                        return switch (type) {
                            case HOME -> HOME;
                            case WORK -> WORK;
                            case MOBILE -> MOBILE;
                            case CUSTOM -> CUSTOM;
                        };
                    }
                }
            }

            public record Email(
                    String value, Type type, Optional<String> label
            ) {

                static Email from(org.whispersystems.signalservice.api.messages.shared.SharedContact.Email email) {
                    return new Email(email.getValue(), Type.from(email.getType()), email.getLabel());
                }

                public enum Type {
                    HOME,
                    WORK,
                    MOBILE,
                    CUSTOM;

                    static Type from(org.whispersystems.signalservice.api.messages.shared.SharedContact.Email.Type type) {
                        return switch (type) {
                            case HOME -> HOME;
                            case WORK -> WORK;
                            case MOBILE -> MOBILE;
                            case CUSTOM -> CUSTOM;
                        };
                    }
                }
            }

            public record Address(
                    Type type,
                    Optional<String> label,
                    Optional<String> street,
                    Optional<String> pobox,
                    Optional<String> neighborhood,
                    Optional<String> city,
                    Optional<String> region,
                    Optional<String> postcode,
                    Optional<String> country
            ) {

                static Address from(org.whispersystems.signalservice.api.messages.shared.SharedContact.PostalAddress address) {
                    return new Address(Address.Type.from(address.getType()),
                            address.getLabel(),
                            address.getLabel(),
                            address.getLabel(),
                            address.getLabel(),
                            address.getLabel(),
                            address.getLabel(),
                            address.getLabel(),
                            address.getLabel());
                }

                public enum Type {
                    HOME,
                    WORK,
                    CUSTOM;

                    static Type from(org.whispersystems.signalservice.api.messages.shared.SharedContact.PostalAddress.Type type) {
                        return switch (type) {
                            case HOME -> HOME;
                            case WORK -> WORK;
                            case CUSTOM -> CUSTOM;
                        };
                    }
                }
            }
        }

        public record Preview(String title, String description, long date, String url, Optional<Attachment> image) {

            static Preview from(
                    SignalServicePreview preview, final AttachmentFileProvider fileProvider
            ) {
                return new Preview(preview.getTitle(),
                        preview.getDescription(),
                        preview.getDate(),
                        preview.getUrl(),
                        preview.getImage().map(as -> Attachment.from(as, fileProvider)));
            }
        }

        public record TextStyle(Style style, int start, int length) {

            public enum Style {
                NONE,
                BOLD,
                ITALIC,
                SPOILER,
                STRIKETHROUGH,
                MONOSPACE;

                static Style from(BodyRange.Style style) {
                    return switch (style) {
                        case NONE -> NONE;
                        case BOLD -> BOLD;
                        case ITALIC -> ITALIC;
                        case SPOILER -> SPOILER;
                        case STRIKETHROUGH -> STRIKETHROUGH;
                        case MONOSPACE -> MONOSPACE;
                    };
                }
            }

            static TextStyle from(BodyRange bodyRange) {
                return new TextStyle(Style.from(bodyRange.getStyle()), bodyRange.getStart(), bodyRange.getLength());
            }
        }
    }

    public record Sync(
            Optional<Sent> sent,
            Optional<Blocked> blocked,
            List<Read> read,
            List<Viewed> viewed,
            Optional<ViewOnceOpen> viewOnceOpen,
            Optional<Contacts> contacts,
            Optional<Groups> groups,
            Optional<MessageRequestResponse> messageRequestResponse
    ) {

        public static Sync from(
                final SignalServiceSyncMessage syncMessage,
                RecipientResolver recipientResolver,
                RecipientAddressResolver addressResolver,
                final AttachmentFileProvider fileProvider
        ) {
            return new Sync(syncMessage.getSent()
                    .map(s -> Sent.from(s, recipientResolver, addressResolver, fileProvider)),
                    syncMessage.getBlockedList().map(b -> Blocked.from(b, recipientResolver, addressResolver)),
                    syncMessage.getRead()
                            .map(r -> r.stream().map(rm -> Read.from(rm, recipientResolver, addressResolver)).toList())
                            .orElse(List.of()),
                    syncMessage.getViewed()
                            .map(r -> r.stream()
                                    .map(rm -> Viewed.from(rm, recipientResolver, addressResolver))
                                    .toList())
                            .orElse(List.of()),
                    syncMessage.getViewOnceOpen().map(rm -> ViewOnceOpen.from(rm, recipientResolver, addressResolver)),
                    syncMessage.getContacts().map(Contacts::from),
                    syncMessage.getGroups().map(Groups::from),
                    syncMessage.getMessageRequestResponse()
                            .map(m -> MessageRequestResponse.from(m, recipientResolver, addressResolver)));
        }

        public record Sent(
                long timestamp,
                long expirationStartTimestamp,
                Optional<RecipientAddress> destination,
                Set<RecipientAddress> recipients,
                Optional<Data> message,
                Optional<Story> story
        ) {

            static Sent from(
                    SentTranscriptMessage sentMessage,
                    RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver,
                    final AttachmentFileProvider fileProvider
            ) {
                return new Sent(sentMessage.getTimestamp(),
                        sentMessage.getExpirationStartTimestamp(),
                        sentMessage.getDestination()
                                .map(d -> addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(d))
                                        .toApiRecipientAddress()),
                        sentMessage.getRecipients()
                                .stream()
                                .map(d -> addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(d))
                                        .toApiRecipientAddress())
                                .collect(Collectors.toSet()),
                        sentMessage.getDataMessage()
                                .map(message -> Data.from(message, recipientResolver, addressResolver, fileProvider)),
                        sentMessage.getStoryMessage().map(s -> Story.from(s, fileProvider)));
            }
        }

        public record Blocked(List<RecipientAddress> recipients, List<GroupId> groupIds) {

            static Blocked from(
                    BlockedListMessage blockedListMessage,
                    RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver
            ) {
                return new Blocked(blockedListMessage.getAddresses()
                        .stream()
                        .map(d -> addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(d))
                                .toApiRecipientAddress())
                        .toList(), blockedListMessage.getGroupIds().stream().map(GroupId::unknownVersion).toList());
            }
        }

        public record Read(RecipientAddress sender, long timestamp) {

            static Read from(
                    ReadMessage readMessage,
                    RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver
            ) {
                return new Read(addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(readMessage.getSender()))
                        .toApiRecipientAddress(), readMessage.getTimestamp());
            }
        }

        public record Viewed(RecipientAddress sender, long timestamp) {

            static Viewed from(
                    ViewedMessage readMessage,
                    RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver
            ) {
                return new Viewed(addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(readMessage.getSender()))
                        .toApiRecipientAddress(), readMessage.getTimestamp());
            }
        }

        public record ViewOnceOpen(RecipientAddress sender, long timestamp) {

            static ViewOnceOpen from(
                    ViewOnceOpenMessage readMessage,
                    RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver
            ) {
                return new ViewOnceOpen(addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(
                        readMessage.getSender())).toApiRecipientAddress(), readMessage.getTimestamp());
            }
        }

        public record Contacts(boolean isComplete) {

            static Contacts from(ContactsMessage contactsMessage) {
                return new Contacts(contactsMessage.isComplete());
            }
        }

        public record Groups() {

            static Groups from(SignalServiceAttachment groupsMessage) {
                return new Groups();
            }
        }

        public record MessageRequestResponse(Type type, Optional<GroupId> groupId, Optional<RecipientAddress> person) {

            static MessageRequestResponse from(
                    MessageRequestResponseMessage messageRequestResponse,
                    RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver
            ) {
                return new MessageRequestResponse(Type.from(messageRequestResponse.getType()),
                        messageRequestResponse.getGroupId().map(GroupId::unknownVersion),
                        messageRequestResponse.getPerson()
                                .map(p -> addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(p))
                                        .toApiRecipientAddress()));
            }

            public enum Type {
                UNKNOWN,
                ACCEPT,
                DELETE,
                BLOCK,
                BLOCK_AND_DELETE,
                UNBLOCK_AND_ACCEPT;

                static Type from(MessageRequestResponseMessage.Type type) {
                    return switch (type) {
                        case UNKNOWN -> UNKNOWN;
                        case ACCEPT -> ACCEPT;
                        case DELETE -> DELETE;
                        case BLOCK -> BLOCK;
                        case BLOCK_AND_DELETE -> BLOCK_AND_DELETE;
                        case UNBLOCK_AND_ACCEPT -> UNBLOCK_AND_ACCEPT;
                    };
                }
            }
        }
    }

    public record Call(
            Optional<Integer> destinationDeviceId,
            Optional<GroupId> groupId,
            Optional<Long> timestamp,
            Optional<Offer> offer,
            Optional<Answer> answer,
            Optional<Hangup> hangup,
            Optional<Busy> busy,
            List<IceUpdate> iceUpdate,
            Optional<Opaque> opaque
    ) {

        public static Call from(final SignalServiceCallMessage callMessage) {
            return new Call(callMessage.getDestinationDeviceId(),
                    callMessage.getGroupId().map(GroupId::unknownVersion),
                    callMessage.getTimestamp(),
                    callMessage.getOfferMessage().map(Offer::from),
                    callMessage.getAnswerMessage().map(Answer::from),
                    callMessage.getHangupMessage().map(Hangup::from),
                    callMessage.getBusyMessage().map(Busy::from),
                    callMessage.getIceUpdateMessages()
                            .map(m -> m.stream().map(IceUpdate::from).toList())
                            .orElse(List.of()),
                    callMessage.getOpaqueMessage().map(Opaque::from));
        }

        public record Offer(long id, String sdp, Type type, byte[] opaque) {

            static Offer from(OfferMessage offerMessage) {
                return new Offer(offerMessage.getId(),
                        offerMessage.getSdp(),
                        Type.from(offerMessage.getType()),
                        offerMessage.getOpaque());
            }

            public enum Type {
                AUDIO_CALL,
                VIDEO_CALL;

                static Type from(OfferMessage.Type type) {
                    return switch (type) {
                        case AUDIO_CALL -> AUDIO_CALL;
                        case VIDEO_CALL -> VIDEO_CALL;
                    };
                }
            }
        }

        public record Answer(long id, String sdp, byte[] opaque) {

            static Answer from(AnswerMessage answerMessage) {
                return new Answer(answerMessage.getId(), answerMessage.getSdp(), answerMessage.getOpaque());
            }
        }

        public record Busy(long id) {

            static Busy from(BusyMessage busyMessage) {
                return new Busy(busyMessage.getId());
            }
        }

        public record Hangup(long id, Type type, int deviceId, boolean isLegacy) {

            static Hangup from(HangupMessage hangupMessage) {
                return new Hangup(hangupMessage.getId(),
                        Type.from(hangupMessage.getType()),
                        hangupMessage.getDeviceId(),
                        hangupMessage.isLegacy());
            }

            public enum Type {
                NORMAL,
                ACCEPTED,
                DECLINED,
                BUSY,
                NEED_PERMISSION;

                static Type from(HangupMessage.Type type) {
                    return switch (type) {
                        case NORMAL -> NORMAL;
                        case ACCEPTED -> ACCEPTED;
                        case DECLINED -> DECLINED;
                        case BUSY -> BUSY;
                        case NEED_PERMISSION -> NEED_PERMISSION;
                    };
                }
            }
        }

        public record IceUpdate(long id, String sdp, byte[] opaque) {

            static IceUpdate from(IceUpdateMessage iceUpdateMessage) {
                return new IceUpdate(iceUpdateMessage.getId(), iceUpdateMessage.getSdp(), iceUpdateMessage.getOpaque());
            }
        }

        public record Opaque(byte[] opaque, Urgency urgency) {

            static Opaque from(OpaqueMessage opaqueMessage) {
                return new Opaque(opaqueMessage.getOpaque(), Urgency.from(opaqueMessage.getUrgency()));
            }

            public enum Urgency {
                DROPPABLE,
                HANDLE_IMMEDIATELY;

                static Urgency from(OpaqueMessage.Urgency urgency) {
                    return switch (urgency) {
                        case DROPPABLE -> DROPPABLE;
                        case HANDLE_IMMEDIATELY -> HANDLE_IMMEDIATELY;
                    };
                }
            }
        }
    }

    public record Story(
            boolean allowsReplies,
            Optional<GroupId> groupId,
            Optional<Data.Attachment> fileAttachment,
            Optional<TextAttachment> textAttachment
    ) {

        public static Story from(
                SignalServiceStoryMessage storyMessage, final AttachmentFileProvider fileProvider
        ) {
            return new Story(storyMessage.getAllowsReplies().orElse(false),
                    storyMessage.getGroupContext().map(c -> GroupUtils.getGroupIdV2(c.getMasterKey())),
                    storyMessage.getFileAttachment().map(f -> Data.Attachment.from(f, fileProvider)),
                    storyMessage.getTextAttachment().map(t -> TextAttachment.from(t, fileProvider)));
        }

        public record TextAttachment(
                Optional<String> text,
                Optional<Style> style,
                Optional<Color> textForegroundColor,
                Optional<Color> textBackgroundColor,
                Optional<Data.Preview> preview,
                Optional<Gradient> backgroundGradient,
                Optional<Color> backgroundColor
        ) {

            static TextAttachment from(
                    SignalServiceTextAttachment textAttachment, final AttachmentFileProvider fileProvider
            ) {
                return new TextAttachment(textAttachment.getText(),
                        textAttachment.getStyle().map(Style::from),
                        textAttachment.getTextForegroundColor().map(Color::new),
                        textAttachment.getTextBackgroundColor().map(Color::new),
                        textAttachment.getPreview().map(p -> Data.Preview.from(p, fileProvider)),
                        textAttachment.getBackgroundGradient().map(Gradient::from),
                        textAttachment.getBackgroundColor().map(Color::new));
            }

            public enum Style {
                DEFAULT,
                REGULAR,
                BOLD,
                SERIF,
                SCRIPT,
                CONDENSED;

                static Style from(SignalServiceTextAttachment.Style style) {
                    return switch (style) {
                        case DEFAULT -> DEFAULT;
                        case REGULAR -> REGULAR;
                        case BOLD -> BOLD;
                        case SERIF -> SERIF;
                        case SCRIPT -> SCRIPT;
                        case CONDENSED -> CONDENSED;
                    };
                }
            }

            public record Gradient(
                    List<Color> colors, List<Float> positions, Optional<Integer> angle
            ) {

                static Gradient from(SignalServiceTextAttachment.Gradient gradient) {
                    return new Gradient(gradient.getColors().stream().map(Color::new).toList(),
                            gradient.getPositions(),
                            gradient.getAngle());
                }
            }
        }
    }

    public static MessageEnvelope from(
            SignalServiceEnvelope envelope,
            SignalServiceContent content,
            RecipientResolver recipientResolver,
            RecipientAddressResolver addressResolver,
            final AttachmentFileProvider fileProvider,
            Exception exception
    ) {
        final var source = !envelope.isUnidentifiedSender() && envelope.hasSourceUuid()
                ? recipientResolver.resolveRecipient(envelope.getSourceAddress())
                : envelope.isUnidentifiedSender() && content != null
                        ? recipientResolver.resolveRecipient(content.getSender())
                        : exception instanceof ProtocolException e
                                ? recipientResolver.resolveRecipient(e.getSender())
                                : null;
        final var sourceDevice = envelope.hasSourceDevice()
                ? envelope.getSourceDevice()
                : content != null
                        ? content.getSenderDevice()
                        : exception instanceof ProtocolException e ? e.getSenderDevice() : 0;

        Optional<Receipt> receipt;
        Optional<Typing> typing;
        Optional<Data> data;
        Optional<Sync> sync;
        Optional<Call> call;
        Optional<Story> story;
        if (content != null) {
            receipt = content.getReceiptMessage().map(Receipt::from);
            typing = content.getTypingMessage().map(Typing::from);
            data = content.getDataMessage()
                    .map(dataMessage -> Data.from(dataMessage, recipientResolver, addressResolver, fileProvider));
            sync = content.getSyncMessage().map(s -> Sync.from(s, recipientResolver, addressResolver, fileProvider));
            call = content.getCallMessage().map(Call::from);
            story = content.getStoryMessage().map(s -> Story.from(s, fileProvider));
        } else {
            receipt = envelope.isReceipt() ? Optional.of(new Receipt(envelope.getServerReceivedTimestamp(),
                    Receipt.Type.DELIVERY,
                    List.of(envelope.getTimestamp()))) : Optional.empty();
            typing = Optional.empty();
            data = Optional.empty();
            sync = Optional.empty();
            call = Optional.empty();
            story = Optional.empty();
        }

        return new MessageEnvelope(source == null
                ? Optional.empty()
                : Optional.of(addressResolver.resolveRecipientAddress(source).toApiRecipientAddress()),
                sourceDevice,
                envelope.getTimestamp(),
                envelope.getServerReceivedTimestamp(),
                envelope.getServerDeliveredTimestamp(),
                envelope.isUnidentifiedSender(),
                receipt,
                typing,
                data,
                sync,
                call,
                story);
    }

    public interface AttachmentFileProvider {

        File getFile(SignalServiceAttachmentPointer pointer);
    }
}

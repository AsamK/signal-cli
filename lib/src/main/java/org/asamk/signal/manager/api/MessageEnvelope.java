package org.asamk.signal.manager.api;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
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
        Optional<Call> call
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
                    Optional.ofNullable(typingMessage.getGroupId().transform(GroupId::unknownVersion).orNull()));
        }

        public enum Type {
            STARTED,
            STOPPED,
        }
    }

    public record Data(
            long timestamp,
            Optional<GroupContext> groupContext,
            Optional<GroupCallUpdate> groupCallUpdate,
            Optional<String> body,
            int expiresInSeconds,
            boolean isExpirationUpdate,
            boolean isViewOnce,
            boolean isEndSession,
            boolean hasProfileKey,
            Optional<Reaction> reaction,
            Optional<Quote> quote,
            Optional<Payment> payment,
            List<Attachment> attachments,
            Optional<Long> remoteDeleteId,
            Optional<Sticker> sticker,
            List<SharedContact> sharedContacts,
            List<Mention> mentions,
            List<Preview> previews
    ) {

        static Data from(
                final SignalServiceDataMessage dataMessage,
                RecipientResolver recipientResolver,
                RecipientAddressResolver addressResolver,
                final AttachmentFileProvider fileProvider
        ) {
            return new Data(dataMessage.getTimestamp(),
                    Optional.ofNullable(dataMessage.getGroupContext().transform(GroupContext::from).orNull()),
                    Optional.ofNullable(dataMessage.getGroupCallUpdate().transform(GroupCallUpdate::from).orNull()),
                    Optional.ofNullable(dataMessage.getBody().orNull()),
                    dataMessage.getExpiresInSeconds(),
                    dataMessage.isExpirationUpdate(),
                    dataMessage.isViewOnce(),
                    dataMessage.isEndSession(),
                    dataMessage.getProfileKey().isPresent(),
                    Optional.ofNullable(dataMessage.getReaction()
                            .transform(r -> Reaction.from(r, recipientResolver, addressResolver))
                            .orNull()),
                    Optional.ofNullable(dataMessage.getQuote()
                            .transform(q -> Quote.from(q, recipientResolver, addressResolver, fileProvider))
                            .orNull()),
                    Optional.ofNullable(dataMessage.getPayment()
                            .transform(p -> p.getPaymentNotification().isPresent() ? Payment.from(p) : null)
                            .orNull()),
                    dataMessage.getAttachments()
                            .transform(a -> a.stream()
                                    .map(as -> Attachment.from(as, fileProvider))
                                    .collect(Collectors.toList()))
                            .or(List.of()),
                    Optional.ofNullable(dataMessage.getRemoteDelete()
                            .transform(SignalServiceDataMessage.RemoteDelete::getTargetSentTimestamp)
                            .orNull()),
                    Optional.ofNullable(dataMessage.getSticker().transform(Sticker::from).orNull()),
                    dataMessage.getSharedContacts()
                            .transform(a -> a.stream()
                                    .map(sharedContact -> SharedContact.from(sharedContact, fileProvider))
                                    .collect(Collectors.toList()))
                            .or(List.of()),
                    dataMessage.getMentions()
                            .transform(a -> a.stream()
                                    .map(m -> Mention.from(m, recipientResolver, addressResolver))
                                    .collect(Collectors.toList()))
                            .or(List.of()),
                    dataMessage.getPreviews()
                            .transform(a -> a.stream()
                                    .map(preview -> Preview.from(preview, fileProvider))
                                    .collect(Collectors.toList()))
                            .or(List.of()));
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
                        addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(reaction.getTargetAuthor())),
                        reaction.getEmoji(),
                        reaction.isRemove());
            }
        }

        public record Quote(
                long id,
                RecipientAddress author,
                Optional<String> text,
                List<Mention> mentions,
                List<Attachment> attachments
        ) {

            static Quote from(
                    SignalServiceDataMessage.Quote quote,
                    RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver,
                    final AttachmentFileProvider fileProvider
            ) {
                return new Quote(quote.getId(),
                        addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(quote.getAuthor())),
                        Optional.ofNullable(quote.getText()),
                        quote.getMentions() == null
                                ? List.of()
                                : quote.getMentions()
                                        .stream()
                                        .map(m -> Mention.from(m, recipientResolver, addressResolver))
                                        .collect(Collectors.toList()),
                        quote.getAttachments() == null
                                ? List.of()
                                : quote.getAttachments()
                                        .stream()
                                        .map(a -> Attachment.from(a, fileProvider))
                                        .collect(Collectors.toList()));
            }
        }

        public record Payment(String note, byte[] receipt) {
            static Payment from(SignalServiceDataMessage.Payment payment) {
                return new Payment(payment.getPaymentNotification().get().getNote(), payment.getPaymentNotification().get().getReceipt());
            }
        }

        public record Mention(RecipientAddress recipient, int start, int length) {

            static Mention from(
                    SignalServiceDataMessage.Mention mention,
                    RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver
            ) {
                return new Mention(addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(mention.getAci())),
                        mention.getStart(),
                        mention.getLength());
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
                    return new Attachment(Optional.of(a.getRemoteId().toString()),
                            Optional.of(fileProvider.getFile(a.getRemoteId())),
                            Optional.ofNullable(a.getFileName().orNull()),
                            a.getContentType(),
                            a.getUploadTimestamp() == 0 ? Optional.empty() : Optional.of(a.getUploadTimestamp()),
                            Optional.ofNullable(a.getSize().transform(Integer::longValue).orNull()),
                            Optional.ofNullable(a.getPreview().orNull()),
                            Optional.empty(),
                            Optional.ofNullable(a.getCaption().orNull()),
                            a.getWidth() == 0 ? Optional.empty() : Optional.of(a.getWidth()),
                            a.getHeight() == 0 ? Optional.empty() : Optional.of(a.getHeight()),
                            a.getVoiceNote(),
                            a.isGif(),
                            a.isBorderless());
                } else {
                    final var a = attachment.asStream();
                    return new Attachment(Optional.empty(),
                            Optional.empty(),
                            Optional.ofNullable(a.getFileName().orNull()),
                            a.getContentType(),
                            a.getUploadTimestamp() == 0 ? Optional.empty() : Optional.of(a.getUploadTimestamp()),
                            Optional.of(a.getLength()),
                            Optional.ofNullable(a.getPreview().orNull()),
                            Optional.empty(),
                            Optional.ofNullable(a.getCaption().orNull()),
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

        public record Sticker(byte[] packId, byte[] packKey, int stickerId) {

            static Sticker from(SignalServiceDataMessage.Sticker sticker) {
                return new Sticker(sticker.getPackId(), sticker.getPackKey(), sticker.getStickerId());
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
                        Optional.ofNullable(sharedContact.getAvatar()
                                .transform(avatar1 -> Avatar.from(avatar1, fileProvider))
                                .orNull()),
                        sharedContact.getPhone()
                                .transform(p -> p.stream().map(Phone::from).collect(Collectors.toList()))
                                .or(List.of()),
                        sharedContact.getEmail()
                                .transform(p -> p.stream().map(Email::from).collect(Collectors.toList()))
                                .or(List.of()),
                        sharedContact.getAddress()
                                .transform(p -> p.stream().map(Address::from).collect(Collectors.toList()))
                                .or(List.of()),
                        Optional.ofNullable(sharedContact.getOrganization().orNull()));
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
                    return new Name(Optional.ofNullable(name.getDisplay().orNull()),
                            Optional.ofNullable(name.getGiven().orNull()),
                            Optional.ofNullable(name.getFamily().orNull()),
                            Optional.ofNullable(name.getPrefix().orNull()),
                            Optional.ofNullable(name.getSuffix().orNull()),
                            Optional.ofNullable(name.getMiddle().orNull()));
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
                    return new Phone(phone.getValue(),
                            Type.from(phone.getType()),
                            Optional.ofNullable(phone.getLabel().orNull()));
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
                    return new Email(email.getValue(),
                            Type.from(email.getType()),
                            Optional.ofNullable(email.getLabel().orNull()));
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
                            Optional.ofNullable(address.getLabel().orNull()),
                            Optional.ofNullable(address.getLabel().orNull()),
                            Optional.ofNullable(address.getLabel().orNull()),
                            Optional.ofNullable(address.getLabel().orNull()),
                            Optional.ofNullable(address.getLabel().orNull()),
                            Optional.ofNullable(address.getLabel().orNull()),
                            Optional.ofNullable(address.getLabel().orNull()),
                            Optional.ofNullable(address.getLabel().orNull()));
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
                    SignalServiceDataMessage.Preview preview, final AttachmentFileProvider fileProvider
            ) {
                return new Preview(preview.getTitle(),
                        preview.getDescription(),
                        preview.getDate(),
                        preview.getUrl(),
                        Optional.ofNullable(preview.getImage()
                                .transform(as -> Attachment.from(as, fileProvider))
                                .orNull()));
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
            return new Sync(Optional.ofNullable(syncMessage.getSent()
                    .transform(s -> Sent.from(s, recipientResolver, addressResolver, fileProvider))
                    .orNull()),
                    Optional.ofNullable(syncMessage.getBlockedList()
                            .transform(b -> Blocked.from(b, recipientResolver, addressResolver))
                            .orNull()),
                    syncMessage.getRead()
                            .transform(r -> r.stream()
                                    .map(rm -> Read.from(rm, recipientResolver, addressResolver))
                                    .collect(Collectors.toList()))
                            .or(List.of()),
                    syncMessage.getViewed()
                            .transform(r -> r.stream()
                                    .map(rm -> Viewed.from(rm, recipientResolver, addressResolver))
                                    .collect(Collectors.toList()))
                            .or(List.of()),
                    Optional.ofNullable(syncMessage.getViewOnceOpen()
                            .transform(rm -> ViewOnceOpen.from(rm, recipientResolver, addressResolver))
                            .orNull()),
                    Optional.ofNullable(syncMessage.getContacts().transform(Contacts::from).orNull()),
                    Optional.ofNullable(syncMessage.getGroups().transform(Groups::from).orNull()),
                    Optional.ofNullable(syncMessage.getMessageRequestResponse()
                            .transform(m -> MessageRequestResponse.from(m, recipientResolver, addressResolver))
                            .orNull()));
        }

        public record Sent(
                long timestamp,
                long expirationStartTimestamp,
                Optional<RecipientAddress> destination,
                Set<RecipientAddress> recipients,
                Data message
        ) {

            static Sent from(
                    SentTranscriptMessage sentMessage,
                    RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver,
                    final AttachmentFileProvider fileProvider
            ) {
                return new Sent(sentMessage.getTimestamp(),
                        sentMessage.getExpirationStartTimestamp(),
                        Optional.ofNullable(sentMessage.getDestination()
                                .transform(d -> addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(
                                        d)))
                                .orNull()),
                        sentMessage.getRecipients()
                                .stream()
                                .map(d -> addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(d)))
                                .collect(Collectors.toSet()),
                        Data.from(sentMessage.getMessage(), recipientResolver, addressResolver, fileProvider));
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
                        .map(d -> addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(d)))
                        .collect(Collectors.toList()),
                        blockedListMessage.getGroupIds()
                                .stream()
                                .map(GroupId::unknownVersion)
                                .collect(Collectors.toList()));
            }
        }

        public record Read(RecipientAddress sender, long timestamp) {

            static Read from(
                    ReadMessage readMessage,
                    RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver
            ) {
                return new Read(addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(readMessage.getSender())),
                        readMessage.getTimestamp());
            }
        }

        public record Viewed(RecipientAddress sender, long timestamp) {

            static Viewed from(
                    ViewedMessage readMessage,
                    RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver
            ) {
                return new Viewed(addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(readMessage.getSender())),
                        readMessage.getTimestamp());
            }
        }

        public record ViewOnceOpen(RecipientAddress sender, long timestamp) {

            static ViewOnceOpen from(
                    ViewOnceOpenMessage readMessage,
                    RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver
            ) {
                return new ViewOnceOpen(addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(
                        readMessage.getSender())), readMessage.getTimestamp());
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
                        Optional.ofNullable(messageRequestResponse.getGroupId()
                                .transform(GroupId::unknownVersion)
                                .orNull()),
                        Optional.ofNullable(messageRequestResponse.getPerson()
                                .transform(p -> addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(
                                        p)))
                                .orNull()));
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
            return new Call(Optional.ofNullable(callMessage.getDestinationDeviceId().orNull()),
                    Optional.ofNullable(callMessage.getGroupId().transform(GroupId::unknownVersion).orNull()),
                    Optional.ofNullable(callMessage.getTimestamp().orNull()),
                    Optional.ofNullable(callMessage.getOfferMessage().transform(Offer::from).orNull()),
                    Optional.ofNullable(callMessage.getAnswerMessage().transform(Answer::from).orNull()),
                    Optional.ofNullable(callMessage.getHangupMessage().transform(Hangup::from).orNull()),
                    Optional.ofNullable(callMessage.getBusyMessage().transform(Busy::from).orNull()),
                    callMessage.getIceUpdateMessages()
                            .transform(m -> m.stream().map(IceUpdate::from).collect(Collectors.toList()))
                            .or(List.of()),
                    Optional.ofNullable(callMessage.getOpaqueMessage().transform(Opaque::from).orNull()));
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

    public static MessageEnvelope from(
            SignalServiceEnvelope envelope,
            SignalServiceContent content,
            RecipientResolver recipientResolver,
            RecipientAddressResolver addressResolver,
            final AttachmentFileProvider fileProvider
    ) {
        final var source = !envelope.isUnidentifiedSender() && envelope.hasSourceUuid()
                ? recipientResolver.resolveRecipient(envelope.getSourceAddress())
                : envelope.isUnidentifiedSender() && content != null
                        ? recipientResolver.resolveRecipient(content.getSender())
                        : null;
        final var sourceDevice = envelope.hasSourceDevice()
                ? envelope.getSourceDevice()
                : content != null ? content.getSenderDevice() : 0;

        Optional<Receipt> receipt;
        Optional<Typing> typing;
        Optional<Data> data;
        Optional<Sync> sync;
        Optional<Call> call;
        if (content != null) {
            receipt = Optional.ofNullable(content.getReceiptMessage().transform(Receipt::from).orNull());
            typing = Optional.ofNullable(content.getTypingMessage().transform(Typing::from).orNull());
            data = Optional.ofNullable(content.getDataMessage()
                    .transform(dataMessage -> Data.from(dataMessage, recipientResolver, addressResolver, fileProvider))
                    .orNull());
            sync = Optional.ofNullable(content.getSyncMessage()
                    .transform(s -> Sync.from(s, recipientResolver, addressResolver, fileProvider))
                    .orNull());
            call = Optional.ofNullable(content.getCallMessage().transform(Call::from).orNull());
        } else {
            receipt = Optional.empty();
            typing = Optional.empty();
            data = Optional.empty();
            sync = Optional.empty();
            call = Optional.empty();
        }

        return new MessageEnvelope(source == null
                ? Optional.empty()
                : Optional.of(addressResolver.resolveRecipientAddress(source)),
                sourceDevice,
                envelope.getTimestamp(),
                envelope.getServerReceivedTimestamp(),
                envelope.getServerDeliveredTimestamp(),
                envelope.isUnidentifiedSender(),
                receipt,
                typing,
                data,
                sync,
                call);
    }

    public interface AttachmentFileProvider {

        File getFile(SignalServiceAttachmentRemoteId attachmentRemoteId);
    }
}

package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.GroupIdV2;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.RecipientAddress;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.storage.SignalAccount;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class HistoryMessageConverter {

    private final HistoryBackupFrameParser frameParser = new HistoryBackupFrameParser();
    private final Map<Long, RecipientData> recipients = new HashMap<>();
    private final Map<Long, Long> chats = new HashMap<>();
    private final RecipientAddress selfAddress;

    HistoryMessageConverter(final SignalAccount account) {
        this(new RecipientAddress(account.getAci().toString(),
                account.getPni() == null ? null : account.getPni().toString(),
                account.getNumber(),
                null));
    }

    HistoryMessageConverter(final RecipientAddress selfAddress) {
        this.selfAddress = selfAddress;
    }

    Result accept(final String jsonLine) throws IOException {
        final var frame = frameParser.parse(jsonLine);
        if (frame instanceof HistoryBackupFrameParser.MetadataFrame) {
            return Result.metadata();
        }
        if (frame instanceof HistoryBackupFrameParser.RecipientFrame recipient) {
            collectRecipient(recipient);
            return Result.metadata();
        }
        if (frame instanceof HistoryBackupFrameParser.ChatFrame chat) {
            collectChat(chat);
            return Result.metadata();
        }
        if (frame instanceof HistoryBackupFrameParser.ChatItemFrame chatItem) {
            final var envelope = convertChatItem(chatItem);
            return envelope == null ? Result.skippedRecord() : Result.message(envelope);
        }
        return Result.skippedRecord();
    }

    private void collectRecipient(final HistoryBackupFrameParser.RecipientFrame frame) {
        final var id = frame.id();
        if (id < 0) {
            return;
        }
        if (frame.recipient() instanceof HistoryBackupFrameParser.SelfRecipient) {
            recipients.put(id, new RecipientData(selfAddress, null));
            return;
        }
        if (frame.recipient() instanceof HistoryBackupFrameParser.ContactRecipient contact) {
            final var aci = uuidFromBase64(contact.aci());
            final var pni = uuidFromBase64(contact.pni());
            final var e164Value = contact.e164();
            final var number = e164Value == null || e164Value.equals("0") ? null : "+" + e164Value;
            final var username = nonBlank(contact.username());
            if (aci != null || pni != null || number != null || username != null) {
                recipients.put(id, new RecipientData(new RecipientAddress(aci, pni, number, username), null));
            }
            return;
        }
        if (frame.recipient() instanceof HistoryBackupFrameParser.GroupRecipient group) {
            final var masterKey = group.masterKey();
            if (masterKey == null) {
                return;
            }
            try {
                final GroupIdV2 groupId = GroupUtils.getGroupIdV2(new GroupMasterKey(Base64.getDecoder()
                        .decode(masterKey)));
                recipients.put(id, new RecipientData(null, groupId));
            } catch (IllegalArgumentException | InvalidInputException ignored) {
                // A valid backup exporter will report malformed group keys separately.
            }
        }
    }

    private void collectChat(final HistoryBackupFrameParser.ChatFrame chat) {
        final var id = chat.id();
        final var recipientId = chat.recipientId();
        if (id >= 0 && recipientId >= 0) {
            chats.put(id, recipientId);
        }
    }

    private MessageEnvelope convertChatItem(final HistoryBackupFrameParser.ChatItemFrame item) {
        final var standardMessage = item.standardMessage();
        final var chatRecipientId = chats.get(item.chatId());
        final var chatRecipient = chatRecipientId == null ? null : recipients.get(chatRecipientId);
        if (chatRecipient == null) {
            return null;
        }

        final var timestamp = item.dateSent();
        final var body = Optional.ofNullable(nonBlank(standardMessage.body()));
        final var expiresInSeconds = (int) Math.min(Integer.MAX_VALUE,
                Math.max(0, item.expiresInMs() / 1000));
        final Optional<MessageEnvelope.Data.GroupContext> groupContext = chatRecipient.groupId() == null
                ? Optional.empty()
                : Optional.of(new MessageEnvelope.Data.GroupContext(chatRecipient.groupId(), false, 0));
        final var data = new MessageEnvelope.Data(timestamp,
                groupContext,
                Optional.empty(),
                Optional.empty(),
                body,
                expiresInSeconds,
                false,
                false,
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                attachments(standardMessage.attachments()),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        if (item.direction() instanceof HistoryBackupFrameParser.Outgoing) {
            final var destination = chatRecipient.groupId() == null
                    ? Optional.ofNullable(chatRecipient.address())
                    : Optional.<RecipientAddress>empty();
            final var sent = new MessageEnvelope.Sync.Sent(timestamp,
                    item.expireStartDate(),
                    destination,
                    Set.of(),
                    Optional.of(data),
                    Optional.empty(),
                    Optional.empty());
            final var sync = new MessageEnvelope.Sync(Optional.of(sent),
                    Optional.empty(),
                    List.of(),
                    List.of(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());
            return new MessageEnvelope(Optional.empty(),
                    1,
                    timestamp,
                    0,
                    timestamp,
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(sync),
                    Optional.empty(),
                    Optional.empty());
        }

        if (!(item.direction() instanceof HistoryBackupFrameParser.Incoming incoming)) {
            return null;
        }
        final var author = recipients.get(item.authorId());
        if (author == null || author.address() == null) {
            return null;
        }
        return new MessageEnvelope(Optional.of(author.address()),
                1,
                timestamp,
                incoming.dateServerSent(),
                incoming.dateReceived(),
                incoming.sealedSender(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(data),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static List<MessageEnvelope.Data.Attachment> attachments(
            final List<HistoryBackupFrameParser.Attachment> attachments
    ) {
        return attachments.stream().map(attachment -> {
            final var cdnKey = nonBlank(attachment.transitCdnKey());
            final var flag = attachment.flag();
            return new MessageEnvelope.Data.Attachment(Optional.ofNullable(cdnKey),
                    Optional.empty(),
                    Optional.ofNullable(nonBlank(attachment.fileName())),
                    Optional.ofNullable(nonBlank(attachment.contentType()))
                            .orElse("application/octet-stream"),
                    attachment.uploadTimestamp() == 0
                            ? Optional.empty()
                            : Optional.of(attachment.uploadTimestamp()),
                    attachment.size() == 0 ? Optional.empty() : Optional.of(attachment.size()),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.ofNullable(nonBlank(attachment.caption())),
                    optionalInt(attachment.width()),
                    optionalInt(attachment.height()),
                    "VOICE_MESSAGE".equals(flag),
                    "GIF".equals(flag),
                    "BORDERLESS".equals(flag));
        }).toList();
    }

    private static Optional<Integer> optionalInt(final long value) {
        return value == 0 ? Optional.empty() : Optional.of((int) Math.min(Integer.MAX_VALUE, value));
    }

    private static String nonBlank(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String uuidFromBase64(final String value) {
        if (value == null) {
            return null;
        }
        try {
            final var bytes = Base64.getDecoder().decode(value);
            if (bytes.length != 16) {
                return null;
            }
            final var buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong()).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    record Result(MessageEnvelope envelope, boolean skipped) {

        static Result message(final MessageEnvelope envelope) {
            return new Result(envelope, false);
        }

        static Result skippedRecord() {
            return new Result(null, true);
        }

        static Result metadata() {
            return new Result(null, false);
        }
    }

    private record RecipientData(RecipientAddress address, GroupIdV2 groupId) {
    }
}

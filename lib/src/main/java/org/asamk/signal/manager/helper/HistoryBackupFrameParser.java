package org.asamk.signal.manager.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Isolates the JSON schema emitted by BackupJsonExporter from the history import state machine.
 */
final class HistoryBackupFrameParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    Frame parse(final String jsonLine) throws IOException {
        final var root = objectMapper.readTree(jsonLine);
        if (root.has("account")) {
            return new MetadataFrame();
        }
        if (root.has("recipient")) {
            return recipient(root.get("recipient"));
        }
        if (root.has("chat")) {
            final var chat = root.get("chat");
            return new ChatFrame(longValue(chat, "id", -1), longValue(chat, "recipientId", -1));
        }
        if (root.has("chatItem")) {
            return chatItem(root.get("chatItem"));
        }
        return new UnsupportedFrame();
    }

    private static Frame recipient(final JsonNode recipient) {
        final var id = longValue(recipient, "id", -1);
        if (recipient.has("self")) {
            return new RecipientFrame(id, new SelfRecipient());
        }
        final var contact = recipient.get("contact");
        if (contact != null) {
            return new RecipientFrame(id,
                    new ContactRecipient(textValue(contact, "aci"),
                            textValue(contact, "pni"),
                            textValue(contact, "e164"),
                            textValue(contact, "username")));
        }
        final var group = recipient.get("group");
        if (group != null) {
            return new RecipientFrame(id, new GroupRecipient(textValue(group, "masterKey")));
        }
        return new RecipientFrame(id, new UnsupportedRecipient());
    }

    private static Frame chatItem(final JsonNode item) {
        final var standardMessage = item.get("standardMessage");
        if (standardMessage == null) {
            return new UnsupportedFrame();
        }
        final Direction direction;
        if (item.has("outgoing")) {
            direction = new Outgoing();
        } else if (item.has("incoming")) {
            final var incoming = item.get("incoming");
            direction = new Incoming(longValue(incoming, "dateServerSent", 0),
                    longValue(incoming, "dateReceived", longValue(item, "dateSent", 0)),
                    booleanValue(incoming, "sealedSender"));
        } else {
            direction = new UnsupportedDirection();
        }

        final var text = standardMessage.get("text");
        return new ChatItemFrame(longValue(item, "chatId", -1),
                longValue(item, "authorId", -1),
                longValue(item, "dateSent", 0),
                longValue(item, "expiresInMs", 0),
                longValue(item, "expireStartDate", 0),
                direction,
                new StandardMessage(text == null ? null : textValue(text, "body"),
                        attachments(standardMessage.get("attachments"))));
    }

    private static List<Attachment> attachments(final JsonNode attachments) {
        if (attachments == null || !attachments.isArray()) {
            return List.of();
        }
        final var result = new ArrayList<Attachment>();
        for (final var attachment : attachments) {
            final var pointer = attachment.get("pointer");
            if (pointer == null) {
                continue;
            }
            final var locator = pointer.get("locatorInfo");
            result.add(new Attachment(locator == null ? null : textValue(locator, "transitCdnKey"),
                    textValue(pointer, "fileName"),
                    textValue(pointer, "contentType"),
                    locator == null ? 0 : longValue(locator, "transitTierUploadTimestamp", 0),
                    locator == null ? 0 : longValue(locator, "size", 0),
                    textValue(pointer, "caption"),
                    longValue(pointer, "width", 0),
                    longValue(pointer, "height", 0),
                    textValue(attachment, "flag")));
        }
        return List.copyOf(result);
    }

    private static long longValue(final JsonNode node, final String field, final long defaultValue) {
        final var value = node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isNumber()) {
            return value.longValue();
        }
        try {
            return Long.parseLong(value.asText());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean booleanValue(final JsonNode node, final String field) {
        final var value = node.get(field);
        return value != null && value.asBoolean(false);
    }

    private static String textValue(final JsonNode node, final String field) {
        final var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    sealed interface Frame permits MetadataFrame, RecipientFrame, ChatFrame, ChatItemFrame, UnsupportedFrame {
    }

    record MetadataFrame() implements Frame {
    }

    record RecipientFrame(long id, Recipient recipient) implements Frame {
    }

    record ChatFrame(long id, long recipientId) implements Frame {
    }

    record ChatItemFrame(
            long chatId,
            long authorId,
            long dateSent,
            long expiresInMs,
            long expireStartDate,
            Direction direction,
            StandardMessage standardMessage
    ) implements Frame {
    }

    record UnsupportedFrame() implements Frame {
    }

    sealed interface Recipient permits SelfRecipient, ContactRecipient, GroupRecipient, UnsupportedRecipient {
    }

    record SelfRecipient() implements Recipient {
    }

    record ContactRecipient(String aci, String pni, String e164, String username) implements Recipient {
    }

    record GroupRecipient(String masterKey) implements Recipient {
    }

    record UnsupportedRecipient() implements Recipient {
    }

    sealed interface Direction permits Incoming, Outgoing, UnsupportedDirection {
    }

    record Incoming(long dateServerSent, long dateReceived, boolean sealedSender) implements Direction {
    }

    record Outgoing() implements Direction {
    }

    record UnsupportedDirection() implements Direction {
    }

    record StandardMessage(String body, List<Attachment> attachments) {
    }

    record Attachment(
            String transitCdnKey,
            String fileName,
            String contentType,
            long uploadTimestamp,
            long size,
            String caption,
            long width,
            long height,
            String flag
    ) {
    }
}

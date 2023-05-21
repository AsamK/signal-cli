package org.asamk.signal.dbus;

import org.asamk.Signal;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.RecipientAddress;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.Variant;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbusReceiveMessageHandler implements Manager.ReceiveMessageHandler {

    private final DBusConnection conn;
    private final String objectPath;

    public DbusReceiveMessageHandler(DBusConnection conn, final String objectPath) {
        this.conn = conn;
        this.objectPath = objectPath;
    }

    @Override
    public void handleMessage(MessageEnvelope envelope, Throwable exception) {
        try {
            sendDbusMessages(envelope);
        } catch (DBusException e) {
            e.printStackTrace();
        }
    }

    private void sendDbusMessages(MessageEnvelope envelope) throws DBusException {
        final var senderString = envelope.sourceAddress().map(RecipientAddress::getLegacyIdentifier).orElse("");
        if (envelope.receipt().isPresent()) {
            final var receiptMessage = envelope.receipt().get();
            final var type = switch (receiptMessage.type()) {
                case READ -> "read";
                case VIEWED -> "viewed";
                case DELIVERY -> "delivery";
                case UNKNOWN -> "unknown";
            };
            for (long timestamp : receiptMessage.timestamps()) {
                conn.sendMessage(new Signal.ReceiptReceived(objectPath, timestamp, senderString));
                conn.sendMessage(new Signal.ReceiptReceivedV2(objectPath, timestamp, senderString, type, Map.of()));
            }
        }
        if (envelope.data().isPresent()) {
            var message = envelope.data().get();

            var groupId = message.groupContext()
                    .map(MessageEnvelope.Data.GroupContext::groupId)
                    .map(GroupId::serialize)
                    .orElseGet(() -> new byte[0]);
            var isGroupUpdate = message.groupContext()
                    .map(MessageEnvelope.Data.GroupContext::isGroupUpdate)
                    .orElse(false);
            if (!message.isEndSession() && !isGroupUpdate) {
                conn.sendMessage(new Signal.MessageReceived(objectPath,
                        message.timestamp(),
                        senderString,
                        groupId,
                        message.body().orElse(""),
                        getAttachments(message)));
                conn.sendMessage(new Signal.MessageReceivedV2(objectPath,
                        message.timestamp(),
                        senderString,
                        groupId,
                        message.body().orElse(""),
                        getMessageExtras(message)));
            }
        }
        if (envelope.sync().isPresent()) {
            var syncMessage = envelope.sync().get();
            if (syncMessage.sent().isPresent()) {
                var transcript = syncMessage.sent().get();

                if (transcript.message().isPresent()) {
                    final var dataMessage = transcript.message().get();
                    if (transcript.destination().isPresent() || dataMessage.groupContext().isPresent()) {
                        var groupId = dataMessage.groupContext()
                                .map(MessageEnvelope.Data.GroupContext::groupId)
                                .map(GroupId::serialize)
                                .orElseGet(() -> new byte[0]);

                        conn.sendMessage(new Signal.SyncMessageReceived(objectPath,
                                dataMessage.timestamp(),
                                senderString,
                                transcript.destination().map(RecipientAddress::getLegacyIdentifier).orElse(""),
                                groupId,
                                dataMessage.body().orElse(""),
                                getAttachments(dataMessage)));
                        conn.sendMessage(new Signal.SyncMessageReceivedV2(objectPath,
                                dataMessage.timestamp(),
                                senderString,
                                transcript.destination().map(RecipientAddress::getLegacyIdentifier).orElse(""),
                                groupId,
                                dataMessage.body().orElse(""),
                                getMessageExtras(dataMessage)));
                    }
                }
            }
        }

    }

    private List<String> getAttachments(MessageEnvelope.Data message) {
        var attachments = new ArrayList<String>();
        if (message.attachments().size() > 0) {
            for (var attachment : message.attachments()) {
                if (attachment.file().isPresent()) {
                    attachments.add(attachment.file().get().getAbsolutePath());
                }
            }
        }
        return attachments;
    }

    private HashMap<String, Variant<?>> getMessageExtras(MessageEnvelope.Data message) {
        var extras = new HashMap<String, Variant<?>>();
        if (message.attachments().size() > 0) {
            var attachments = message.attachments()
                    .stream()
                    .filter(a -> a.id().isPresent())
                    .map(this::getAttachmentMap)
                    .toList();
            extras.put("attachments", new Variant<>(attachments, "aa{sv}"));
        }
        if (message.mentions().size() > 0) {
            var mentions = message.mentions().stream().map(this::getMentionMap).toList();
            extras.put("mentions", new Variant<>(mentions, "aa{sv}"));
        }
        extras.put("expiresInSeconds", new Variant<>(message.expiresInSeconds()));
        if (message.quote().isPresent()) {
            extras.put("quote", new Variant<>(getQuoteMap(message.quote().get()), "a{sv}"));
        }
        if (message.reaction().isPresent()) {
            final var reaction = message.reaction().get();
            extras.put("reaction", new Variant<>(getReactionMap(reaction), "a{sv}"));
        }
        if (message.remoteDeleteId().isPresent()) {
            extras.put("remoteDelete",
                    new Variant<>(Map.of("timestamp", new Variant<>(message.remoteDeleteId().get())), "a{sv}"));
        }
        if (message.sticker().isPresent()) {
            final var sticker = message.sticker().get();
            extras.put("sticker", new Variant<>(getStickerMap(sticker), "a{sv}"));
        }
        extras.put("isViewOnce", new Variant<>(message.isViewOnce()));
        return extras;
    }

    private Map<String, Variant<?>> getQuoteMap(final MessageEnvelope.Data.Quote quote) {
        return Map.of("id",
                new Variant<>(quote.id()),
                "author",
                new Variant<>(quote.author().getLegacyIdentifier()),
                "text",
                new Variant<>(quote.text().orElse("")));
    }

    private Map<String, Variant<? extends Serializable>> getStickerMap(final MessageEnvelope.Data.Sticker sticker) {
        return Map.of("packId",
                new Variant<>(sticker.packId().serialize()),
                "stickerId",
                new Variant<>(sticker.stickerId()));
    }

    private Map<String, Variant<?>> getReactionMap(final MessageEnvelope.Data.Reaction reaction) {
        return Map.of("emoji",
                new Variant<>(reaction.emoji()),
                "targetAuthor",
                new Variant<>(reaction.targetAuthor().getLegacyIdentifier()),
                "targetSentTimestamp",
                new Variant<>(reaction.targetSentTimestamp()),
                "isRemove",
                new Variant<>(reaction.isRemove()));
    }

    private Map<String, Variant<?>> getAttachmentMap(
            final MessageEnvelope.Data.Attachment a
    ) {
        final var map = new HashMap<String, Variant<?>>();
        if (a.id().isPresent()) {
            map.put("remoteId", new Variant<>(a.id().get()));
        }
        if (a.file().isPresent()) {
            map.put("file", new Variant<>(a.file().get().getAbsolutePath()));
        }
        map.put("contentType", new Variant<>(a.contentType()));
        map.put("isVoiceNote", new Variant<>(a.isVoiceNote()));
        map.put("isBorderless", new Variant<>(a.isBorderless()));
        map.put("isGif", new Variant<>(a.isGif()));
        if (a.caption().isPresent()) {
            map.put("caption", new Variant<>(a.caption().get()));
        }
        if (a.fileName().isPresent()) {
            map.put("fileName", new Variant<>(a.fileName().get()));
        }
        if (a.size().isPresent()) {
            map.put("size", new Variant<>(a.size().get()));
        }
        if (a.width().isPresent()) {
            map.put("width", new Variant<>(a.width().get()));
        }
        if (a.height().isPresent()) {
            map.put("height", new Variant<>(a.height().get()));
        }
        return map;
    }

    private Map<String, Variant<?>> getMentionMap(
            final MessageEnvelope.Data.Mention mention
    ) {
        return Map.of("recipient",
                new Variant<>(mention.recipient().getLegacyIdentifier()),
                "start",
                new Variant<>(mention.start()),
                "length",
                new Variant<>(mention.length()));
    }
}

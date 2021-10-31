package org.asamk.signal;

import org.asamk.Signal;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.util.Util;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.Variant;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public class DbusReceiveMessageHandler implements Manager.ReceiveMessageHandler {

    private final Manager m;
    private final DBusConnection conn;
    private final String objectPath;

    public DbusReceiveMessageHandler(Manager m, DBusConnection conn, final String objectPath) {
        this.m = m;
        this.conn = conn;
        this.objectPath = objectPath;
    }

    @Override
    public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) {
        try {
            sendDbusMessages(envelope, content);
        } catch (DBusException e) {
            e.printStackTrace();
        }
    }

    private void sendDbusMessages(
            final SignalServiceEnvelope envelope, final SignalServiceContent content
    ) throws DBusException {
        if (envelope.isReceipt()) {
            conn.sendMessage(new Signal.ReceiptReceived(objectPath, envelope.getTimestamp(),
                    // A receipt envelope always has a source address
                    getLegacyIdentifier(envelope.getSourceAddress())));
            conn.sendMessage(new Signal.ReceiptReceivedV2(objectPath, envelope.getTimestamp(),
                    // A receipt envelope always has a source address
                    getLegacyIdentifier(envelope.getSourceAddress()), "delivery", Map.of()));
        } else if (content != null) {
            final var sender = !envelope.isUnidentifiedSender() && envelope.hasSourceUuid()
                    ? envelope.getSourceAddress()
                    : content.getSender();
            final var senderString = getLegacyIdentifier(sender);
            if (content.getReceiptMessage().isPresent()) {
                final var receiptMessage = content.getReceiptMessage().get();
                final var type = switch (receiptMessage.getType()) {
                    case READ -> "read";
                    case VIEWED -> "viewed";
                    case DELIVERY -> "delivery";
                    case UNKNOWN -> "unknown";
                };
                for (long timestamp : receiptMessage.getTimestamps()) {
                    conn.sendMessage(new Signal.ReceiptReceived(objectPath, timestamp, senderString));
                    conn.sendMessage(new Signal.ReceiptReceivedV2(objectPath,
                            envelope.getTimestamp(),
                            senderString,
                            type,
                            Map.of()));
                }

            } else if (content.getDataMessage().isPresent()) {
                var message = content.getDataMessage().get();

                var groupId = getGroupId(message);
                if (!message.isEndSession() && (
                        groupId == null
                                || message.getGroupContext().get().getGroupV1Type() == null
                                || message.getGroupContext().get().getGroupV1Type() == SignalServiceGroup.Type.DELIVER
                )) {
                    conn.sendMessage(new Signal.MessageReceived(objectPath,
                            message.getTimestamp(),
                            senderString,
                            groupId != null ? groupId : new byte[0],
                            message.getBody().or(""),
                            getAttachments(message)));
                    conn.sendMessage(new Signal.MessageReceivedV2(objectPath,
                            message.getTimestamp(),
                            senderString,
                            groupId != null ? groupId : new byte[0],
                            message.getBody().or(""),
                            getMessageExtras(message)));
                }
            } else if (content.getSyncMessage().isPresent()) {
                var sync_message = content.getSyncMessage().get();
                if (sync_message.getSent().isPresent()) {
                    var transcript = sync_message.getSent().get();

                    if (transcript.getDestination().isPresent() || transcript.getMessage()
                            .getGroupContext()
                            .isPresent()) {
                        var message = transcript.getMessage();
                        var groupId = getGroupId(message);

                        conn.sendMessage(new Signal.SyncMessageReceived(objectPath,
                                transcript.getTimestamp(),
                                senderString,
                                transcript.getDestination().transform(Util::getLegacyIdentifier).or(""),
                                groupId != null ? groupId : new byte[0],
                                message.getBody().or(""),
                                getAttachments(message)));
                        conn.sendMessage(new Signal.SyncMessageReceivedV2(objectPath,
                                transcript.getTimestamp(),
                                senderString,
                                transcript.getDestination().transform(Util::getLegacyIdentifier).or(""),
                                groupId != null ? groupId : new byte[0],
                                message.getBody().or(""),
                                getMessageExtras(message)));
                    }
                }
            }
        }
    }

    private byte[] getGroupId(final SignalServiceDataMessage message) {
        return message.getGroupContext().isPresent() ? GroupUtils.getGroupId(message.getGroupContext().get())
                .serialize() : null;
    }

    private List<String> getAttachments(SignalServiceDataMessage message) {
        var attachments = new ArrayList<String>();
        if (message.getAttachments().isPresent()) {
            for (var attachment : message.getAttachments().get()) {
                if (attachment.isPointer()) {
                    attachments.add(m.getAttachmentFile(attachment.asPointer().getRemoteId()).getAbsolutePath());
                }
            }
        }
        return attachments;
    }

    private HashMap<String, Variant<?>> getMessageExtras(SignalServiceDataMessage message) {
        var extras = new HashMap<String, Variant<?>>();
        if (message.getAttachments().isPresent()) {
            var attachments = message.getAttachments()
                    .get()
                    .stream()
                    .filter(SignalServiceAttachment::isPointer)
                    .map(a -> getAttachmentMap(m, a))
                    .collect(Collectors.toList());
            extras.put("attachments", new Variant<>(attachments, "aa{sv}"));
        }
        if (message.getMentions().isPresent()) {
            var mentions = message.getMentions()
                    .get()
                    .stream()
                    .map(mention -> getMentionMap(m, mention))
                    .collect(Collectors.toList());
            extras.put("mentions", new Variant<>(mentions, "aa{sv}"));
        }
        extras.put("expiresInSeconds", new Variant<>(message.getExpiresInSeconds()));
        if (message.getQuote().isPresent()) {
            extras.put("quote", new Variant<>(getQuoteMap(message.getQuote().get()), "a{sv}"));
        }
        if (message.getReaction().isPresent()) {
            final var reaction = message.getReaction().get();
            extras.put("reaction", new Variant<>(getReactionMap(reaction), "a{sv}"));
        }
        if (message.getRemoteDelete().isPresent()) {
            extras.put("remoteDelete",
                    new Variant<>(Map.of("timestamp", new Variant<>(message.getRemoteDelete())), "a{sv}"));
        }
        if (message.getSticker().isPresent()) {
            final var sticker = message.getSticker().get();
            extras.put("sticker", new Variant<>(getStickerMap(sticker), "a{sv}"));
        }
        extras.put("isViewOnce", new Variant<>(message.isViewOnce()));
        return extras;
    }

    private Map<String, Variant<?>> getQuoteMap(final SignalServiceDataMessage.Quote quote) {
        return Map.of("id",
                new Variant<>(quote.getId()),
                "author",
                new Variant<>(getLegacyIdentifier(m.resolveSignalServiceAddress(quote.getAuthor()))),
                "text",
                new Variant<>(quote.getText()));
    }

    private Map<String, Variant<? extends Serializable>> getStickerMap(final SignalServiceDataMessage.Sticker sticker) {
        return Map.of("packId", new Variant<>(sticker.getPackId()), "stickerId", new Variant<>(sticker.getStickerId()));
    }

    private Map<String, Variant<?>> getReactionMap(final SignalServiceDataMessage.Reaction reaction) {
        return Map.of("emoji",
                new Variant<>(reaction.getEmoji()),
                "targetAuthor",
                new Variant<>(getLegacyIdentifier(m.resolveSignalServiceAddress(reaction.getTargetAuthor()))),
                "targetSentTimestamp",
                new Variant<>(reaction.getTargetSentTimestamp()),
                "isRemove",
                new Variant<>(reaction.isRemove()));
    }

    private Map<String, Variant<?>> getAttachmentMap(final Manager m, final SignalServiceAttachment attachment) {
        final var a = attachment.asPointer();
        final var map = new HashMap<String, Variant<?>>();
        map.put("file", new Variant<>(m.getAttachmentFile(a.getRemoteId()).getAbsolutePath()));
        map.put("remoteId", new Variant<>(a.getRemoteId().toString()));
        map.put("isVoiceNote", new Variant<>(a.getVoiceNote()));
        map.put("isBorderless", new Variant<>(a.isBorderless()));
        map.put("isGif", new Variant<>(a.isGif()));
        if (a.getCaption().isPresent()) {
            map.put("caption", new Variant<>(a.getCaption().get()));
        }
        if (a.getFileName().isPresent()) {
            map.put("fileName", new Variant<>(a.getFileName().get()));
        }
        if (a.getSize().isPresent()) {
            map.put("size", new Variant<>(a.getSize().get()));
        }
        if (a.getWidth() > 0 || a.getHeight() > 0) {
            map.put("height", new Variant<>(a.getHeight()));
            map.put("width", new Variant<>(a.getWidth()));
        }
        return map;
    }

    private Map<String, Variant<?>> getMentionMap(
            final Manager m, final SignalServiceDataMessage.Mention mention
    ) {
        return Map.of("recipient",
                new Variant<>(getLegacyIdentifier(m.resolveSignalServiceAddress(new SignalServiceAddress(mention.getUuid())))),
                "start",
                new Variant<>(mention.getStart()),
                "length",
                new Variant<>(mention.getLength()));
    }
}

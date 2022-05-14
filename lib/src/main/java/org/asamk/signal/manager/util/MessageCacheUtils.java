package org.asamk.signal.manager.util;

import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Optional;

public class MessageCacheUtils {

    public static SignalServiceEnvelope loadEnvelope(File file) throws IOException {
        try (var f = new FileInputStream(file)) {
            var in = new DataInputStream(f);
            var version = in.readInt();
            if (version > 5) {
                return null;
            }
            var type = in.readInt();
            var source = in.readUTF();
            ServiceId sourceServiceId = null;
            if (version >= 3) {
                sourceServiceId = ServiceId.parseOrNull(in.readUTF());
            }
            var sourceDevice = in.readInt();
            if (version == 1) {
                // read legacy relay field
                in.readUTF();
            }
            String destinationUuid = null;
            if (version >= 5) {
                destinationUuid = in.readUTF();
            }
            var timestamp = in.readLong();
            byte[] content = null;
            var contentLen = in.readInt();
            if (contentLen > 0) {
                content = new byte[contentLen];
                in.readFully(content);
            }
            byte[] legacyMessage = null;
            var legacyMessageLen = in.readInt();
            if (legacyMessageLen > 0) {
                legacyMessage = new byte[legacyMessageLen];
                in.readFully(legacyMessage);
            }
            long serverReceivedTimestamp = 0;
            String uuid = null;
            if (version >= 2) {
                serverReceivedTimestamp = in.readLong();
                uuid = in.readUTF();
                if ("".equals(uuid)) {
                    uuid = null;
                }
            }
            long serverDeliveredTimestamp = 0;
            if (version >= 4) {
                serverDeliveredTimestamp = in.readLong();
            }
            Optional<SignalServiceAddress> addressOptional = sourceServiceId == null
                    ? Optional.empty()
                    : Optional.of(new SignalServiceAddress(sourceServiceId, source));
            return new SignalServiceEnvelope(type,
                    addressOptional,
                    sourceDevice,
                    timestamp,
                    legacyMessage,
                    content,
                    serverReceivedTimestamp,
                    serverDeliveredTimestamp,
                    uuid,
                    destinationUuid == null ? UuidUtil.UNKNOWN_UUID.toString() : destinationUuid);
        }
    }

    public static void storeEnvelope(SignalServiceEnvelope envelope, File file) throws IOException {
        try (var f = new FileOutputStream(file)) {
            try (var out = new DataOutputStream(f)) {
                out.writeInt(5); // version
                out.writeInt(envelope.getType());
                out.writeUTF(envelope.getSourceE164().isPresent() ? envelope.getSourceE164().get() : "");
                out.writeUTF(envelope.getSourceUuid().isPresent() ? envelope.getSourceUuid().get() : "");
                out.writeInt(envelope.getSourceDevice());
                out.writeUTF(envelope.getDestinationUuid() == null ? "" : envelope.getDestinationUuid());
                out.writeLong(envelope.getTimestamp());
                if (envelope.hasContent()) {
                    out.writeInt(envelope.getContent().length);
                    out.write(envelope.getContent());
                } else {
                    out.writeInt(0);
                }
                if (envelope.hasLegacyMessage()) {
                    out.writeInt(envelope.getLegacyMessage().length);
                    out.write(envelope.getLegacyMessage());
                } else {
                    out.writeInt(0);
                }
                out.writeLong(envelope.getServerReceivedTimestamp());
                var uuid = envelope.getServerGuid();
                out.writeUTF(uuid == null ? "" : uuid);
                out.writeLong(envelope.getServerDeliveredTimestamp());
            }
        }
    }
}

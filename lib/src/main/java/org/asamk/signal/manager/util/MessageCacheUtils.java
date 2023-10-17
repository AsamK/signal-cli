package org.asamk.signal.manager.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.Envelope;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Optional;

public class MessageCacheUtils {

    private final static Logger logger = LoggerFactory.getLogger(MessageCacheUtils.class);

    final static int CURRENT_VERSION = 9;

    public static SignalServiceEnvelope loadEnvelope(File file) throws IOException {
        try (var f = new FileInputStream(file)) {
            var in = new DataInputStream(f);
            var version = in.readInt();
            logger.trace("Reading cached envelope file with version {} (current: {})", version, CURRENT_VERSION);
            if (version > CURRENT_VERSION) {
                logger.warn("Unsupported envelope version {} (current: {})", version, CURRENT_VERSION);
                // Unsupported envelope version
                return null;
            }
            if (version >= 9) {
                final var serverReceivedTimestamp = in.readLong();
                final var envelope = Envelope.ADAPTER.decode(in.readAllBytes());
                return new SignalServiceEnvelope(envelope, serverReceivedTimestamp);
            } else {
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
                var legacyMessageLen = in.readInt();
                if (legacyMessageLen > 0) {
                    byte[] legacyMessage = new byte[legacyMessageLen];
                    in.readFully(legacyMessage);
                }
                long serverReceivedTimestamp = 0;
                String uuid = null;
                if (version >= 2) {
                    serverReceivedTimestamp = in.readLong();
                    uuid = in.readUTF();
                    if (uuid.isEmpty()) {
                        uuid = null;
                    }
                }
                long serverDeliveredTimestamp = 0;
                if (version >= 4) {
                    serverDeliveredTimestamp = in.readLong();
                }
                boolean isUrgent = true;
                if (version >= 6) {
                    isUrgent = in.readBoolean();
                }
                boolean isStory = true;
                if (version >= 7) {
                    isStory = in.readBoolean();
                }
                String updatedPni = null;
                if (version >= 8) {
                    updatedPni = in.readUTF();
                }
                Optional<SignalServiceAddress> addressOptional = sourceServiceId == null
                        ? Optional.empty()
                        : Optional.of(new SignalServiceAddress(sourceServiceId, source));
                return new SignalServiceEnvelope(type,
                        addressOptional,
                        sourceDevice,
                        timestamp,
                        content,
                        serverReceivedTimestamp,
                        serverDeliveredTimestamp,
                        uuid,
                        destinationUuid == null ? UuidUtil.UNKNOWN_UUID.toString() : destinationUuid,
                        isUrgent,
                        isStory,
                        null,
                        updatedPni == null ? "" : updatedPni);
            }
        }
    }

    public static void storeEnvelope(SignalServiceEnvelope envelope, File file) throws IOException {
        try (var f = new FileOutputStream(file)) {
            try (var out = new DataOutputStream(f)) {
                out.writeInt(CURRENT_VERSION); // version
                out.writeLong(envelope.getServerDeliveredTimestamp());
                envelope.getProto().encode(out);
            }
        }
    }
}

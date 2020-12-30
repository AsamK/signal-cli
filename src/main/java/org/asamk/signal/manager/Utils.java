package org.asamk.signal.manager;

import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.libsignal.fingerprint.NumericFingerprintGenerator;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;
import org.whispersystems.util.Base64;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.whispersystems.signalservice.internal.util.Util.isEmpty;

class Utils {

    static List<SignalServiceAttachment> getSignalServiceAttachments(List<String> attachments) throws AttachmentInvalidException {
        List<SignalServiceAttachment> signalServiceAttachments = null;
        if (attachments != null) {
            signalServiceAttachments = new ArrayList<>(attachments.size());
            for (String attachment : attachments) {
                try {
                    signalServiceAttachments.add(createAttachment(new File(attachment)));
                } catch (IOException e) {
                    throw new AttachmentInvalidException(attachment, e);
                }
            }
        }
        return signalServiceAttachments;
    }

    static String getFileMimeType(File file, String defaultMimeType) throws IOException {
        String mime = Files.probeContentType(file.toPath());
        if (mime == null) {
            try (InputStream bufferedStream = new BufferedInputStream(new FileInputStream(file))) {
                mime = URLConnection.guessContentTypeFromStream(bufferedStream);
            }
        }
        if (mime == null) {
            return defaultMimeType;
        }
        return mime;
    }

    static SignalServiceAttachmentStream createAttachment(File attachmentFile) throws IOException {
        InputStream attachmentStream = new FileInputStream(attachmentFile);
        final long attachmentSize = attachmentFile.length();
        final String mime = getFileMimeType(attachmentFile, "application/octet-stream");
        // TODO mabybe add a parameter to set the voiceNote, borderless, preview, width, height and caption option
        final long uploadTimestamp = System.currentTimeMillis();
        Optional<byte[]> preview = Optional.absent();
        Optional<String> caption = Optional.absent();
        Optional<String> blurHash = Optional.absent();
        final Optional<ResumableUploadSpec> resumableUploadSpec = Optional.absent();
        return new SignalServiceAttachmentStream(attachmentStream,
                mime,
                attachmentSize,
                Optional.of(attachmentFile.getName()),
                false,
                false,
                preview,
                0,
                0,
                uploadTimestamp,
                caption,
                blurHash,
                null,
                null,
                resumableUploadSpec);
    }

    static StreamDetails createStreamDetailsFromFile(File file) throws IOException {
        InputStream stream = new FileInputStream(file);
        final long size = file.length();
        String mime = Files.probeContentType(file.toPath());
        if (mime == null) {
            mime = "application/octet-stream";
        }
        return new StreamDetails(stream, mime, size);
    }

    static CertificateValidator getCertificateValidator() {
        try {
            ECPublicKey unidentifiedSenderTrustRoot = Curve.decodePoint(Base64.decode(ServiceConfig.UNIDENTIFIED_SENDER_TRUST_ROOT),
                    0);
            return new CertificateValidator(unidentifiedSenderTrustRoot);
        } catch (InvalidKeyException | IOException e) {
            throw new AssertionError(e);
        }
    }

    private static Map<String, String> getQueryMap(String query) {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<>();
        for (String param : params) {
            final String[] paramParts = param.split("=");
            String name = URLDecoder.decode(paramParts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(paramParts[1], StandardCharsets.UTF_8);
            map.put(name, value);
        }
        return map;
    }

    static String createDeviceLinkUri(DeviceLinkInfo info) {
        return "tsdevice:/?uuid="
                + URLEncoder.encode(info.deviceIdentifier, StandardCharsets.UTF_8)
                + "&pub_key="
                + URLEncoder.encode(Base64.encodeBytesWithoutPadding(info.deviceKey.serialize()),
                StandardCharsets.UTF_8);
    }

    static DeviceLinkInfo parseDeviceLinkUri(URI linkUri) throws IOException, InvalidKeyException {
        Map<String, String> query = getQueryMap(linkUri.getRawQuery());
        String deviceIdentifier = query.get("uuid");
        String publicKeyEncoded = query.get("pub_key");

        if (isEmpty(deviceIdentifier) || isEmpty(publicKeyEncoded)) {
            throw new RuntimeException("Invalid device link uri");
        }

        ECPublicKey deviceKey = Curve.decodePoint(Base64.decode(publicKeyEncoded), 0);

        return new DeviceLinkInfo(deviceIdentifier, deviceKey);
    }

    static SignalServiceEnvelope loadEnvelope(File file) throws IOException {
        try (FileInputStream f = new FileInputStream(file)) {
            DataInputStream in = new DataInputStream(f);
            int version = in.readInt();
            if (version > 4) {
                return null;
            }
            int type = in.readInt();
            String source = in.readUTF();
            UUID sourceUuid = null;
            if (version >= 3) {
                sourceUuid = UuidUtil.parseOrNull(in.readUTF());
            }
            int sourceDevice = in.readInt();
            if (version == 1) {
                // read legacy relay field
                in.readUTF();
            }
            long timestamp = in.readLong();
            byte[] content = null;
            int contentLen = in.readInt();
            if (contentLen > 0) {
                content = new byte[contentLen];
                in.readFully(content);
            }
            byte[] legacyMessage = null;
            int legacyMessageLen = in.readInt();
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
            Optional<SignalServiceAddress> addressOptional = sourceUuid == null && source.isEmpty()
                    ? Optional.absent()
                    : Optional.of(new SignalServiceAddress(sourceUuid, source));
            return new SignalServiceEnvelope(type,
                    addressOptional,
                    sourceDevice,
                    timestamp,
                    legacyMessage,
                    content,
                    serverReceivedTimestamp,
                    serverDeliveredTimestamp,
                    uuid);
        }
    }

    static void storeEnvelope(SignalServiceEnvelope envelope, File file) throws IOException {
        try (FileOutputStream f = new FileOutputStream(file)) {
            try (DataOutputStream out = new DataOutputStream(f)) {
                out.writeInt(4); // version
                out.writeInt(envelope.getType());
                out.writeUTF(envelope.getSourceE164().isPresent() ? envelope.getSourceE164().get() : "");
                out.writeUTF(envelope.getSourceUuid().isPresent() ? envelope.getSourceUuid().get() : "");
                out.writeInt(envelope.getSourceDevice());
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
                String uuid = envelope.getUuid();
                out.writeUTF(uuid == null ? "" : uuid);
                out.writeLong(envelope.getServerDeliveredTimestamp());
            }
        }
    }

    static File retrieveAttachment(SignalServiceAttachmentStream stream, File outputFile) throws IOException {
        InputStream input = stream.getInputStream();

        try (OutputStream output = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[4096];
            int read;

            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        return outputFile;
    }

    static String computeSafetyNumber(
            SignalServiceAddress ownAddress,
            IdentityKey ownIdentityKey,
            SignalServiceAddress theirAddress,
            IdentityKey theirIdentityKey
    ) {
        int version;
        byte[] ownId;
        byte[] theirId;

        if (ServiceConfig.capabilities.isUuid() && ownAddress.getUuid().isPresent() && theirAddress.getUuid()
                .isPresent()) {
            // Version 2: UUID user
            version = 2;
            ownId = UuidUtil.toByteArray(ownAddress.getUuid().get());
            theirId = UuidUtil.toByteArray(theirAddress.getUuid().get());
        } else {
            // Version 1: E164 user
            version = 1;
            if (!ownAddress.getNumber().isPresent() || !theirAddress.getNumber().isPresent()) {
                return "INVALID ID";
            }
            ownId = ownAddress.getNumber().get().getBytes();
            theirId = theirAddress.getNumber().get().getBytes();
        }

        Fingerprint fingerprint = new NumericFingerprintGenerator(5200).createFor(version,
                ownId,
                ownIdentityKey,
                theirId,
                theirIdentityKey);
        return fingerprint.getDisplayableFingerprint().getDisplayText();
    }

    static class DeviceLinkInfo {

        final String deviceIdentifier;
        final ECPublicKey deviceKey;

        DeviceLinkInfo(final String deviceIdentifier, final ECPublicKey deviceKey) {
            this.deviceIdentifier = deviceIdentifier;
            this.deviceKey = deviceKey;
        }
    }
}

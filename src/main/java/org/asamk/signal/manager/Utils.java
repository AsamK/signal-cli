package org.asamk.signal.manager;

import org.apache.http.util.TextUtils;
import org.asamk.signal.AttachmentInvalidException;
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
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.internal.util.Base64;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.*;

class Utils {

    static List<SignalServiceAttachment> getSignalServiceAttachments(List<String> attachments) throws AttachmentInvalidException {
        List<SignalServiceAttachment> SignalServiceAttachments = null;
        if (attachments != null) {
            SignalServiceAttachments = new ArrayList<>(attachments.size());
            for (String attachment : attachments) {
                try {
                    SignalServiceAttachments.add(createAttachment(new File(attachment)));
                } catch (IOException e) {
                    throw new AttachmentInvalidException(attachment, e);
                }
            }
        }
        return SignalServiceAttachments;
    }

    static SignalServiceAttachmentStream createAttachment(File attachmentFile) throws IOException {
        InputStream attachmentStream = new FileInputStream(attachmentFile);
        final long attachmentSize = attachmentFile.length();
        String mime = Files.probeContentType(attachmentFile.toPath());
        if (mime == null) {
            mime = "application/octet-stream";
        }
        // TODO mabybe add a parameter to set the voiceNote, preview, width, height and caption option
        Optional<byte[]> preview = Optional.absent();
        Optional<String> caption = Optional.absent();
        return new SignalServiceAttachmentStream(attachmentStream, mime, attachmentSize, Optional.of(attachmentFile.getName()), false, preview, 0, 0, caption, null);
    }

    static CertificateValidator getCertificateValidator() {
        try {
            ECPublicKey unidentifiedSenderTrustRoot = Curve.decodePoint(Base64.decode(BaseConfig.UNIDENTIFIED_SENDER_TRUST_ROOT), 0);
            return new CertificateValidator(unidentifiedSenderTrustRoot);
        } catch (InvalidKeyException | IOException e) {
            throw new AssertionError(e);
        }
    }

    private static Map<String, String> getQueryMap(String query) {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<>();
        for (String param : params) {
            String name = null;
            final String[] paramParts = param.split("=");
            try {
                name = URLDecoder.decode(paramParts[0], "utf-8");
            } catch (UnsupportedEncodingException e) {
                // Impossible
            }
            String value = null;
            try {
                value = URLDecoder.decode(paramParts[1], "utf-8");
            } catch (UnsupportedEncodingException e) {
                // Impossible
            }
            map.put(name, value);
        }
        return map;
    }

    static String createDeviceLinkUri(DeviceLinkInfo info) {
        try {
            return "tsdevice:/?uuid=" + URLEncoder.encode(info.deviceIdentifier, "utf-8") + "&pub_key=" + URLEncoder.encode(Base64.encodeBytesWithoutPadding(info.deviceKey.serialize()), "utf-8");
        } catch (UnsupportedEncodingException e) {
            // Shouldn't happen
            return null;
        }
    }

    static DeviceLinkInfo parseDeviceLinkUri(URI linkUri) throws IOException, InvalidKeyException {
        Map<String, String> query = getQueryMap(linkUri.getRawQuery());
        String deviceIdentifier = query.get("uuid");
        String publicKeyEncoded = query.get("pub_key");

        if (TextUtils.isEmpty(deviceIdentifier) || TextUtils.isEmpty(publicKeyEncoded)) {
            throw new RuntimeException("Invalid device link uri");
        }

        ECPublicKey deviceKey = Curve.decodePoint(Base64.decode(publicKeyEncoded), 0);

        return new DeviceLinkInfo(deviceIdentifier, deviceKey);
    }

    static Set<SignalServiceAddress> getSignalServiceAddresses(Collection<String> recipients, String localNumber) {
        Set<SignalServiceAddress> recipientsTS = new HashSet<>(recipients.size());
        for (String recipient : recipients) {
            try {
                recipientsTS.add(getPushAddress(recipient, localNumber));
            } catch (InvalidNumberException e) {
                System.err.println("Failed to add recipient \"" + recipient + "\": " + e.getMessage());
                System.err.println("Aborting sending.");
                return null;
            }
        }
        return recipientsTS;
    }

    static String canonicalizeNumber(String number, String localNumber) throws InvalidNumberException {
        return PhoneNumberFormatter.formatNumber(number, localNumber);
    }

    private static SignalServiceAddress getPushAddress(String number, String localNumber) throws InvalidNumberException {
        String e164number = canonicalizeNumber(number, localNumber);
        return new SignalServiceAddress(e164number);
    }

    static SignalServiceEnvelope loadEnvelope(File file) throws IOException {
        try (FileInputStream f = new FileInputStream(file)) {
            DataInputStream in = new DataInputStream(f);
            int version = in.readInt();
            if (version > 2) {
                return null;
            }
            int type = in.readInt();
            String source = in.readUTF();
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
            long serverTimestamp = 0;
            String uuid = null;
            if (version == 2) {
                serverTimestamp = in.readLong();
                uuid = in.readUTF();
                if ("".equals(uuid)) {
                    uuid = null;
                }
            }
            return new SignalServiceEnvelope(type, source, sourceDevice, timestamp, legacyMessage, content, serverTimestamp, uuid);
        }
    }

    static void storeEnvelope(SignalServiceEnvelope envelope, File file) throws IOException {
        try (FileOutputStream f = new FileOutputStream(file)) {
            try (DataOutputStream out = new DataOutputStream(f)) {
                out.writeInt(2); // version
                out.writeInt(envelope.getType());
                out.writeUTF(envelope.getSource());
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
                out.writeLong(envelope.getServerTimestamp());
                String uuid = envelope.getUuid();
                out.writeUTF(uuid == null ? "" : uuid);
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

    static String computeSafetyNumber(String ownUsername, IdentityKey ownIdentityKey, String theirUsername, IdentityKey theirIdentityKey) {
        Fingerprint fingerprint = new NumericFingerprintGenerator(5200).createFor(ownUsername, ownIdentityKey, theirUsername, theirIdentityKey);
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

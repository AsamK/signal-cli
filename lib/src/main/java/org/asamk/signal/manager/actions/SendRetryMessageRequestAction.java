package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.jobs.Context;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.signal.libsignal.metadata.ProtocolException;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.DecryptionErrorMessage;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

public class SendRetryMessageRequestAction implements HandleAction {

    private final RecipientId recipientId;
    private final ProtocolException protocolException;
    private final SignalServiceEnvelope envelope;

    public SendRetryMessageRequestAction(
            final RecipientId recipientId,
            final ProtocolException protocolException,
            final SignalServiceEnvelope envelope
    ) {
        this.recipientId = recipientId;
        this.protocolException = protocolException;
        this.envelope = envelope;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getAccount().getSessionStore().archiveSessions(recipientId);

        int senderDevice = protocolException.getSenderDevice();
        Optional<GroupId> groupId = protocolException.getGroupId().isPresent() ? Optional.of(GroupId.unknownVersion(
                protocolException.getGroupId().get())) : Optional.absent();

        byte[] originalContent;
        int envelopeType;
        if (protocolException.getUnidentifiedSenderMessageContent().isPresent()) {
            final var messageContent = protocolException.getUnidentifiedSenderMessageContent().get();
            originalContent = messageContent.getContent();
            envelopeType = messageContent.getType();
        } else {
            originalContent = envelope.getContent();
            envelopeType = envelopeTypeToCiphertextMessageType(envelope.getType());
        }

        DecryptionErrorMessage decryptionErrorMessage = DecryptionErrorMessage.forOriginalMessage(originalContent,
                envelopeType,
                envelope.getTimestamp(),
                senderDevice);

        context.getSendHelper().sendRetryReceipt(decryptionErrorMessage, recipientId, groupId);
    }

    private static int envelopeTypeToCiphertextMessageType(int envelopeType) {
        switch (envelopeType) {
            case SignalServiceProtos.Envelope.Type.PREKEY_BUNDLE_VALUE:
                return CiphertextMessage.PREKEY_TYPE;
            case SignalServiceProtos.Envelope.Type.UNIDENTIFIED_SENDER_VALUE:
                return CiphertextMessage.SENDERKEY_TYPE;
            case SignalServiceProtos.Envelope.Type.PLAINTEXT_CONTENT_VALUE:
                return CiphertextMessage.PLAINTEXT_CONTENT_TYPE;
            case SignalServiceProtos.Envelope.Type.CIPHERTEXT_VALUE:
            default:
                return CiphertextMessage.WHISPER_TYPE;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final SendRetryMessageRequestAction that = (SendRetryMessageRequestAction) o;

        if (!recipientId.equals(that.recipientId)) return false;
        if (!protocolException.equals(that.protocolException)) return false;
        return envelope.equals(that.envelope);
    }

    @Override
    public int hashCode() {
        int result = recipientId.hashCode();
        result = 31 * result + protocolException.hashCode();
        result = 31 * result + envelope.hashCode();
        return result;
    }
}

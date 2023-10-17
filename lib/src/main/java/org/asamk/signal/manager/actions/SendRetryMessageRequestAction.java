package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.helper.Context;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.signal.libsignal.metadata.ProtocolException;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.message.DecryptionErrorMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.internal.push.Envelope;

import java.util.Optional;

public class SendRetryMessageRequestAction implements HandleAction {

    private final RecipientId recipientId;
    private final ServiceId serviceId;
    private final ProtocolException protocolException;
    private final SignalServiceEnvelope envelope;
    private final ServiceId accountId;

    public SendRetryMessageRequestAction(
            final RecipientId recipientId,
            final ServiceId serviceId,
            final ProtocolException protocolException,
            final SignalServiceEnvelope envelope,
            final ServiceId accountId
    ) {
        this.recipientId = recipientId;
        this.serviceId = serviceId;
        this.protocolException = protocolException;
        this.envelope = envelope;
        this.accountId = accountId;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getAccount().getAccountData(accountId).getSessionStore().archiveSessions(serviceId);

        int senderDevice = protocolException.getSenderDevice();
        Optional<GroupId> groupId = protocolException.getGroupId().isPresent() ? Optional.of(GroupId.unknownVersion(
                protocolException.getGroupId().get())) : Optional.empty();

        byte[] originalContent;
        int envelopeType;
        if (protocolException.getUnidentifiedSenderMessageContent().isPresent()) {
            final var messageContent = protocolException.getUnidentifiedSenderMessageContent().get();
            originalContent = messageContent.getContent();
            envelopeType = messageContent.getType();
        } else {
            originalContent = envelope.getContent();
            envelopeType = envelope.getType() == null
                    ? CiphertextMessage.WHISPER_TYPE
                    : envelopeTypeToCiphertextMessageType(envelope.getType());
        }

        DecryptionErrorMessage decryptionErrorMessage = DecryptionErrorMessage.forOriginalMessage(originalContent,
                envelopeType,
                envelope.getTimestamp(),
                senderDevice);

        context.getSendHelper().sendRetryReceipt(decryptionErrorMessage, recipientId, groupId);
    }

    private static int envelopeTypeToCiphertextMessageType(int envelopeType) {
        final var type = Envelope.Type.fromValue(envelopeType);
        if (type == null) {
            return CiphertextMessage.WHISPER_TYPE;
        }
        return switch (type) {
            case PREKEY_BUNDLE -> CiphertextMessage.PREKEY_TYPE;
            case UNIDENTIFIED_SENDER -> CiphertextMessage.SENDERKEY_TYPE;
            case PLAINTEXT_CONTENT -> CiphertextMessage.PLAINTEXT_CONTENT_TYPE;
            default -> CiphertextMessage.WHISPER_TYPE;
        };
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

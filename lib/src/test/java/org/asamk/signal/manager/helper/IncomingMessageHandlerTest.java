package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.InvalidEnvelopeContentException;
import org.junit.jupiter.api.Test;
import org.signal.core.models.ServiceId.ACI;
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata;
import org.whispersystems.signalservice.api.messages.EnvelopeContentValidator;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.internal.push.BodyRange;
import org.whispersystems.signalservice.internal.push.Content;
import org.whispersystems.signalservice.internal.push.DataMessage;
import org.whispersystems.signalservice.internal.push.EditMessage;
import org.whispersystems.signalservice.internal.push.Envelope;
import org.whispersystems.signalservice.internal.push.SyncMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncomingMessageHandlerTest {

    @Test
    void invalidEnvelopeContentReportsOutOfBoundsBodyRange() {
        final var sender = ACI.parseOrThrow("2a04f0cc-199f-4b93-99d8-13c6b10a70de");
        final var bodyRange = new BodyRange.Builder().start(4).length(3).style(BodyRange.Style.BOLD).build();
        final var dataMessage = new DataMessage.Builder().body("hello").bodyRanges(List.of(bodyRange)).build();
        final var content = new Content.Builder().dataMessage(dataMessage).build();
        final var metadata = new EnvelopeMetadata(sender, null, 2, false, null, sender, 1);
        final var validationResult = new EnvelopeContentValidator.Result.Invalid(
                "[DataMessage] Body range with out-of-bounds start/length!",
                new Throwable());

        final var exception = IncomingMessageHandler.createInvalidEnvelopeContentException(validationResult,
                metadata,
                content);

        assertEquals(InvalidEnvelopeContentException.DATA_MESSAGE_BODY_RANGE_OUT_OF_BOUNDS, exception.getCode());
        assertEquals(sender.toString(), exception.getSender());
        assertEquals(2, exception.getSenderDevice());
        assertEquals(5, exception.getBodyLength());
        assertEquals(List.of(new InvalidEnvelopeContentException.InvalidBodyRange(0, 4, 3, "STYLE_BOLD")),
                exception.getInvalidBodyRanges());
    }

    @Test
    void invalidEnvelopeContentReportsOutOfBoundsBodyRangeFromSyncMessage() {
        final var sender = ACI.parseOrThrow("2a04f0cc-199f-4b93-99d8-13c6b10a70de");
        final var bodyRange = new BodyRange.Builder().start(-1).length(2).mentionAci(sender.toString()).build();
        final var dataMessage = new DataMessage.Builder().body("hello").bodyRanges(List.of(bodyRange)).build();
        final var sent = new SyncMessage.Sent.Builder().message(dataMessage).build();
        final var content = new Content.Builder().syncMessage(new SyncMessage.Builder().sent(sent).build()).build();
        final var metadata = new EnvelopeMetadata(sender, null, 2, false, null, sender, 1);
        final var validationResult = new EnvelopeContentValidator.Result.Invalid(
                "[DataMessage] Body range with out-of-bounds start/length!",
                new Throwable());

        final var exception = IncomingMessageHandler.createInvalidEnvelopeContentException(validationResult,
                metadata,
                content);

        assertEquals(InvalidEnvelopeContentException.DATA_MESSAGE_BODY_RANGE_OUT_OF_BOUNDS, exception.getCode());
        assertEquals(5, exception.getBodyLength());
        assertEquals(List.of(new InvalidEnvelopeContentException.InvalidBodyRange(0, -1, 2, "MENTION")),
                exception.getInvalidBodyRanges());
    }

    @Test
    void invalidEnvelopeContentReportsOutOfBoundsBodyRangeFromEditMessage() {
        final var sender = ACI.parseOrThrow("2a04f0cc-199f-4b93-99d8-13c6b10a70de");
        final var bodyRange = new BodyRange.Builder().start(5).length(1).style(BodyRange.Style.ITALIC).build();
        final var dataMessage = new DataMessage.Builder().body("hello").bodyRanges(List.of(bodyRange)).build();
        final var editMessage = new EditMessage.Builder().targetSentTimestamp(1L).dataMessage(dataMessage).build();
        final var content = new Content.Builder().editMessage(editMessage).build();
        final var metadata = new EnvelopeMetadata(sender, null, 2, false, null, sender, 1);
        final var validationResult = new EnvelopeContentValidator.Result.Invalid(
                "[EditMessage] Body range with out-of-bounds start/length!",
                new Throwable());

        final var exception = IncomingMessageHandler.createInvalidEnvelopeContentException(validationResult,
                metadata,
                content);

        assertEquals(InvalidEnvelopeContentException.DATA_MESSAGE_BODY_RANGE_OUT_OF_BOUNDS, exception.getCode());
        assertEquals(5, exception.getBodyLength());
        assertEquals(List.of(new InvalidEnvelopeContentException.InvalidBodyRange(0, 5, 1, "STYLE_ITALIC")),
                exception.getInvalidBodyRanges());
    }

    @Test
    void invalidEnvelopeContentReportsOutOfBoundsBodyRangeFromQuote() {
        final var sender = ACI.parseOrThrow("2a04f0cc-199f-4b93-99d8-13c6b10a70de");
        final var bodyRange = new BodyRange.Builder().start(2).length(2).style(BodyRange.Style.MONOSPACE).build();
        final var quote = new DataMessage.Quote.Builder().text("hey").bodyRanges(List.of(bodyRange)).build();
        final var dataMessage = new DataMessage.Builder().body("outer body").quote(quote).build();
        final var content = new Content.Builder().dataMessage(dataMessage).build();
        final var metadata = new EnvelopeMetadata(sender, null, 2, false, null, sender, 1);
        final var validationResult = new EnvelopeContentValidator.Result.Invalid(
                "[DataMessage] Quote body range with out-of-bounds start/length!",
                new Throwable());

        final var exception = IncomingMessageHandler.createInvalidEnvelopeContentException(validationResult,
                metadata,
                content);

        assertEquals(InvalidEnvelopeContentException.DATA_MESSAGE_BODY_RANGE_OUT_OF_BOUNDS, exception.getCode());
        assertEquals(3, exception.getBodyLength());
        assertEquals(List.of(new InvalidEnvelopeContentException.InvalidBodyRange(0, 2, 2, "STYLE_MONOSPACE")),
                exception.getInvalidBodyRanges());
    }

    @Test
    void invalidSealedSenderCanBeResolvedForBlocking() {
        final var sender = ACI.parseOrThrow("2a04f0cc-199f-4b93-99d8-13c6b10a70de");
        final var envelope = new SignalServiceEnvelope(new Envelope.Builder()
                .type(Envelope.Type.UNIDENTIFIED_SENDER)
                .clientTimestamp(1L)
                .build(), 2L);
        final var exception = new InvalidEnvelopeContentException("invalid body range",
                InvalidEnvelopeContentException.DATA_MESSAGE_BODY_RANGE_OUT_OF_BOUNDS,
                sender.toString(),
                2,
                5,
                List.of(),
                new Throwable());

        final var source = IncomingMessageHandler.getSenderAddress(envelope, null, exception);

        assertEquals(sender, source.getServiceId());
    }
}

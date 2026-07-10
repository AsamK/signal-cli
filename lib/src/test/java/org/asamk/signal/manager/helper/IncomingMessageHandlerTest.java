package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.InvalidEnvelopeContentException;
import org.junit.jupiter.api.Test;
import org.signal.core.models.ServiceId.ACI;
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata;
import org.whispersystems.signalservice.api.messages.EnvelopeContentValidator;
import org.whispersystems.signalservice.internal.push.BodyRange;
import org.whispersystems.signalservice.internal.push.Content;
import org.whispersystems.signalservice.internal.push.DataMessage;

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
}

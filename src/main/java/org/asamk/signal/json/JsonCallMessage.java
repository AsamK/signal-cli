package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.math.BigInteger;
import java.util.Base64;
import java.util.List;

import static org.asamk.signal.manager.util.Utils.callIdUnsigned;

@JsonSchema(title = "CallMessage")
record JsonCallMessage(
        @JsonInclude(JsonInclude.Include.NON_NULL) Offer offerMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) Answer answerMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) Busy busyMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) Hangup hangupMessage,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<IceUpdate> iceUpdateMessages
) {

    static JsonCallMessage from(MessageEnvelope.Call callMessage) {
        return new JsonCallMessage(callMessage.offer().map(Offer::from).orElse(null),
                callMessage.answer().map(Answer::from).orElse(null),
                callMessage.busy().map(Busy::from).orElse(null),
                callMessage.hangup().map(Hangup::from).orElse(null),
                callMessage.iceUpdate().stream().map(IceUpdate::from).toList());
    }

    record Offer(BigInteger id, String type, String opaque) {

        public static Offer from(final MessageEnvelope.Call.Offer offer) {
            return new Offer(callIdUnsigned(offer.id()),
                    offer.type().name(),
                    Base64.getEncoder().encodeToString(offer.opaque()));
        }
    }

    public record Answer(BigInteger id, String opaque) {

        public static Answer from(final MessageEnvelope.Call.Answer answer) {
            return new Answer(callIdUnsigned(answer.id()), Base64.getEncoder().encodeToString(answer.opaque()));
        }
    }

    public record Busy(BigInteger id) {

        public static Busy from(final MessageEnvelope.Call.Busy busy) {
            return new Busy(callIdUnsigned(busy.id()));
        }
    }

    public record Hangup(BigInteger id, String type, int deviceId) {

        public static Hangup from(final MessageEnvelope.Call.Hangup hangup) {
            return new Hangup(callIdUnsigned(hangup.id()), hangup.type().name(), hangup.deviceId());
        }
    }

    public record IceUpdate(BigInteger id, String opaque) {

        public static IceUpdate from(final MessageEnvelope.Call.IceUpdate iceUpdate) {
            return new IceUpdate(callIdUnsigned(iceUpdate.id()),
                    Base64.getEncoder().encodeToString(iceUpdate.opaque()));
        }
    }
}

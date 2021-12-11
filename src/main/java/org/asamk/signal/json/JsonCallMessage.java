package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.Base64;
import java.util.List;

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

    record Offer(long id, String sdp, String type, String opaque) {

        public static Offer from(final MessageEnvelope.Call.Offer offer) {
            return new Offer(offer.id(),
                    offer.sdp(),
                    offer.type().name(),
                    Base64.getEncoder().encodeToString(offer.opaque()));
        }
    }

    public record Answer(long id, String sdp, String opaque) {

        public static Answer from(final MessageEnvelope.Call.Answer answer) {
            return new Answer(answer.id(), answer.sdp(), Base64.getEncoder().encodeToString(answer.opaque()));
        }
    }

    public record Busy(long id) {

        public static Busy from(final MessageEnvelope.Call.Busy busy) {
            return new Busy(busy.id());
        }
    }

    public record Hangup(long id, String type, int deviceId, boolean isLegacy) {

        public static Hangup from(final MessageEnvelope.Call.Hangup hangup) {
            return new Hangup(hangup.id(), hangup.type().name(), hangup.deviceId(), hangup.isLegacy());
        }
    }

    public record IceUpdate(long id, String sdp, String opaque) {

        public static IceUpdate from(final MessageEnvelope.Call.IceUpdate iceUpdate) {
            return new IceUpdate(iceUpdate.id(),
                    iceUpdate.sdp(),
                    Base64.getEncoder().encodeToString(iceUpdate.opaque()));
        }
    }
}

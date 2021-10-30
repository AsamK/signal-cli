package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;

import java.util.List;

record JsonCallMessage(
        @JsonInclude(JsonInclude.Include.NON_NULL) OfferMessage offerMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) AnswerMessage answerMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) BusyMessage busyMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) HangupMessage hangupMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<IceUpdateMessage> iceUpdateMessages
) {

    static JsonCallMessage from(SignalServiceCallMessage callMessage) {
        return new JsonCallMessage(callMessage.getOfferMessage().orNull(),
                callMessage.getAnswerMessage().orNull(),
                callMessage.getBusyMessage().orNull(),
                callMessage.getHangupMessage().orNull(),
                callMessage.getIceUpdateMessages().orNull());
    }
}

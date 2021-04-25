package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;

import java.util.List;

class JsonCallMessage {

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final OfferMessage offerMessage;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final AnswerMessage answerMessage;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final BusyMessage busyMessage;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final HangupMessage hangupMessage;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final List<IceUpdateMessage> iceUpdateMessages;

    JsonCallMessage(SignalServiceCallMessage callMessage) {
        this.offerMessage = callMessage.getOfferMessage().orNull();
        this.answerMessage = callMessage.getAnswerMessage().orNull();
        this.busyMessage = callMessage.getBusyMessage().orNull();
        this.hangupMessage = callMessage.getHangupMessage().orNull();
        this.iceUpdateMessages = callMessage.getIceUpdateMessages().orNull();
    }
}

package org.asamk.signal;

import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;

import java.util.List;

class JsonCallMessage {

    OfferMessage offerMessage;
    AnswerMessage answerMessage;
    BusyMessage busyMessage;
    HangupMessage hangupMessage;
    List<IceUpdateMessage> iceUpdateMessages;

    JsonCallMessage(SignalServiceCallMessage callMessage) {
        if (callMessage.getOfferMessage().isPresent()) {
            this.offerMessage = callMessage.getOfferMessage().get();
        }
        if (callMessage.getAnswerMessage().isPresent()) {
            this.answerMessage = callMessage.getAnswerMessage().get();
        }
        if (callMessage.getBusyMessage().isPresent()) {
            this.busyMessage = callMessage.getBusyMessage().get();
        }
        if (callMessage.getHangupMessage().isPresent()) {
            this.hangupMessage = callMessage.getHangupMessage().get();
        }
        if (callMessage.getIceUpdateMessages().isPresent()) {
            this.iceUpdateMessages = callMessage.getIceUpdateMessages().get();
        }
    }
}

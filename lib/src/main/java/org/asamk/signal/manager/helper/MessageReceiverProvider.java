package org.asamk.signal.manager.helper;

import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

public interface MessageReceiverProvider {

    SignalServiceMessageReceiver getMessageReceiver();
}

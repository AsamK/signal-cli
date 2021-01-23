package org.asamk.signal.manager.helper;

import org.whispersystems.signalservice.api.SignalServiceMessagePipe;

public interface MessagePipeProvider {

    SignalServiceMessagePipe getMessagePipe(boolean unidentified);
}

package org.asamk.signal.json;

import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

record JsonRemoteDelete(long timestamp) {

    static JsonRemoteDelete from(SignalServiceDataMessage.RemoteDelete remoteDelete) {
        return new JsonRemoteDelete(remoteDelete.getTargetSentTimestamp());
    }
}

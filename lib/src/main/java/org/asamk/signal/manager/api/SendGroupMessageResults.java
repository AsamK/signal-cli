package org.asamk.signal.manager.api;

import org.whispersystems.signalservice.api.messages.SendMessageResult;

import java.util.List;

public class SendGroupMessageResults {

    private final long timestamp;
    private final List<SendMessageResult> results;

    public SendGroupMessageResults(
            final long timestamp, final List<SendMessageResult> results
    ) {
        this.timestamp = timestamp;
        this.results = results;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<SendMessageResult> getResults() {
        return results;
    }
}

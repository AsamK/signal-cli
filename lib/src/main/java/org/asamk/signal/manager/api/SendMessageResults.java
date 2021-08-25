package org.asamk.signal.manager.api;

import org.whispersystems.signalservice.api.messages.SendMessageResult;

import java.util.List;
import java.util.Map;

public class SendMessageResults {

    private final long timestamp;
    private final Map<RecipientIdentifier, List<SendMessageResult>> results;

    public SendMessageResults(
            final long timestamp, final Map<RecipientIdentifier, List<SendMessageResult>> results
    ) {
        this.timestamp = timestamp;
        this.results = results;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Map<RecipientIdentifier, List<SendMessageResult>> getResults() {
        return results;
    }
}

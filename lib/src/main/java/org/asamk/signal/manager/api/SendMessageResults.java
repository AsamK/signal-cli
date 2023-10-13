package org.asamk.signal.manager.api;

import java.util.List;
import java.util.Map;

public record SendMessageResults(long timestamp, Map<RecipientIdentifier, List<SendMessageResult>> results) {

    public boolean hasSuccess() {
        return results.values()
                .stream()
                .flatMap(res -> res.stream().map(SendMessageResult::isSuccess))
                .anyMatch(success -> success) || results.values().stream().mapToInt(List::size).sum() == 0;
    }

    public boolean hasOnlyUntrustedIdentity() {
        return results.values()
                .stream()
                .flatMap(res -> res.stream().map(SendMessageResult::isIdentityFailure))
                .allMatch(identityFailure -> identityFailure)
                && results.values().stream().mapToInt(List::size).sum() > 0;
    }

    public boolean hasOnlyRateLimitFailure() {
        return results.values()
                .stream()
                .flatMap(res -> res.stream().map(SendMessageResult::isRateLimitFailure))
                .allMatch(r -> r) && results.values().stream().mapToInt(List::size).sum() > 0;
    }
}

package org.asamk.signal.manager.api;

import java.util.List;
import java.util.Map;

public record SendMessageResults(long timestamp, Map<RecipientIdentifier, List<SendMessageResult>> results) {}

package org.asamk.signal.manager.api;

import java.util.List;

public record SendGroupMessageResults(long timestamp, List<SendMessageResult> results) {}

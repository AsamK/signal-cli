package org.asamk.signal.manager.api;

import org.whispersystems.signalservice.api.messages.SendMessageResult;

import java.util.List;

public record SendGroupMessageResults(long timestamp, List<SendMessageResult> results) {}

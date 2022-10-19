package org.asamk.signal.manager.api;

public record ReceiveConfig(boolean ignoreAttachments, boolean ignoreStories, boolean sendReadReceipts) {}

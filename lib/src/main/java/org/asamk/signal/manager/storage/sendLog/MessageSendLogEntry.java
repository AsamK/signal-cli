package org.asamk.signal.manager.storage.sendLog;

import org.asamk.signal.manager.groups.GroupId;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.util.Optional;

public record MessageSendLogEntry(
        Optional<GroupId> groupId, SignalServiceProtos.Content content, ContentHint contentHint, boolean urgent
) {}

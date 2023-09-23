package org.asamk.signal.manager.storage.sendLog;

import org.asamk.signal.manager.api.GroupId;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.internal.push.Content;

import java.util.Optional;

public record MessageSendLogEntry(
        Optional<GroupId> groupId, Content content, ContentHint contentHint, boolean urgent
) {}

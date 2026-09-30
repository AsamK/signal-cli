package org.asamk.signal.manager.storage.notificationProfiles;

import org.whispersystems.signalservice.api.storage.StorageId;

/**
 * Local copy of a notification profile as synced via storage service.
 * The full profile data is kept in the raw storage record, the remaining fields are
 * denormalized for querying.
 */
public record NotificationProfile(
        long internalId,
        byte[] profileId,
        String name,
        long deletedTimestamp,
        StorageId storageId,
        byte[] storageRecord
) {}

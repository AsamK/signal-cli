package org.asamk.signal.manager.storage.notificationProfiles;

import org.asamk.signal.manager.storage.AccountDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.whispersystems.signalservice.api.storage.SignalNotificationProfileRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.internal.storage.protos.NotificationProfile;

import java.io.File;
import java.util.List;

import okio.ByteString;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationProfileStoreTest {

    @TempDir
    File tempDir;

    private AccountDatabase database;
    private NotificationProfileStore store;

    @BeforeEach
    void setUp() throws Exception {
        database = AccountDatabase.init(new File(tempDir, "account.db"));
        store = new NotificationProfileStore(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private static SignalNotificationProfileRecord record(byte[] id, String name, long deletedAt, byte[] storageId) {
        return new SignalNotificationProfileRecord(StorageId.forNotificationProfile(storageId),
                new NotificationProfile.Builder().id(ByteString.of(id))
                        .name(name)
                        .emoji("🌙")
                        .deletedAtTimestampMs(deletedAt)
                        .build());
    }

    private static byte[] bytes(int seed) {
        final var result = new byte[16];
        result[0] = (byte) seed;
        return result;
    }

    @Test
    void upsertInsertsAndUpdates() throws Exception {
        final var id = bytes(1);
        try (final var connection = database.getConnection()) {
            store.upsertFromStorageSync(connection, record(id, "Sleep", 0, bytes(10)));

            final var inserted = store.getNotificationProfile(connection, id);
            assertNotNull(inserted);
            assertEquals("Sleep", inserted.name());
            assertEquals(0, inserted.deletedTimestamp());
            assertArrayEquals(bytes(10), inserted.storageId().getRaw());
            assertEquals("Sleep", NotificationProfile.ADAPTER.decode(inserted.storageRecord()).name);

            store.upsertFromStorageSync(connection, record(id, "Sleep 2", 42, bytes(11)));

            final var all = store.getNotificationProfiles(connection);
            assertEquals(1, all.size());
            final var updated = all.getFirst();
            assertEquals(inserted.internalId(), updated.internalId());
            assertEquals("Sleep 2", updated.name());
            assertEquals(42, updated.deletedTimestamp());
            assertArrayEquals(bytes(11), updated.storageId().getRaw());

            assertNotNull(store.getNotificationProfile(connection, StorageId.forNotificationProfile(bytes(11))));
            assertNull(store.getNotificationProfile(connection, StorageId.forNotificationProfile(bytes(10))));
            assertEquals(List.of(StorageId.forNotificationProfile(bytes(11))), store.getStorageIds(connection));
        }
    }

    @Test
    void removesOnlyDeletedLocalOnlyProfiles() throws Exception {
        try (final var connection = database.getConnection()) {
            store.upsertFromStorageSync(connection, record(bytes(1), "Active", 0, bytes(10)));
            store.upsertFromStorageSync(connection, record(bytes(2), "Deleted", 42, bytes(11)));

            final var removed = store.removeLocalOnlyDeletedNotificationProfiles(connection,
                    List.of(StorageId.forNotificationProfile(bytes(10)), StorageId.forNotificationProfile(bytes(11))));

            assertEquals(1, removed);
            assertNotNull(store.getNotificationProfile(connection, bytes(1)));
            assertNull(store.getNotificationProfile(connection, bytes(2)));
        }
    }

    @Test
    void updatesStorageIds() throws Exception {
        try (final var connection = database.getConnection()) {
            store.upsertFromStorageSync(connection, record(bytes(1), "A", 0, bytes(10)));
            store.upsertFromStorageSync(connection, record(bytes(2), "B", 0, bytes(11)));
            final var profiles = store.getNotificationProfiles(connection);

            store.updateStorageIds(connection,
                    java.util.Map.of(profiles.get(0).internalId(),
                            StorageId.forNotificationProfile(bytes(20)),
                            profiles.get(1).internalId(),
                            StorageId.forNotificationProfile(bytes(21))));

            assertArrayEquals(bytes(20), store.getNotificationProfile(connection, bytes(1)).storageId().getRaw());
            assertArrayEquals(bytes(21), store.getNotificationProfile(connection, bytes(2)).storageId().getRaw());
        }
    }
}

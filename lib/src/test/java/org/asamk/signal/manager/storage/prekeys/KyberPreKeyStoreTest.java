package org.asamk.signal.manager.storage.prekeys;

import org.asamk.signal.manager.storage.AccountDatabase;
import org.asamk.signal.manager.storage.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.ReusedBaseKeyException;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.kem.KEMKeyPair;
import org.signal.libsignal.protocol.kem.KEMKeyType;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.whispersystems.signalservice.api.push.ServiceIdType;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.asamk.signal.manager.config.ServiceConfig.PREKEY_ARCHIVE_AGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KyberPreKeyStoreTest {

    private static final int SIGNED_PRE_KEY_ID = 7;

    @TempDir
    Path tempDir;

    @Test
    void oneTimeKyberPreKeyIsDeletedWhenMarkedUsed() throws Exception {
        try (var database = newDatabase()) {
            final var store = new KyberPreKeyStore(database, ServiceIdType.ACI);
            final var baseKey = baseKey();
            store.storeKyberPreKey(1, kyberRecord(1, 1_000L));

            store.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey);

            assertFalse(store.containsKyberPreKey(1));
            store.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey);
            assertFalse(store.containsKyberPreKey(1));
        }
    }

    @Test
    void lastResortKyberPreKeyIsKeptWhenMarkedUsed() throws Exception {
        try (var database = newDatabase()) {
            final var store = new KyberPreKeyStore(database, ServiceIdType.ACI);
            store.storeLastResortKyberPreKey(1, kyberRecord(1, 1_000L));

            store.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey());

            assertTrue(store.containsKyberPreKey(1));
        }
    }

    @Test
    void repeatedLastResortTupleThrowsReusedBaseKeyException() throws Exception {
        try (var database = newDatabase()) {
            final var store = new KyberPreKeyStore(database, ServiceIdType.ACI);
            final var baseKey = baseKey();
            store.storeLastResortKyberPreKey(1, kyberRecord(1, 1_000L));

            store.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey);

            assertTrue(store.containsKyberPreKey(1));
            assertThrows(ReusedBaseKeyException.class,
                    () -> store.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, new ECPublicKey(baseKey.serialize())));
            assertTrue(store.containsKyberPreKey(1));
            assertThrows(ReusedBaseKeyException.class,
                    () -> store.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey));
        }
    }

    @Test
    void differentTuplePartsDoNotThrow() throws Exception {
        try (var database = newDatabase()) {
            final var store = new KyberPreKeyStore(database, ServiceIdType.ACI);
            final var baseKey = baseKey();
            final var otherBaseKey = baseKey();
            store.storeLastResortKyberPreKey(1, kyberRecord(1, 1_000L));
            store.storeLastResortKyberPreKey(2, kyberRecord(2, 1_000L));

            store.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey);
            store.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, otherBaseKey);
            store.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID + 1, baseKey);
            store.markKyberPreKeyUsed(2, SIGNED_PRE_KEY_ID, baseKey);

            assertTrue(store.containsKyberPreKey(1));
            assertTrue(store.containsKyberPreKey(2));
            assertThrows(ReusedBaseKeyException.class,
                    () -> store.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, otherBaseKey));
        }
    }

    @Test
    void usedTuplesAreScopedByAccountIdType() throws Exception {
        try (var database = newDatabase()) {
            final var aci = new KyberPreKeyStore(database, ServiceIdType.ACI);
            final var pni = new KyberPreKeyStore(database, ServiceIdType.PNI);
            final var baseKey = baseKey();
            aci.storeLastResortKyberPreKey(1, kyberRecord(1, 1_000L));
            pni.storeLastResortKyberPreKey(1, kyberRecord(1, 1_000L));

            aci.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey);
            pni.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey);

            assertThrows(ReusedBaseKeyException.class,
                    () -> aci.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey));
            assertThrows(ReusedBaseKeyException.class,
                    () -> pni.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey));
            assertTrue(aci.containsKyberPreKey(1));
            assertTrue(pni.containsKyberPreKey(1));
        }
    }

    @Test
    void unknownKyberPreKeyIdIsIgnored() throws Exception {
        try (var database = newDatabase()) {
            final var store = new KyberPreKeyStore(database, ServiceIdType.ACI);

            store.markKyberPreKeyUsed(99, SIGNED_PRE_KEY_ID, baseKey());

            assertFalse(store.containsKyberPreKey(99));
        }
    }

    @Test
    void usedTuplesAreRemovedWithTheKyberPreKey() throws Exception {
        try (var database = newDatabase()) {
            final var store = new KyberPreKeyStore(database, ServiceIdType.ACI);
            final var baseKey = baseKey();
            final var removed = kyberRecord(1, 1_000L);
            store.storeLastResortKyberPreKey(1, removed);
            store.storeLastResortKyberPreKey(2, kyberRecord(2, 1_000L));
            store.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey);
            store.markKyberPreKeyUsed(2, SIGNED_PRE_KEY_ID, baseKey);

            store.removeKyberPreKey(1);

            assertFalse(store.containsKyberPreKey(1));
            store.storeLastResortKyberPreKey(1, removed);
            store.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey);
            assertThrows(ReusedBaseKeyException.class,
                    () -> store.markKyberPreKeyUsed(2, SIGNED_PRE_KEY_ID, baseKey));
        }
    }

    @Test
    void usedTuplesAreRemovedWithAllKyberPreKeysForThatAccount() throws Exception {
        try (var database = newDatabase()) {
            final var aci = new KyberPreKeyStore(database, ServiceIdType.ACI);
            final var pni = new KyberPreKeyStore(database, ServiceIdType.PNI);
            final var baseKey = baseKey();
            final var aciRecord = kyberRecord(1, 1_000L);
            aci.storeLastResortKyberPreKey(1, aciRecord);
            pni.storeLastResortKyberPreKey(1, kyberRecord(1, 1_000L));
            aci.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey);
            pni.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey);

            aci.removeAllKyberPreKeys();

            assertFalse(aci.containsKyberPreKey(1));
            assertTrue(pni.containsKyberPreKey(1));
            aci.storeLastResortKyberPreKey(1, aciRecord);
            aci.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey);
            assertThrows(ReusedBaseKeyException.class,
                    () -> pni.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey));
        }
    }

    @Test
    void usedTuplesAreRemovedWithOldLastResortKyberPreKeys() throws Exception {
        try (var database = newDatabase()) {
            final var store = new KyberPreKeyStore(database, ServiceIdType.ACI);
            final var baseKey = baseKey();
            final var now = System.currentTimeMillis();
            final var removed = kyberRecord(3, now - PREKEY_ARCHIVE_AGE - 10 * 86_400_000L);
            store.storeLastResortKyberPreKey(1, kyberRecord(1, now));
            store.storeLastResortKyberPreKey(2, kyberRecord(2, now - PREKEY_ARCHIVE_AGE - 86_400_000L));
            store.storeLastResortKyberPreKey(3, removed);
            store.markKyberPreKeyUsed(2, SIGNED_PRE_KEY_ID, baseKey);
            store.markKyberPreKeyUsed(3, SIGNED_PRE_KEY_ID, baseKey);

            store.removeOldLastResortKyberPreKeys(1);

            assertTrue(store.containsKyberPreKey(1));
            assertTrue(store.containsKyberPreKey(2));
            assertFalse(store.containsKyberPreKey(3));
            store.storeLastResortKyberPreKey(3, removed);
            store.markKyberPreKeyUsed(3, SIGNED_PRE_KEY_ID, baseKey);
            assertThrows(ReusedBaseKeyException.class,
                    () -> store.markKyberPreKeyUsed(2, SIGNED_PRE_KEY_ID, baseKey));
        }
    }

    @Test
    void usedTuplesAreRemovedWithStaleOneTimeKyberPreKeys() throws Exception {
        try (var database = newDatabase()) {
            final var store = new KyberPreKeyStore(database, ServiceIdType.ACI);
            final var baseKey = baseKey();
            final var record = kyberRecord(1, 1_000L);
            store.storeLastResortKyberPreKey(1, record);
            store.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey);
            // The stale deleter only selects one-time rows. Flip this used row so that deleter is the path under test.
            try (var connection = database.getConnection();
                 var statement = connection.prepareStatement("""
                                                             UPDATE kyber_pre_key
                                                             SET is_last_resort = FALSE, stale_timestamp = ?
                                                             WHERE account_id_type = ? AND key_id = ?
                                                             """)) {
                statement.setLong(1, 1L);
                statement.setInt(2, Utils.getAccountIdType(ServiceIdType.ACI));
                statement.setInt(3, 1);
                assertEquals(1, statement.executeUpdate());
            }

            store.deleteAllStaleOneTimeKyberPreKeys(10L, 0);

            assertFalse(store.containsKyberPreKey(1));
            store.storeLastResortKyberPreKey(1, record);
            store.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey);
        }
    }

    @Test
    void migrationFromPreviousVersionCreatesUsedTable() throws Exception {
        final var previousVersion = 32;
        final var file = tempDir.resolve("previous.db").toFile();
        final var record = kyberRecord(4, 1_000L);
        final var baseKey = baseKey();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath())) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                                        CREATE TABLE kyber_pre_key (
                                          _id INTEGER PRIMARY KEY,
                                          account_id_type INTEGER NOT NULL,
                                          key_id INTEGER NOT NULL,
                                          serialized BLOB NOT NULL,
                                          is_last_resort INTEGER NOT NULL,
                                          stale_timestamp INTEGER,
                                          timestamp INTEGER DEFAULT 0,
                                          UNIQUE(account_id_type, key_id)
                                        ) STRICT;
                                        """);
            }
            try (var statement = connection.prepareStatement("""
                                                             INSERT INTO kyber_pre_key (account_id_type, key_id, serialized, is_last_resort, timestamp)
                                                             VALUES (?, ?, ?, ?, ?)
                                                             """)) {
                statement.setInt(1, Utils.getAccountIdType(ServiceIdType.ACI));
                statement.setInt(2, 4);
                statement.setBytes(3, record.serialize());
                statement.setBoolean(4, true);
                statement.setLong(5, record.getTimestamp());
                statement.executeUpdate();
            }
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("PRAGMA user_version = " + previousVersion);
            }
        }

        try (var database = AccountDatabase.init(file)) {
            try (var connection = database.getConnection();
                 var statement = connection.createStatement();
                 var version = statement.executeQuery("PRAGMA user_version")) {
                assertTrue(version.next());
                assertEquals(33L, version.getLong(1));
            }
            try (var connection = database.getConnection();
                 var statement = connection.createStatement();
                 var foreignKeys = statement.executeQuery("PRAGMA foreign_key_list(kyber_pre_key_used)")) {
                assertTrue(foreignKeys.next());
                assertEquals("kyber_pre_key", foreignKeys.getString("table"));
                assertEquals("CASCADE", foreignKeys.getString("on_delete"));
            }

            final var store = new KyberPreKeyStore(database, ServiceIdType.ACI);
            assertTrue(store.containsKyberPreKey(4));
            store.markKyberPreKeyUsed(4, SIGNED_PRE_KEY_ID, baseKey);
            assertTrue(store.containsKyberPreKey(4));
            assertThrows(ReusedBaseKeyException.class,
                    () -> store.markKyberPreKeyUsed(4, SIGNED_PRE_KEY_ID, baseKey));
        }
    }

    @Test
    void concurrentSameTupleAllowsOneCaller() throws Exception {
        try (var database = newDatabase()) {
            final var store = new KyberPreKeyStore(database, ServiceIdType.ACI);
            final var baseKey = baseKey();
            store.storeLastResortKyberPreKey(1, kyberRecord(1, 1_000L));
            final var start = new CountDownLatch(1);
            final var done = new CountDownLatch(2);
            final var successes = new AtomicInteger();
            final var reuses = new AtomicInteger();
            final var failure = new AtomicReference<Throwable>();

            for (var i = 0; i < 2; i++) {
                Thread.ofPlatform().start(() -> {
                    try {
                        start.await();
                        store.markKyberPreKeyUsed(1, SIGNED_PRE_KEY_ID, baseKey);
                        successes.incrementAndGet();
                    } catch (ReusedBaseKeyException e) {
                        reuses.incrementAndGet();
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            done.await();

            assertTrue(failure.get() == null, () -> String.valueOf(failure.get()));
            assertEquals(1, successes.get());
            assertEquals(1, reuses.get());
            assertTrue(store.containsKyberPreKey(1));
        }
    }

    private AccountDatabase newDatabase() throws SQLException {
        return AccountDatabase.init(tempDir.resolve("account.db").toFile());
    }

    private static ECPublicKey baseKey() {
        return ECKeyPair.generate().getPublicKey();
    }

    private static KyberPreKeyRecord kyberRecord(final int id, final long timestamp) {
        final var identityKeyPair = IdentityKeyPair.generate();
        final var keyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024);
        final var signature = identityKeyPair.getPrivateKey().calculateSignature(keyPair.getPublicKey().serialize());
        return new KyberPreKeyRecord(id, timestamp, keyPair, signature);
    }
}

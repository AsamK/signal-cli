package org.asamk.signal.manager.storage.recipients;

import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class CdsiStore {

    private static final String TABLE_CDSI = "cdsi";

    private final Database database;

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("""
                                    CREATE TABLE cdsi (
                                      _id INTEGER PRIMARY KEY,
                                      number TEXT NOT NULL UNIQUE,
                                      last_seen_at INTEGER NOT NULL
                                    ) STRICT;
                                    """);
        }
    }

    public CdsiStore(final Database database) {
        this.database = database;
    }

    public Set<String> getAllNumbers() {
        try (final var connection = database.getConnection()) {
            return getAllNumbers(connection);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from cdsi store", e);
        }
    }

    /**
     * Saves the set of e164 numbers used after a full refresh.
     *
     * @param fullNumbers All the e164 numbers used in the last CDS query (previous and new).
     * @param seenNumbers The E164 numbers that were seen in either the system contacts or recipients table. This is different from fullNumbers in that fullNumbers
     *                    includes every number we've ever seen, even if it's not in our contacts anymore.
     */
    public void updateAfterFullCdsQuery(Set<String> fullNumbers, Set<String> seenNumbers) {
        final var lastSeen = System.currentTimeMillis();
        try (final var connection = database.getConnection()) {
            final var existingNumbers = getAllNumbers(connection);

            final var removedNumbers = new HashSet<>(existingNumbers) {{
                removeAll(fullNumbers);
            }};
            removeNumbers(connection, removedNumbers);

            final var addedNumbers = new HashSet<>(fullNumbers) {{
                removeAll(existingNumbers);
            }};
            addNumbers(connection, addedNumbers, lastSeen);

            updateLastSeen(connection, seenNumbers, lastSeen);
        } catch (SQLException e) {
            throw new RuntimeException("Failed update cdsi store", e);
        }
    }

    /**
     * Updates after a partial CDS query. Will not insert new entries.
     * Instead, this will simply update the lastSeen timestamp of any entry we already have.
     *
     * @param seenNumbers The newly-added E164 numbers that we hadn't previously queried for.
     */
    public void updateAfterPartialCdsQuery(Set<String> seenNumbers) {
        final var lastSeen = System.currentTimeMillis();

        try (final var connection = database.getConnection()) {
            updateLastSeen(connection, seenNumbers, lastSeen);
        } catch (SQLException e) {
            throw new RuntimeException("Failed update cdsi store", e);
        }
    }

    private static Set<String> getAllNumbers(final Connection connection) throws SQLException {
        final var sql = (
                """
                SELECT c.number
                FROM %s c
                """
        ).formatted(TABLE_CDSI);
        try (final var statement = connection.prepareStatement(sql)) {
            try (var result = Utils.executeQueryForStream(statement, r -> r.getString("number"))) {
                return result.collect(Collectors.toSet());
            }
        }
    }

    private static void removeNumbers(
            final Connection connection, final Set<String> numbers
    ) throws SQLException {
        final var sql = (
                """
                DELETE FROM %s
                WHERE number = ?
                """
        ).formatted(TABLE_CDSI);
        try (final var statement = connection.prepareStatement(sql)) {
            for (final var number : numbers) {
                statement.setString(1, number);
                statement.executeUpdate();
            }
        }
    }

    private static void addNumbers(
            final Connection connection, final Set<String> numbers, final long lastSeen
    ) throws SQLException {
        final var sql = (
                """
                INSERT INTO %s (number, last_seen_at)
                VALUES (?, ?)
                ON CONFLICT (number) DO UPDATE SET last_seen_at = excluded.last_seen_at
                """
        ).formatted(TABLE_CDSI);
        try (final var statement = connection.prepareStatement(sql)) {
            for (final var number : numbers) {
                statement.setString(1, number);
                statement.setLong(2, lastSeen);
                statement.executeUpdate();
            }
        }
    }

    private static void updateLastSeen(
            final Connection connection, final Set<String> numbers, final long lastSeen
    ) throws SQLException {
        final var sql = (
                """
                UPDATE %s
                SET last_seen_at = ?
                WHERE number = ?
                """
        ).formatted(TABLE_CDSI);
        try (final var statement = connection.prepareStatement(sql)) {
            for (final var number : numbers) {
                statement.setLong(1, lastSeen);
                statement.setString(2, number);
                statement.executeUpdate();
            }
        }
    }

    public void clearAll() {
        final var sql = (
                """
                TRUNCATE %s
                """
        ).formatted(TABLE_CDSI);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update cdsi store", e);
        }
    }
}

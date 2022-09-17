package org.asamk.signal.manager.storage.recipients;

import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.asamk.signal.manager.storage.contacts.ContactsStore;
import org.asamk.signal.manager.storage.profiles.ProfileStore;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class RecipientStore implements RecipientIdCreator, RecipientResolver, RecipientTrustedResolver, ContactsStore, ProfileStore {

    private final static Logger logger = LoggerFactory.getLogger(RecipientStore.class);
    private static final String TABLE_RECIPIENT = "recipient";
    private static final String SQL_IS_CONTACT = "r.given_name IS NOT NULL OR r.family_name IS NOT NULL OR r.expiration_time > 0 OR r.profile_sharing = TRUE OR r.color IS NOT NULL OR r.blocked = TRUE OR r.archived = TRUE";

    private final RecipientMergeHandler recipientMergeHandler;
    private final SelfAddressProvider selfAddressProvider;
    private final Database database;

    private final Object recipientsLock = new Object();
    private final Map<Long, Long> recipientsMerged = new HashMap<>();

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("""
                                    CREATE TABLE recipient (
                                      _id INTEGER PRIMARY KEY AUTOINCREMENT,
                                      number TEXT UNIQUE,
                                      uuid BLOB UNIQUE,
                                      profile_key BLOB,
                                      profile_key_credential BLOB,

                                      given_name TEXT,
                                      family_name TEXT,
                                      color TEXT,

                                      expiration_time INTEGER NOT NULL DEFAULT 0,
                                      blocked INTEGER NOT NULL DEFAULT FALSE,
                                      archived INTEGER NOT NULL DEFAULT FALSE,
                                      profile_sharing INTEGER NOT NULL DEFAULT FALSE,

                                      profile_last_update_timestamp INTEGER NOT NULL DEFAULT 0,
                                      profile_given_name TEXT,
                                      profile_family_name TEXT,
                                      profile_about TEXT,
                                      profile_about_emoji TEXT,
                                      profile_avatar_url_path TEXT,
                                      profile_mobile_coin_address BLOB,
                                      profile_unidentified_access_mode TEXT,
                                      profile_capabilities TEXT
                                    ) STRICT;
                                    """);
        }
    }

    public RecipientStore(
            final RecipientMergeHandler recipientMergeHandler,
            final SelfAddressProvider selfAddressProvider,
            final Database database
    ) {
        this.recipientMergeHandler = recipientMergeHandler;
        this.selfAddressProvider = selfAddressProvider;
        this.database = database;
    }

    public RecipientAddress resolveRecipientAddress(RecipientId recipientId) {
        final var sql = (
                """
                SELECT r.number, r.uuid
                FROM %s r
                WHERE r._id = ?
                """
        ).formatted(TABLE_RECIPIENT);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, recipientId.id());
                return Utils.executeQuerySingleRow(statement, this::getRecipientAddressFromResultSet);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from recipient store", e);
        }
    }

    public Collection<RecipientId> getRecipientIdsWithEnabledProfileSharing() {
        final var sql = (
                """
                SELECT r._id
                FROM %s r
                WHERE r.blocked = FALSE AND r.profile_sharing = TRUE
                """
        ).formatted(TABLE_RECIPIENT);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                try (var result = Utils.executeQueryForStream(statement, this::getRecipientIdFromResultSet)) {
                    return result.toList();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from recipient store", e);
        }
    }

    @Override
    public RecipientId resolveRecipient(final long rawRecipientId) {
        final var sql = (
                """
                SELECT r._id
                FROM %s r
                WHERE r._id = ?
                """
        ).formatted(TABLE_RECIPIENT);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, rawRecipientId);
                return Utils.executeQueryForOptional(statement, this::getRecipientIdFromResultSet).orElse(null);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from recipient store", e);
        }
    }

    /**
     * Should only be used for recipientIds from the database.
     * Where the foreign key relations ensure a valid recipientId.
     */
    @Override
    public RecipientId create(final long recipientId) {
        return new RecipientId(recipientId, this);
    }

    public RecipientId resolveRecipient(
            final String number, Supplier<ACI> aciSupplier
    ) throws UnregisteredRecipientException {
        final Optional<RecipientWithAddress> byNumber;
        try (final var connection = database.getConnection()) {
            byNumber = findByNumber(connection, number);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from recipient store", e);
        }
        if (byNumber.isEmpty() || byNumber.get().address().uuid().isEmpty()) {
            final var aci = aciSupplier.get();
            if (aci == null) {
                throw new UnregisteredRecipientException(new RecipientAddress(null, number));
            }

            return resolveRecipient(new RecipientAddress(aci.uuid(), number), false, false);
        }
        return byNumber.get().id();
    }

    public RecipientId resolveRecipient(RecipientAddress address) {
        return resolveRecipient(address, false, false);
    }

    @Override
    public RecipientId resolveSelfRecipientTrusted(RecipientAddress address) {
        return resolveRecipient(address, true, true);
    }

    public RecipientId resolveRecipientTrusted(RecipientAddress address) {
        return resolveRecipient(address, true, false);
    }

    @Override
    public RecipientId resolveRecipientTrusted(SignalServiceAddress address) {
        return resolveRecipient(new RecipientAddress(address), true, false);
    }

    @Override
    public void storeContact(RecipientId recipientId, final Contact contact) {
        try (final var connection = database.getConnection()) {
            storeContact(connection, recipientId, contact);
        } catch (SQLException e) {
            throw new RuntimeException("Failed update recipient store", e);
        }
    }

    @Override
    public Contact getContact(RecipientId recipientId) {
        try (final var connection = database.getConnection()) {
            return getContact(connection, recipientId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from recipient store", e);
        }
    }

    @Override
    public List<Pair<RecipientId, Contact>> getContacts() {
        final var sql = (
                """
                SELECT r._id, r.given_name, r.family_name, r.expiration_time, r.profile_sharing, r.color, r.blocked, r.archived
                FROM %s r
                WHERE (r.number IS NOT NULL OR r.uuid IS NOT NULL) AND %s
                """
        ).formatted(TABLE_RECIPIENT, SQL_IS_CONTACT);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                try (var result = Utils.executeQueryForStream(statement,
                        resultSet -> new Pair<>(getRecipientIdFromResultSet(resultSet),
                                getContactFromResultSet(resultSet)))) {
                    return result.toList();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from recipient store", e);
        }
    }

    public List<Recipient> getRecipients(
            boolean onlyContacts, Optional<Boolean> blocked, Set<RecipientId> recipientIds, Optional<String> name
    ) {
        final var sqlWhere = new ArrayList<String>();
        if (onlyContacts) {
            sqlWhere.add("(" + SQL_IS_CONTACT + ")");
        }
        if (blocked.isPresent()) {
            sqlWhere.add("r.blocked = ?");
        }
        if (!recipientIds.isEmpty()) {
            final var recipientIdsCommaSeparated = recipientIds.stream()
                    .map(recipientId -> String.valueOf(recipientId.id()))
                    .collect(Collectors.joining(","));
            sqlWhere.add("r._id IN (" + recipientIdsCommaSeparated + ")");
        }
        final var sql = (
                """
                SELECT r._id,
                       r.number, r.uuid,
                       r.profile_key, r.profile_key_credential,
                       r.given_name, r.family_name, r.expiration_time, r.profile_sharing, r.color, r.blocked, r.archived,
                       r.profile_last_update_timestamp, r.profile_given_name, r.profile_family_name, r.profile_about, r.profile_about_emoji, r.profile_avatar_url_path, r.profile_mobile_coin_address, r.profile_unidentified_access_mode, r.profile_capabilities
                FROM %s r
                WHERE (r.number IS NOT NULL OR r.uuid IS NOT NULL) AND %s
                """
        ).formatted(TABLE_RECIPIENT, sqlWhere.size() == 0 ? "TRUE" : String.join(" AND ", sqlWhere));
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                if (blocked.isPresent()) {
                    statement.setBoolean(1, blocked.get());
                }
                try (var result = Utils.executeQueryForStream(statement, this::getRecipientFromResultSet)) {
                    return result.filter(r -> name.isEmpty() || (
                            r.getContact() != null && name.get().equals(r.getContact().getName())
                    ) || (r.getProfile() != null && name.get().equals(r.getProfile().getDisplayName()))).toList();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from recipient store", e);
        }
    }

    public Map<ServiceId, ProfileKey> getServiceIdToProfileKeyMap() {
        final var sql = (
                """
                SELECT r.uuid, r.profile_key
                FROM %s r
                WHERE r.uuid IS NOT NULL AND r.profile_key IS NOT NULL
                """
        ).formatted(TABLE_RECIPIENT);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                return Utils.executeQueryForStream(statement, resultSet -> {
                    final var serviceId = ServiceId.parseOrThrow(resultSet.getBytes("uuid"));
                    final var profileKey = getProfileKeyFromResultSet(resultSet);
                    return new Pair<>(serviceId, profileKey);
                }).filter(Objects::nonNull).collect(Collectors.toMap(Pair::first, Pair::second));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from recipient store", e);
        }
    }

    @Override
    public void deleteContact(RecipientId recipientId) {
        storeContact(recipientId, null);
    }

    public void deleteRecipientData(RecipientId recipientId) {
        logger.debug("Deleting recipient data for {}", recipientId);
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            storeContact(connection, recipientId, null);
            storeProfile(connection, recipientId, null);
            storeProfileKey(connection, recipientId, null, false);
            storeExpiringProfileKeyCredential(connection, recipientId, null);
            deleteRecipient(connection, recipientId);
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update recipient store", e);
        }
    }

    @Override
    public Profile getProfile(final RecipientId recipientId) {
        try (final var connection = database.getConnection()) {
            return getProfile(connection, recipientId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from recipient store", e);
        }
    }

    @Override
    public ProfileKey getProfileKey(final RecipientId recipientId) {
        try (final var connection = database.getConnection()) {
            return getProfileKey(connection, recipientId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from recipient store", e);
        }
    }

    @Override
    public ExpiringProfileKeyCredential getExpiringProfileKeyCredential(final RecipientId recipientId) {
        try (final var connection = database.getConnection()) {
            return getExpiringProfileKeyCredential(connection, recipientId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from recipient store", e);
        }
    }

    @Override
    public void storeProfile(RecipientId recipientId, final Profile profile) {
        try (final var connection = database.getConnection()) {
            storeProfile(connection, recipientId, profile);
        } catch (SQLException e) {
            throw new RuntimeException("Failed update recipient store", e);
        }
    }

    @Override
    public void storeSelfProfileKey(final RecipientId recipientId, final ProfileKey profileKey) {
        try (final var connection = database.getConnection()) {
            storeProfileKey(connection, recipientId, profileKey, false);
        } catch (SQLException e) {
            throw new RuntimeException("Failed update recipient store", e);
        }
    }

    @Override
    public void storeProfileKey(RecipientId recipientId, final ProfileKey profileKey) {
        try (final var connection = database.getConnection()) {
            storeProfileKey(connection, recipientId, profileKey, true);
        } catch (SQLException e) {
            throw new RuntimeException("Failed update recipient store", e);
        }
    }

    @Override
    public void storeExpiringProfileKeyCredential(
            RecipientId recipientId, final ExpiringProfileKeyCredential profileKeyCredential
    ) {
        try (final var connection = database.getConnection()) {
            storeExpiringProfileKeyCredential(connection, recipientId, profileKeyCredential);
        } catch (SQLException e) {
            throw new RuntimeException("Failed update recipient store", e);
        }
    }

    void addLegacyRecipients(final Map<RecipientId, Recipient> recipients) {
        logger.debug("Migrating legacy recipients to database");
        long start = System.nanoTime();
        final var sql = (
                """
                INSERT INTO %s (_id, number, uuid)
                VALUES (?, ?, ?)
                """
        ).formatted(TABLE_RECIPIENT);
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try (final var statement = connection.prepareStatement("DELETE FROM %s".formatted(TABLE_RECIPIENT))) {
                statement.executeUpdate();
            }
            try (final var statement = connection.prepareStatement(sql)) {
                for (final var recipient : recipients.values()) {
                    statement.setLong(1, recipient.getRecipientId().id());
                    statement.setString(2, recipient.getAddress().number().orElse(null));
                    statement.setBytes(3, recipient.getAddress().uuid().map(UuidUtil::toByteArray).orElse(null));
                    statement.executeUpdate();
                }
            }
            logger.debug("Initial inserts took {}ms", (System.nanoTime() - start) / 1000000);

            for (final var recipient : recipients.values()) {
                if (recipient.getContact() != null) {
                    storeContact(connection, recipient.getRecipientId(), recipient.getContact());
                }
                if (recipient.getProfile() != null) {
                    storeProfile(connection, recipient.getRecipientId(), recipient.getProfile());
                }
                if (recipient.getProfileKey() != null) {
                    storeProfileKey(connection, recipient.getRecipientId(), recipient.getProfileKey(), false);
                }
                if (recipient.getExpiringProfileKeyCredential() != null) {
                    storeExpiringProfileKeyCredential(connection,
                            recipient.getRecipientId(),
                            recipient.getExpiringProfileKeyCredential());
                }
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update recipient store", e);
        }
        logger.debug("Complete recipients migration took {}ms", (System.nanoTime() - start) / 1000000);
    }

    long getActualRecipientId(long recipientId) {
        while (recipientsMerged.containsKey(recipientId)) {
            final var newRecipientId = recipientsMerged.get(recipientId);
            logger.debug("Using {} instead of {}, because recipients have been merged", newRecipientId, recipientId);
            recipientId = newRecipientId;
        }
        return recipientId;
    }

    private void storeContact(
            final Connection connection, final RecipientId recipientId, final Contact contact
    ) throws SQLException {
        final var sql = (
                """
                UPDATE %s
                SET given_name = ?, family_name = ?, expiration_time = ?, profile_sharing = ?, color = ?, blocked = ?, archived = ?
                WHERE _id = ?
                """
        ).formatted(TABLE_RECIPIENT);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setString(1, contact == null ? null : contact.getGivenName());
            statement.setString(2, contact == null ? null : contact.getFamilyName());
            statement.setInt(3, contact == null ? 0 : contact.getMessageExpirationTime());
            statement.setBoolean(4, contact != null && contact.isProfileSharingEnabled());
            statement.setString(5, contact == null ? null : contact.getColor());
            statement.setBoolean(6, contact != null && contact.isBlocked());
            statement.setBoolean(7, contact != null && contact.isArchived());
            statement.setLong(8, recipientId.id());
            statement.executeUpdate();
        }
    }

    private void storeExpiringProfileKeyCredential(
            final Connection connection,
            final RecipientId recipientId,
            final ExpiringProfileKeyCredential profileKeyCredential
    ) throws SQLException {
        final var sql = (
                """
                UPDATE %s
                SET profile_key_credential = ?
                WHERE _id = ?
                """
        ).formatted(TABLE_RECIPIENT);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, profileKeyCredential == null ? null : profileKeyCredential.serialize());
            statement.setLong(2, recipientId.id());
            statement.executeUpdate();
        }
    }

    private void storeProfile(
            final Connection connection, final RecipientId recipientId, final Profile profile
    ) throws SQLException {
        final var sql = (
                """
                UPDATE %s
                SET profile_last_update_timestamp = ?, profile_given_name = ?, profile_family_name = ?, profile_about = ?, profile_about_emoji = ?, profile_avatar_url_path = ?, profile_mobile_coin_address = ?, profile_unidentified_access_mode = ?, profile_capabilities = ?
                WHERE _id = ?
                """
        ).formatted(TABLE_RECIPIENT);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, profile == null ? 0 : profile.getLastUpdateTimestamp());
            statement.setString(2, profile == null ? null : profile.getGivenName());
            statement.setString(3, profile == null ? null : profile.getFamilyName());
            statement.setString(4, profile == null ? null : profile.getAbout());
            statement.setString(5, profile == null ? null : profile.getAboutEmoji());
            statement.setString(6, profile == null ? null : profile.getAvatarUrlPath());
            statement.setBytes(7, profile == null ? null : profile.getMobileCoinAddress());
            statement.setString(8, profile == null ? null : profile.getUnidentifiedAccessMode().name());
            statement.setString(9,
                    profile == null
                            ? null
                            : profile.getCapabilities().stream().map(Enum::name).collect(Collectors.joining(",")));
            statement.setLong(10, recipientId.id());
            statement.executeUpdate();
        }
    }

    private void storeProfileKey(
            Connection connection, RecipientId recipientId, final ProfileKey profileKey, boolean resetProfile
    ) throws SQLException {
        if (profileKey != null) {
            final var recipientProfileKey = getProfileKey(recipientId);
            if (profileKey.equals(recipientProfileKey)) {
                final var recipientProfile = getProfile(recipientId);
                if (recipientProfile == null || (
                        recipientProfile.getUnidentifiedAccessMode() != Profile.UnidentifiedAccessMode.UNKNOWN
                                && recipientProfile.getUnidentifiedAccessMode()
                                != Profile.UnidentifiedAccessMode.DISABLED
                )) {
                    return;
                }
            }
        }

        final var sql = (
                """
                UPDATE %s
                SET profile_key = ?, profile_key_credential = NULL%s
                WHERE _id = ?
                """
        ).formatted(TABLE_RECIPIENT, resetProfile ? ", profile_last_update_timestamp = 0" : "");
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, profileKey == null ? null : profileKey.serialize());
            statement.setLong(2, recipientId.id());
            statement.executeUpdate();
        }
    }

    /**
     * @param isHighTrust true, if the number/uuid connection was obtained from a trusted source.
     *                    Has no effect, if the address contains only a number or a uuid.
     */
    private RecipientId resolveRecipient(RecipientAddress address, boolean isHighTrust, boolean isSelf) {
        final Pair<RecipientId, Optional<RecipientId>> pair;
        synchronized (recipientsLock) {
            try (final var connection = database.getConnection()) {
                connection.setAutoCommit(false);
                pair = resolveRecipientLocked(connection, address, isHighTrust, isSelf);
                connection.commit();
            } catch (SQLException e) {
                throw new RuntimeException("Failed update recipient store", e);
            }
        }

        if (pair.second().isPresent()) {
            recipientMergeHandler.mergeRecipients(pair.first(), pair.second().get());
            try (final var connection = database.getConnection()) {
                deleteRecipient(connection, pair.second().get());
            } catch (SQLException e) {
                throw new RuntimeException("Failed update recipient store", e);
            }
        }
        return pair.first();
    }

    private Pair<RecipientId, Optional<RecipientId>> resolveRecipientLocked(
            Connection connection, RecipientAddress address, boolean isHighTrust, boolean isSelf
    ) throws SQLException {
        if (isHighTrust && !isSelf) {
            if (selfAddressProvider.getSelfAddress().matches(address)) {
                isHighTrust = false;
            }
        }
        final var byNumber = address.number().isEmpty()
                ? Optional.<RecipientWithAddress>empty()
                : findByNumber(connection, address.number().get());
        final var byUuid = address.uuid().isEmpty()
                ? Optional.<RecipientWithAddress>empty()
                : findByUuid(connection, address.uuid().get());

        if (byNumber.isEmpty() && byUuid.isEmpty()) {
            logger.debug("Got new recipient, both uuid and number are unknown");

            if (isHighTrust || address.uuid().isEmpty() || address.number().isEmpty()) {
                return new Pair<>(addNewRecipient(connection, address), Optional.empty());
            }

            return new Pair<>(addNewRecipient(connection, new RecipientAddress(address.uuid().get())),
                    Optional.empty());
        }

        if (!isHighTrust || address.uuid().isEmpty() || address.number().isEmpty() || byNumber.equals(byUuid)) {
            return new Pair<>(byUuid.or(() -> byNumber).map(RecipientWithAddress::id).get(), Optional.empty());
        }

        if (byNumber.isEmpty()) {
            logger.debug("Got recipient {} existing with uuid, updating with high trust number", byUuid.get().id());
            updateRecipientAddress(connection, byUuid.get().id(), address);
            return new Pair<>(byUuid.get().id(), Optional.empty());
        }

        final var byNumberRecipient = byNumber.get();

        if (byUuid.isEmpty()) {
            if (byNumberRecipient.address().uuid().isPresent()) {
                logger.debug(
                        "Got recipient {} existing with number, but different uuid, so stripping its number and adding new recipient",
                        byNumberRecipient.id());

                updateRecipientAddress(connection,
                        byNumberRecipient.id(),
                        new RecipientAddress(byNumberRecipient.address().uuid().get()));
                return new Pair<>(addNewRecipient(connection, address), Optional.empty());
            }

            logger.debug("Got recipient {} existing with number and no uuid, updating with high trust uuid",
                    byNumberRecipient.id());
            updateRecipientAddress(connection, byNumberRecipient.id(), address);
            return new Pair<>(byNumberRecipient.id(), Optional.empty());
        }

        final var byUuidRecipient = byUuid.get();

        if (byNumberRecipient.address().uuid().isPresent()) {
            logger.debug(
                    "Got separate recipients for high trust number {} and uuid {}, recipient for number has different uuid, so stripping its number",
                    byNumberRecipient.id(),
                    byUuidRecipient.id());

            updateRecipientAddress(connection,
                    byNumberRecipient.id(),
                    new RecipientAddress(byNumberRecipient.address().uuid().get()));
            updateRecipientAddress(connection, byUuidRecipient.id(), address);
            return new Pair<>(byUuidRecipient.id(), Optional.empty());
        }

        logger.debug("Got separate recipients for high trust number {} and uuid {}, need to merge them",
                byNumberRecipient.id(),
                byUuidRecipient.id());
        // Create a fixed RecipientId that won't update its id after merge
        final var toBeMergedRecipientId = new RecipientId(byNumberRecipient.id().id(), null);
        mergeRecipientsLocked(connection, byUuidRecipient.id(), toBeMergedRecipientId);
        removeRecipientAddress(connection, toBeMergedRecipientId);
        updateRecipientAddress(connection, byUuidRecipient.id(), address);
        return new Pair<>(byUuidRecipient.id(), Optional.of(toBeMergedRecipientId));
    }

    private RecipientId addNewRecipient(
            final Connection connection, final RecipientAddress address
    ) throws SQLException {
        final var sql = (
                """
                INSERT INTO %s (number, uuid)
                VALUES (?, ?)
                """
        ).formatted(TABLE_RECIPIENT);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setString(1, address.number().orElse(null));
            statement.setBytes(2, address.uuid().map(UuidUtil::toByteArray).orElse(null));
            statement.executeUpdate();
            final var generatedKeys = statement.getGeneratedKeys();
            if (generatedKeys.next()) {
                final var recipientId = new RecipientId(generatedKeys.getLong(1), this);
                logger.debug("Added new recipient {} with address {}", recipientId, address);
                return recipientId;
            } else {
                throw new RuntimeException("Failed to add new recipient to database");
            }
        }
    }

    private void removeRecipientAddress(Connection connection, RecipientId recipientId) throws SQLException {
        final var sql = (
                """
                UPDATE %s
                SET number = NULL, uuid = NULL
                WHERE _id = ?
                """
        ).formatted(TABLE_RECIPIENT);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recipientId.id());
            statement.executeUpdate();
        }
    }

    private void updateRecipientAddress(
            Connection connection, RecipientId recipientId, final RecipientAddress address
    ) throws SQLException {
        final var sql = (
                """
                UPDATE %s
                SET number = ?, uuid = ?
                WHERE _id = ?
                """
        ).formatted(TABLE_RECIPIENT);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setString(1, address.number().orElse(null));
            statement.setBytes(2, address.uuid().map(UuidUtil::toByteArray).orElse(null));
            statement.setLong(3, recipientId.id());
            statement.executeUpdate();
        }
    }

    private void deleteRecipient(final Connection connection, final RecipientId recipientId) throws SQLException {
        final var sql = (
                """
                DELETE FROM %s
                WHERE _id = ?
                """
        ).formatted(TABLE_RECIPIENT);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recipientId.id());
            statement.executeUpdate();
        }
    }

    private void mergeRecipientsLocked(
            Connection connection, RecipientId recipientId, RecipientId toBeMergedRecipientId
    ) throws SQLException {
        final var contact = getContact(connection, recipientId);
        if (contact == null) {
            final var toBeMergedContact = getContact(connection, toBeMergedRecipientId);
            storeContact(connection, recipientId, toBeMergedContact);
        }

        final var profileKey = getProfileKey(connection, recipientId);
        if (profileKey == null) {
            final var toBeMergedProfileKey = getProfileKey(connection, toBeMergedRecipientId);
            storeProfileKey(connection, recipientId, toBeMergedProfileKey, false);
        }

        final var profileKeyCredential = getExpiringProfileKeyCredential(connection, recipientId);
        if (profileKeyCredential == null) {
            final var toBeMergedProfileKeyCredential = getExpiringProfileKeyCredential(connection,
                    toBeMergedRecipientId);
            storeExpiringProfileKeyCredential(connection, recipientId, toBeMergedProfileKeyCredential);
        }

        final var profile = getProfile(connection, recipientId);
        if (profile == null) {
            final var toBeMergedProfile = getProfile(connection, toBeMergedRecipientId);
            storeProfile(connection, recipientId, toBeMergedProfile);
        }

        recipientsMerged.put(toBeMergedRecipientId.id(), recipientId.id());
    }

    private Optional<RecipientWithAddress> findByNumber(
            final Connection connection, final String number
    ) throws SQLException {
        final var sql = """
                        SELECT r._id, r.number, r.uuid
                        FROM %s r
                        WHERE r.number = ?
                        """.formatted(TABLE_RECIPIENT);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setString(1, number);
            return Utils.executeQueryForOptional(statement, this::getRecipientWithAddressFromResultSet);
        }
    }

    private Optional<RecipientWithAddress> findByUuid(
            final Connection connection, final UUID uuid
    ) throws SQLException {
        final var sql = """
                        SELECT r._id, r.number, r.uuid
                        FROM %s r
                        WHERE r.uuid = ?
                        """.formatted(TABLE_RECIPIENT);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidUtil.toByteArray(uuid));
            return Utils.executeQueryForOptional(statement, this::getRecipientWithAddressFromResultSet);
        }
    }

    private Contact getContact(final Connection connection, final RecipientId recipientId) throws SQLException {
        final var sql = (
                """
                SELECT r.given_name, r.family_name, r.expiration_time, r.profile_sharing, r.color, r.blocked, r.archived
                FROM %s r
                WHERE r._id = ? AND (%s)
                """
        ).formatted(TABLE_RECIPIENT, SQL_IS_CONTACT);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recipientId.id());
            return Utils.executeQueryForOptional(statement, this::getContactFromResultSet).orElse(null);
        }
    }

    private ProfileKey getProfileKey(final Connection connection, final RecipientId recipientId) throws SQLException {
        final var sql = (
                """
                SELECT r.profile_key
                FROM %s r
                WHERE r._id = ?
                """
        ).formatted(TABLE_RECIPIENT);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recipientId.id());
            return Utils.executeQueryForOptional(statement, this::getProfileKeyFromResultSet).orElse(null);
        }
    }

    private ExpiringProfileKeyCredential getExpiringProfileKeyCredential(
            final Connection connection, final RecipientId recipientId
    ) throws SQLException {
        final var sql = (
                """
                SELECT r.profile_key_credential
                FROM %s r
                WHERE r._id = ?
                """
        ).formatted(TABLE_RECIPIENT);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recipientId.id());
            return Utils.executeQueryForOptional(statement, this::getExpiringProfileKeyCredentialFromResultSet)
                    .orElse(null);
        }
    }

    private Profile getProfile(final Connection connection, final RecipientId recipientId) throws SQLException {
        final var sql = (
                """
                SELECT r.profile_last_update_timestamp, r.profile_given_name, r.profile_family_name, r.profile_about, r.profile_about_emoji, r.profile_avatar_url_path, r.profile_mobile_coin_address, r.profile_unidentified_access_mode, r.profile_capabilities
                FROM %s r
                WHERE r._id = ? AND r.profile_capabilities IS NOT NULL
                """
        ).formatted(TABLE_RECIPIENT);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recipientId.id());
            return Utils.executeQueryForOptional(statement, this::getProfileFromResultSet).orElse(null);
        }
    }

    private RecipientAddress getRecipientAddressFromResultSet(ResultSet resultSet) throws SQLException {
        final var uuid = Optional.ofNullable(resultSet.getBytes("uuid")).map(UuidUtil::parseOrNull);
        final var number = Optional.ofNullable(resultSet.getString("number"));
        return new RecipientAddress(uuid, number);
    }

    private RecipientId getRecipientIdFromResultSet(ResultSet resultSet) throws SQLException {
        return new RecipientId(resultSet.getLong("_id"), this);
    }

    private RecipientWithAddress getRecipientWithAddressFromResultSet(final ResultSet resultSet) throws SQLException {
        return new RecipientWithAddress(getRecipientIdFromResultSet(resultSet),
                getRecipientAddressFromResultSet(resultSet));
    }

    private Recipient getRecipientFromResultSet(final ResultSet resultSet) throws SQLException {
        return new Recipient(getRecipientIdFromResultSet(resultSet),
                getRecipientAddressFromResultSet(resultSet),
                getContactFromResultSet(resultSet),
                getProfileKeyFromResultSet(resultSet),
                getExpiringProfileKeyCredentialFromResultSet(resultSet),
                getProfileFromResultSet(resultSet));
    }

    private Contact getContactFromResultSet(ResultSet resultSet) throws SQLException {
        return new Contact(resultSet.getString("given_name"),
                resultSet.getString("family_name"),
                resultSet.getString("color"),
                resultSet.getInt("expiration_time"),
                resultSet.getBoolean("blocked"),
                resultSet.getBoolean("archived"),
                resultSet.getBoolean("profile_sharing"));
    }

    private Profile getProfileFromResultSet(ResultSet resultSet) throws SQLException {
        final var profileCapabilities = resultSet.getString("profile_capabilities");
        final var profileUnidentifiedAccessMode = resultSet.getString("profile_unidentified_access_mode");
        return new Profile(resultSet.getLong("profile_last_update_timestamp"),
                resultSet.getString("profile_given_name"),
                resultSet.getString("profile_family_name"),
                resultSet.getString("profile_about"),
                resultSet.getString("profile_about_emoji"),
                resultSet.getString("profile_avatar_url_path"),
                resultSet.getBytes("profile_mobile_coin_address"),
                profileUnidentifiedAccessMode == null
                        ? Profile.UnidentifiedAccessMode.UNKNOWN
                        : Profile.UnidentifiedAccessMode.valueOfOrUnknown(profileUnidentifiedAccessMode),
                profileCapabilities == null
                        ? Set.of()
                        : Arrays.stream(profileCapabilities.split(","))
                                .map(Profile.Capability::valueOfOrNull)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet()));
    }

    private ProfileKey getProfileKeyFromResultSet(ResultSet resultSet) throws SQLException {
        final var profileKey = resultSet.getBytes("profile_key");

        if (profileKey == null) {
            return null;
        }
        try {
            return new ProfileKey(profileKey);
        } catch (InvalidInputException ignored) {
            return null;
        }
    }

    private ExpiringProfileKeyCredential getExpiringProfileKeyCredentialFromResultSet(ResultSet resultSet) throws SQLException {
        final var profileKeyCredential = resultSet.getBytes("profile_key_credential");

        if (profileKeyCredential == null) {
            return null;
        }
        try {
            return new ExpiringProfileKeyCredential(profileKeyCredential);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public interface RecipientMergeHandler {

        void mergeRecipients(RecipientId recipientId, RecipientId toBeMergedRecipientId);
    }

    private record RecipientWithAddress(RecipientId id, RecipientAddress address) {}
}

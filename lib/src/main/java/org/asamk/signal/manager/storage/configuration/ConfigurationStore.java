package org.asamk.signal.manager.storage.configuration;

import org.asamk.signal.manager.api.PhoneNumberSharingMode;
import org.asamk.signal.manager.storage.keyValue.KeyValueEntry;
import org.asamk.signal.manager.storage.keyValue.KeyValueStore;
import org.asamk.signal.manager.storage.recipients.RecipientStore;

import java.sql.Connection;
import java.sql.SQLException;

public class ConfigurationStore {

    private final KeyValueStore keyValueStore;
    private final RecipientStore recipientStore;

    private final KeyValueEntry<Boolean> readReceipts = new KeyValueEntry<>("config-read-receipts", Boolean.class);
    private final KeyValueEntry<Boolean> unidentifiedDeliveryIndicators = new KeyValueEntry<>(
            "config-unidentified-delivery-indicators",
            Boolean.class);
    private final KeyValueEntry<Boolean> typingIndicators = new KeyValueEntry<>("config-typing-indicators",
            Boolean.class);
    private final KeyValueEntry<Boolean> linkPreviews = new KeyValueEntry<>("config-link-previews", Boolean.class);
    private final KeyValueEntry<Boolean> phoneNumberUnlisted = new KeyValueEntry<>("config-phone-number-unlisted",
            Boolean.class);
    private final KeyValueEntry<PhoneNumberSharingMode> phoneNumberSharingMode = new KeyValueEntry<>(
            "config-phone-number-sharing-mode",
            PhoneNumberSharingMode.class);
    private final KeyValueEntry<String> usernameLinkColor = new KeyValueEntry<>("username-link-color", String.class);

    public ConfigurationStore(final KeyValueStore keyValueStore, RecipientStore recipientStore) {
        this.keyValueStore = keyValueStore;
        this.recipientStore = recipientStore;
    }

    public Boolean getReadReceipts() {
        return keyValueStore.getEntry(readReceipts);
    }

    public void setReadReceipts(final boolean value) {
        if (keyValueStore.storeEntry(readReceipts, value)) {
            recipientStore.rotateSelfStorageId();
        }
    }

    public void setReadReceipts(final Connection connection, final boolean value) throws SQLException {
        if (keyValueStore.storeEntry(connection, readReceipts, value)) {
            recipientStore.rotateSelfStorageId(connection);
        }
    }

    public Boolean getUnidentifiedDeliveryIndicators() {
        return keyValueStore.getEntry(unidentifiedDeliveryIndicators);
    }

    public void setUnidentifiedDeliveryIndicators(final boolean value) {
        if (keyValueStore.storeEntry(unidentifiedDeliveryIndicators, value)) {
            recipientStore.rotateSelfStorageId();
        }
    }

    public void setUnidentifiedDeliveryIndicators(
            final Connection connection, final boolean value
    ) throws SQLException {
        if (keyValueStore.storeEntry(connection, unidentifiedDeliveryIndicators, value)) {
            recipientStore.rotateSelfStorageId(connection);
        }
    }

    public Boolean getTypingIndicators() {
        return keyValueStore.getEntry(typingIndicators);
    }

    public void setTypingIndicators(final boolean value) {
        if (keyValueStore.storeEntry(typingIndicators, value)) {
            recipientStore.rotateSelfStorageId();
        }
    }

    public void setTypingIndicators(final Connection connection, final boolean value) throws SQLException {
        if (keyValueStore.storeEntry(connection, typingIndicators, value)) {
            recipientStore.rotateSelfStorageId(connection);
        }
    }

    public Boolean getLinkPreviews() {
        return keyValueStore.getEntry(linkPreviews);
    }

    public void setLinkPreviews(final boolean value) {
        if (keyValueStore.storeEntry(linkPreviews, value)) {
            recipientStore.rotateSelfStorageId();
        }
    }

    public void setLinkPreviews(final Connection connection, final boolean value) throws SQLException {
        if (keyValueStore.storeEntry(connection, linkPreviews, value)) {
            recipientStore.rotateSelfStorageId(connection);
        }
    }

    public Boolean getPhoneNumberUnlisted() {
        return keyValueStore.getEntry(phoneNumberUnlisted);
    }

    public void setPhoneNumberUnlisted(final boolean value) {
        if (keyValueStore.storeEntry(phoneNumberUnlisted, value)) {
            recipientStore.rotateSelfStorageId();
        }
    }

    public void setPhoneNumberUnlisted(final Connection connection, final boolean value) throws SQLException {
        if (keyValueStore.storeEntry(connection, phoneNumberUnlisted, value)) {
            recipientStore.rotateSelfStorageId(connection);
        }
    }

    public PhoneNumberSharingMode getPhoneNumberSharingMode() {
        return keyValueStore.getEntry(phoneNumberSharingMode);
    }

    public void setPhoneNumberSharingMode(final PhoneNumberSharingMode value) {
        if (keyValueStore.storeEntry(phoneNumberSharingMode, value)) {
            recipientStore.rotateSelfStorageId();
        }
    }

    public void setPhoneNumberSharingMode(
            final Connection connection, final PhoneNumberSharingMode value
    ) throws SQLException {
        if (keyValueStore.storeEntry(connection, phoneNumberSharingMode, value)) {
            recipientStore.rotateSelfStorageId(connection);
        }
    }

    public String getUsernameLinkColor() {
        return keyValueStore.getEntry(usernameLinkColor);
    }

    public void setUsernameLinkColor(final String color) {
        if (keyValueStore.storeEntry(usernameLinkColor, color)) {
            recipientStore.rotateSelfStorageId();
        }
    }

    public void setUsernameLinkColor(final Connection connection, final String color) throws SQLException {
        if (keyValueStore.storeEntry(connection, usernameLinkColor, color)) {
            recipientStore.rotateSelfStorageId(connection);
        }
    }
}

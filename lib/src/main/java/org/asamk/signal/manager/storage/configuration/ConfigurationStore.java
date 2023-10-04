package org.asamk.signal.manager.storage.configuration;

import org.asamk.signal.manager.api.PhoneNumberSharingMode;
import org.asamk.signal.manager.storage.keyValue.KeyValueEntry;
import org.asamk.signal.manager.storage.keyValue.KeyValueStore;

public class ConfigurationStore {

    private final KeyValueStore keyValueStore;

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

    public ConfigurationStore(final KeyValueStore keyValueStore) {
        this.keyValueStore = keyValueStore;
    }

    public Boolean getReadReceipts() {
        return keyValueStore.getEntry(readReceipts);
    }

    public void setReadReceipts(final boolean value) {
        keyValueStore.storeEntry(readReceipts, value);
    }

    public Boolean getUnidentifiedDeliveryIndicators() {
        return keyValueStore.getEntry(unidentifiedDeliveryIndicators);
    }

    public void setUnidentifiedDeliveryIndicators(final boolean value) {
        keyValueStore.storeEntry(unidentifiedDeliveryIndicators, value);
    }

    public Boolean getTypingIndicators() {
        return keyValueStore.getEntry(typingIndicators);
    }

    public void setTypingIndicators(final boolean value) {
        keyValueStore.storeEntry(typingIndicators, value);
    }

    public Boolean getLinkPreviews() {
        return keyValueStore.getEntry(linkPreviews);
    }

    public void setLinkPreviews(final boolean value) {
        keyValueStore.storeEntry(linkPreviews, value);
    }

    public Boolean getPhoneNumberUnlisted() {
        return keyValueStore.getEntry(phoneNumberUnlisted);
    }

    public void setPhoneNumberUnlisted(final boolean value) {
        keyValueStore.storeEntry(phoneNumberUnlisted, value);
    }

    public PhoneNumberSharingMode getPhoneNumberSharingMode() {
        return keyValueStore.getEntry(phoneNumberSharingMode);
    }

    public void setPhoneNumberSharingMode(final PhoneNumberSharingMode value) {
        keyValueStore.storeEntry(phoneNumberSharingMode, value);
    }
}

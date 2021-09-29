package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.AvatarStore;
import org.asamk.signal.manager.TrustLevel;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfoV1;
import org.asamk.signal.manager.storage.recipients.Contact;
import org.asamk.signal.manager.util.AttachmentUtils;
import org.asamk.signal.manager.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ConfigurationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroupsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.KeysMessage;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class SyncHelper {

    private final static Logger logger = LoggerFactory.getLogger(SyncHelper.class);

    private final SignalAccount account;
    private final AttachmentHelper attachmentHelper;
    private final SendHelper sendHelper;
    private final GroupHelper groupHelper;
    private final AvatarStore avatarStore;
    private final SignalServiceAddressResolver addressResolver;

    public SyncHelper(
            final SignalAccount account,
            final AttachmentHelper attachmentHelper,
            final SendHelper sendHelper,
            final GroupHelper groupHelper,
            final AvatarStore avatarStore,
            final SignalServiceAddressResolver addressResolver
    ) {
        this.account = account;
        this.attachmentHelper = attachmentHelper;
        this.sendHelper = sendHelper;
        this.groupHelper = groupHelper;
        this.avatarStore = avatarStore;
        this.addressResolver = addressResolver;
    }

    public void requestAllSyncData() throws IOException {
        requestSyncGroups();
        requestSyncContacts();
        requestSyncBlocked();
        requestSyncConfiguration();
        requestSyncKeys();
    }

    public void sendSyncFetchProfileMessage() throws IOException {
        sendHelper.sendSyncMessage(SignalServiceSyncMessage.forFetchLatest(SignalServiceSyncMessage.FetchType.LOCAL_PROFILE));
    }

    public void sendGroups() throws IOException {
        var groupsFile = IOUtils.createTempFile();

        try {
            try (OutputStream fos = new FileOutputStream(groupsFile)) {
                var out = new DeviceGroupsOutputStream(fos);
                for (var record : account.getGroupStore().getGroups()) {
                    if (record instanceof GroupInfoV1) {
                        var groupInfo = (GroupInfoV1) record;
                        out.write(new DeviceGroup(groupInfo.getGroupId().serialize(),
                                Optional.fromNullable(groupInfo.name),
                                groupInfo.getMembers()
                                        .stream()
                                        .map(addressResolver::resolveSignalServiceAddress)
                                        .collect(Collectors.toList()),
                                groupHelper.createGroupAvatarAttachment(groupInfo.getGroupId()),
                                groupInfo.isMember(account.getSelfRecipientId()),
                                Optional.of(groupInfo.messageExpirationTime),
                                Optional.fromNullable(groupInfo.color),
                                groupInfo.blocked,
                                Optional.absent(),
                                groupInfo.archived));
                    }
                }
            }

            if (groupsFile.exists() && groupsFile.length() > 0) {
                try (var groupsFileStream = new FileInputStream(groupsFile)) {
                    var attachmentStream = SignalServiceAttachment.newStreamBuilder()
                            .withStream(groupsFileStream)
                            .withContentType("application/octet-stream")
                            .withLength(groupsFile.length())
                            .build();

                    sendHelper.sendSyncMessage(SignalServiceSyncMessage.forGroups(attachmentStream));
                }
            }
        } finally {
            try {
                Files.delete(groupsFile.toPath());
            } catch (IOException e) {
                logger.warn("Failed to delete groups temp file “{}”, ignoring: {}", groupsFile, e.getMessage());
            }
        }
    }

    public void sendContacts() throws IOException {
        var contactsFile = IOUtils.createTempFile();

        try {
            try (OutputStream fos = new FileOutputStream(contactsFile)) {
                var out = new DeviceContactsOutputStream(fos);
                for (var contactPair : account.getContactStore().getContacts()) {
                    final var recipientId = contactPair.first();
                    final var contact = contactPair.second();
                    final var address = addressResolver.resolveSignalServiceAddress(recipientId);

                    var currentIdentity = account.getIdentityKeyStore().getIdentity(recipientId);
                    VerifiedMessage verifiedMessage = null;
                    if (currentIdentity != null) {
                        verifiedMessage = new VerifiedMessage(address,
                                currentIdentity.getIdentityKey(),
                                currentIdentity.getTrustLevel().toVerifiedState(),
                                currentIdentity.getDateAdded().getTime());
                    }

                    var profileKey = account.getProfileStore().getProfileKey(recipientId);
                    out.write(new DeviceContact(address,
                            Optional.fromNullable(contact.getName()),
                            createContactAvatarAttachment(address),
                            Optional.fromNullable(contact.getColor()),
                            Optional.fromNullable(verifiedMessage),
                            Optional.fromNullable(profileKey),
                            contact.isBlocked(),
                            Optional.of(contact.getMessageExpirationTime()),
                            Optional.absent(),
                            contact.isArchived()));
                }

                if (account.getProfileKey() != null) {
                    // Send our own profile key as well
                    out.write(new DeviceContact(account.getSelfAddress(),
                            Optional.absent(),
                            Optional.absent(),
                            Optional.absent(),
                            Optional.absent(),
                            Optional.of(account.getProfileKey()),
                            false,
                            Optional.absent(),
                            Optional.absent(),
                            false));
                }
            }

            if (contactsFile.exists() && contactsFile.length() > 0) {
                try (var contactsFileStream = new FileInputStream(contactsFile)) {
                    var attachmentStream = SignalServiceAttachment.newStreamBuilder()
                            .withStream(contactsFileStream)
                            .withContentType("application/octet-stream")
                            .withLength(contactsFile.length())
                            .build();

                    sendHelper.sendSyncMessage(SignalServiceSyncMessage.forContacts(new ContactsMessage(attachmentStream,
                            true)));
                }
            }
        } finally {
            try {
                Files.delete(contactsFile.toPath());
            } catch (IOException e) {
                logger.warn("Failed to delete contacts temp file “{}”, ignoring: {}", contactsFile, e.getMessage());
            }
        }
    }

    public void sendBlockedList() throws IOException {
        var addresses = new ArrayList<SignalServiceAddress>();
        for (var record : account.getContactStore().getContacts()) {
            if (record.second().isBlocked()) {
                addresses.add(addressResolver.resolveSignalServiceAddress(record.first()));
            }
        }
        var groupIds = new ArrayList<byte[]>();
        for (var record : account.getGroupStore().getGroups()) {
            if (record.isBlocked()) {
                groupIds.add(record.getGroupId().serialize());
            }
        }
        sendHelper.sendSyncMessage(SignalServiceSyncMessage.forBlocked(new BlockedListMessage(addresses, groupIds)));
    }

    public void sendVerifiedMessage(
            SignalServiceAddress destination, IdentityKey identityKey, TrustLevel trustLevel
    ) throws IOException {
        var verifiedMessage = new VerifiedMessage(destination,
                identityKey,
                trustLevel.toVerifiedState(),
                System.currentTimeMillis());
        sendHelper.sendSyncMessage(SignalServiceSyncMessage.forVerified(verifiedMessage));
    }

    public void sendKeysMessage() throws IOException {
        var keysMessage = new KeysMessage(Optional.fromNullable(account.getStorageKey()));
        sendHelper.sendSyncMessage(SignalServiceSyncMessage.forKeys(keysMessage));
    }

    public void sendConfigurationMessage() throws IOException {
        final var config = account.getConfigurationStore();
        var configurationMessage = new ConfigurationMessage(Optional.fromNullable(config.getReadReceipts()),
                Optional.fromNullable(config.getUnidentifiedDeliveryIndicators()),
                Optional.fromNullable(config.getTypingIndicators()),
                Optional.fromNullable(config.getLinkPreviews()));
        sendHelper.sendSyncMessage(SignalServiceSyncMessage.forConfiguration(configurationMessage));
    }

    public void handleSyncDeviceContacts(final InputStream input) throws IOException {
        final var s = new DeviceContactsInputStream(input);
        DeviceContact c;
        while (true) {
            try {
                c = s.read();
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("Missing contact address!")) {
                    logger.warn("Sync contacts contained invalid contact, ignoring: {}", e.getMessage());
                    continue;
                } else {
                    throw e;
                }
            }
            if (c == null) {
                break;
            }
            if (c.getAddress().matches(account.getSelfAddress()) && c.getProfileKey().isPresent()) {
                account.setProfileKey(c.getProfileKey().get());
            }
            final var recipientId = account.getRecipientStore().resolveRecipientTrusted(c.getAddress());
            var contact = account.getContactStore().getContact(recipientId);
            final var builder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
            if (c.getName().isPresent()) {
                builder.withName(c.getName().get());
            }
            if (c.getColor().isPresent()) {
                builder.withColor(c.getColor().get());
            }
            if (c.getProfileKey().isPresent()) {
                account.getProfileStore().storeProfileKey(recipientId, c.getProfileKey().get());
            }
            if (c.getVerified().isPresent()) {
                final var verifiedMessage = c.getVerified().get();
                account.getIdentityKeyStore()
                        .setIdentityTrustLevel(account.getRecipientStore()
                                        .resolveRecipientTrusted(verifiedMessage.getDestination()),
                                verifiedMessage.getIdentityKey(),
                                TrustLevel.fromVerifiedState(verifiedMessage.getVerified()));
            }
            if (c.getExpirationTimer().isPresent()) {
                builder.withMessageExpirationTime(c.getExpirationTimer().get());
            }
            builder.withBlocked(c.isBlocked());
            builder.withArchived(c.isArchived());
            account.getContactStore().storeContact(recipientId, builder.build());

            if (c.getAvatar().isPresent()) {
                downloadContactAvatar(c.getAvatar().get(), c.getAddress());
            }
        }
    }

    private void requestSyncGroups() throws IOException {
        var r = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.GROUPS)
                .build();
        var message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        sendHelper.sendSyncMessage(message);
    }

    private void requestSyncContacts() throws IOException {
        var r = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.CONTACTS)
                .build();
        var message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        sendHelper.sendSyncMessage(message);
    }

    private void requestSyncBlocked() throws IOException {
        var r = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.BLOCKED)
                .build();
        var message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        sendHelper.sendSyncMessage(message);
    }

    private void requestSyncConfiguration() throws IOException {
        var r = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.CONFIGURATION)
                .build();
        var message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        sendHelper.sendSyncMessage(message);
    }

    private void requestSyncKeys() throws IOException {
        var r = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.KEYS)
                .build();
        var message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        sendHelper.sendSyncMessage(message);
    }

    private Optional<SignalServiceAttachmentStream> createContactAvatarAttachment(SignalServiceAddress address) throws IOException {
        final var streamDetails = avatarStore.retrieveContactAvatar(address);
        if (streamDetails == null) {
            return Optional.absent();
        }

        return Optional.of(AttachmentUtils.createAttachment(streamDetails, Optional.absent()));
    }

    private void downloadContactAvatar(SignalServiceAttachment avatar, SignalServiceAddress address) {
        try {
            avatarStore.storeContactAvatar(address,
                    outputStream -> attachmentHelper.retrieveAttachment(avatar, outputStream));
        } catch (IOException e) {
            logger.warn("Failed to download avatar for contact {}, ignoring: {}", address, e.getMessage());
        }
    }
}

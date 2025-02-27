package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.Contact;
import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.MessageEnvelope.Sync.MessageRequestResponse;
import org.asamk.signal.manager.api.TrustLevel;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfoV1;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.stickers.StickerPack;
import org.asamk.signal.manager.util.IOUtils;
import org.asamk.signal.manager.util.MimeUtils;
import org.jetbrains.annotations.NotNull;
import org.signal.libsignal.protocol.IdentityKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ConfigurationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactAvatar;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroupsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroupsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.KeysMessage;
import org.whispersystems.signalservice.api.messages.multidevice.MessageRequestResponseMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewedMessage;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.push.SyncMessage;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SyncHelper {

    private static final Logger logger = LoggerFactory.getLogger(SyncHelper.class);

    private final Context context;
    private final SignalAccount account;

    public SyncHelper(final Context context) {
        this.context = context;
        this.account = context.getAccount();
    }

    public void requestAllSyncData() {
        requestSyncData(SyncMessage.Request.Type.GROUPS);
        requestSyncData(SyncMessage.Request.Type.CONTACTS);
        requestSyncData(SyncMessage.Request.Type.BLOCKED);
        requestSyncData(SyncMessage.Request.Type.CONFIGURATION);
        requestSyncKeys();
    }

    public void requestSyncKeys() {
        requestSyncData(SyncMessage.Request.Type.KEYS);
    }

    public SendMessageResult sendSyncFetchProfileMessage() {
        return context.getSendHelper()
                .sendSyncMessage(SignalServiceSyncMessage.forFetchLatest(SignalServiceSyncMessage.FetchType.LOCAL_PROFILE));
    }

    public void sendSyncFetchStorageMessage() {
        context.getSendHelper()
                .sendSyncMessage(SignalServiceSyncMessage.forFetchLatest(SignalServiceSyncMessage.FetchType.STORAGE_MANIFEST));
    }

    public void sendSyncReceiptMessage(ServiceId sender, SignalServiceReceiptMessage receiptMessage) {
        if (receiptMessage.isReadReceipt()) {
            final var readMessages = receiptMessage.getTimestamps()
                    .stream()
                    .map(t -> new ReadMessage(sender, t))
                    .toList();
            context.getSendHelper().sendSyncMessage(SignalServiceSyncMessage.forRead(readMessages));
        } else if (receiptMessage.isViewedReceipt()) {
            final var viewedMessages = receiptMessage.getTimestamps()
                    .stream()
                    .map(t -> new ViewedMessage(sender, t))
                    .toList();
            context.getSendHelper().sendSyncMessage(SignalServiceSyncMessage.forViewed(viewedMessages));
        }
    }

    public void sendGroups() throws IOException {
        var groupsFile = IOUtils.createTempFile();

        try {
            try (OutputStream fos = new FileOutputStream(groupsFile)) {
                var out = new DeviceGroupsOutputStream(fos);
                for (var record : account.getGroupStore().getGroups()) {
                    if (record instanceof GroupInfoV1 groupInfo) {
                        out.write(new DeviceGroup(groupInfo.getGroupId().serialize(),
                                Optional.ofNullable(groupInfo.name),
                                groupInfo.getMembers()
                                        .stream()
                                        .map(context.getRecipientHelper()::resolveSignalServiceAddress)
                                        .toList(),
                                context.getGroupHelper().createGroupAvatarAttachment(groupInfo.getGroupId()),
                                groupInfo.isMember(account.getSelfRecipientId()),
                                Optional.of(groupInfo.messageExpirationTime),
                                Optional.ofNullable(groupInfo.color),
                                groupInfo.blocked,
                                Optional.empty(),
                                groupInfo.archived));
                    }
                }
            }

            if (groupsFile.exists() && groupsFile.length() > 0) {
                try (var groupsFileStream = new FileInputStream(groupsFile)) {
                    final var uploadSpec = context.getDependencies().getMessageSender().getResumableUploadSpec();
                    var attachmentStream = SignalServiceAttachment.newStreamBuilder()
                            .withStream(groupsFileStream)
                            .withContentType(MimeUtils.OCTET_STREAM)
                            .withLength(groupsFile.length())
                            .withResumableUploadSpec(uploadSpec)
                            .build();

                    context.getSendHelper().sendSyncMessage(SignalServiceSyncMessage.forGroups(attachmentStream));
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
                    final var address = account.getRecipientAddressResolver().resolveRecipientAddress(recipientId);

                    final var deviceContact = getDeviceContact(address, contact);
                    out.write(deviceContact);
                    deviceContact.getAvatar().ifPresent(a -> {
                        try {
                            a.getInputStream().close();
                        } catch (IOException ignored) {
                        }
                    });
                }

                if (account.getProfileKey() != null) {
                    // Send our own profile key as well
                    final var address = account.getSelfRecipientAddress();
                    final var recipientId = account.getSelfRecipientId();
                    final var contact = account.getContactStore().getContact(recipientId);
                    final var deviceContact = getDeviceContact(address, contact);
                    out.write(deviceContact);
                    deviceContact.getAvatar().ifPresent(a -> {
                        try {
                            a.getInputStream().close();
                        } catch (IOException ignored) {
                        }
                    });
                }
            }

            if (contactsFile.exists() && contactsFile.length() > 0) {
                try (var contactsFileStream = new FileInputStream(contactsFile)) {
                    final var uploadSpec = context.getDependencies().getMessageSender().getResumableUploadSpec();
                    var attachmentStream = SignalServiceAttachment.newStreamBuilder()
                            .withStream(contactsFileStream)
                            .withContentType(MimeUtils.OCTET_STREAM)
                            .withLength(contactsFile.length())
                            .withResumableUploadSpec(uploadSpec)
                            .build();

                    context.getSendHelper()
                            .sendSyncMessage(SignalServiceSyncMessage.forContacts(new ContactsMessage(attachmentStream,
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

    @NotNull
    private DeviceContact getDeviceContact(final RecipientAddress address, final Contact contact) throws IOException {
        return new DeviceContact(address.aci(),
                address.number(),
                Optional.ofNullable(contact == null ? null : contact.getName()),
                createContactAvatarAttachment(address),
                Optional.ofNullable(contact == null ? null : contact.messageExpirationTime()),
                Optional.ofNullable(contact == null ? null : contact.messageExpirationTimeVersion()),
                Optional.empty());
    }

    public SendMessageResult sendBlockedList() {
        var addresses = new ArrayList<BlockedListMessage.Individual>();
        for (var record : account.getContactStore().getContacts()) {
            if (record.second().isBlocked()) {
                final var address = account.getRecipientAddressResolver().resolveRecipientAddress(record.first());
                if (address.aci().isPresent() || address.number().isPresent()) {
                    addresses.add(new BlockedListMessage.Individual(address.aci().orElse(null),
                            address.number().orElse(null)));
                }
            }
        }
        var groupIds = new ArrayList<byte[]>();
        for (var record : account.getGroupStore().getGroups()) {
            if (record.isBlocked()) {
                groupIds.add(record.getGroupId().serialize());
            }
        }
        return context.getSendHelper()
                .sendSyncMessage(SignalServiceSyncMessage.forBlocked(new BlockedListMessage(addresses, groupIds)));
    }

    public SendMessageResult sendVerifiedMessage(
            SignalServiceAddress destination,
            IdentityKey identityKey,
            TrustLevel trustLevel
    ) {
        var verifiedMessage = new VerifiedMessage(destination,
                identityKey,
                trustLevel.toVerifiedState(),
                System.currentTimeMillis());
        return context.getSendHelper().sendSyncMessage(SignalServiceSyncMessage.forVerified(verifiedMessage));
    }

    public SendMessageResult sendKeysMessage() {
        var keysMessage = new KeysMessage(account.getOrCreateStorageKey(),
                account.getOrCreatePinMasterKey(),
                account.getOrCreateAccountEntropyPool(),
                account.getOrCreateMediaRootBackupKey());
        return context.getSendHelper().sendSyncMessage(SignalServiceSyncMessage.forKeys(keysMessage));
    }

    public SendMessageResult sendStickerOperationsMessage(
            List<StickerPack> installStickers,
            List<StickerPack> removeStickers
    ) {
        var installStickerMessages = installStickers.stream().map(s -> getStickerPackOperationMessage(s, true));
        var removeStickerMessages = removeStickers.stream().map(s -> getStickerPackOperationMessage(s, false));
        var stickerMessages = Stream.concat(installStickerMessages, removeStickerMessages).toList();
        return context.getSendHelper()
                .sendSyncMessage(SignalServiceSyncMessage.forStickerPackOperations(stickerMessages));
    }

    private static StickerPackOperationMessage getStickerPackOperationMessage(
            final StickerPack s,
            final boolean installed
    ) {
        return new StickerPackOperationMessage(s.packId().serialize(),
                s.packKey(),
                installed ? StickerPackOperationMessage.Type.INSTALL : StickerPackOperationMessage.Type.REMOVE);
    }

    public SendMessageResult sendConfigurationMessage() {
        final var config = account.getConfigurationStore();
        var configurationMessage = new ConfigurationMessage(Optional.ofNullable(config.getReadReceipts()),
                Optional.ofNullable(config.getUnidentifiedDeliveryIndicators()),
                Optional.ofNullable(config.getTypingIndicators()),
                Optional.ofNullable(config.getLinkPreviews()));
        return context.getSendHelper().sendSyncMessage(SignalServiceSyncMessage.forConfiguration(configurationMessage));
    }

    public void handleSyncDeviceGroups(final InputStream input) {
        final var s = new DeviceGroupsInputStream(input);
        DeviceGroup g;
        while (true) {
            try {
                g = s.read();
            } catch (IOException e) {
                logger.warn("Sync groups contained invalid group, ignoring: {}", e.getMessage());
                continue;
            }
            if (g == null) {
                break;
            }
            var syncGroup = account.getGroupStore().getOrCreateGroupV1(GroupId.v1(g.getId()));
            if (syncGroup != null) {
                if (g.getName().isPresent()) {
                    syncGroup.name = g.getName().get();
                }
                syncGroup.addMembers(g.getMembers()
                        .stream()
                        .map(account.getRecipientResolver()::resolveRecipient)
                        .collect(Collectors.toSet()));
                if (!g.isActive()) {
                    syncGroup.removeMember(account.getSelfRecipientId());
                } else {
                    // Add ourself to the member set as it's marked as active
                    syncGroup.addMembers(List.of(account.getSelfRecipientId()));
                }
                syncGroup.blocked = g.isBlocked();
                if (g.getColor().isPresent()) {
                    syncGroup.color = g.getColor().get();
                }

                if (g.getAvatar().isPresent()) {
                    context.getGroupHelper().downloadGroupAvatar(syncGroup.getGroupId(), g.getAvatar().get());
                }
                syncGroup.archived = g.isArchived();
                account.getGroupStore().updateGroup(syncGroup);
            }
        }
    }

    public void handleSyncDeviceContacts(final InputStream input) throws IOException {
        final var s = new DeviceContactsInputStream(input);
        DeviceContact c;
        while (true) {
            try {
                c = s.read();
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("Missing contact address!")) {
                    logger.debug("Sync contacts contained invalid contact, ignoring: {}", e.getMessage());
                    continue;
                } else {
                    throw e;
                }
            }
            if (c == null || (c.getAci().isEmpty() && c.getE164().isEmpty())) {
                break;
            }
            final var address = new RecipientAddress(c.getAci(), Optional.empty(), c.getE164(), Optional.empty());
            final var recipientId = account.getRecipientTrustedResolver().resolveRecipientTrusted(address);
            var contact = account.getContactStore().getContact(recipientId);
            final var builder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
            if (c.getName().isPresent() && (
                    contact == null || (
                            contact.givenName() == null && contact.familyName() == null
                    )
            )) {
                builder.withGivenName(c.getName().get());
                builder.withFamilyName(null);
            }
            if (c.getExpirationTimer().isPresent()) {
                if (c.getExpirationTimerVersion().isPresent() && (
                        contact == null || c.getExpirationTimerVersion().get() > contact.messageExpirationTimeVersion()
                )) {
                    builder.withMessageExpirationTime(c.getExpirationTimer().get());
                    builder.withMessageExpirationTimeVersion(c.getExpirationTimerVersion().get());
                } else {
                    logger.debug(
                            "[ContactSync] {} was synced with an old expiration timer. Ignoring. Received: {} Current: {}",
                            recipientId,
                            c.getExpirationTimerVersion(),
                            contact == null ? 1 : contact.messageExpirationTimeVersion());
                }
            }
            account.getContactStore().storeContact(recipientId, builder.build());

            if (c.getAvatar().isPresent()) {
                storeContactAvatar(c.getAvatar().get(), address);
            }
        }
    }

    public SendMessageResult sendMessageRequestResponse(final MessageRequestResponse.Type type, final GroupId groupId) {
        final var response = MessageRequestResponseMessage.forGroup(groupId.serialize(), localToRemoteType(type));
        return context.getSendHelper().sendSyncMessage(SignalServiceSyncMessage.forMessageRequestResponse(response));
    }

    public SendMessageResult sendMessageRequestResponse(
            final MessageRequestResponse.Type type,
            final RecipientId recipientId
    ) {
        final var address = account.getRecipientAddressResolver().resolveRecipientAddress(recipientId);
        if (address.serviceId().isEmpty()) {
            return null;
        }
        context.getContactHelper()
                .setContactProfileSharing(recipientId,
                        type == MessageRequestResponse.Type.ACCEPT
                                || type == MessageRequestResponse.Type.UNBLOCK_AND_ACCEPT);
        final var response = MessageRequestResponseMessage.forIndividual(address.serviceId().get(),
                localToRemoteType(type));
        return context.getSendHelper().sendSyncMessage(SignalServiceSyncMessage.forMessageRequestResponse(response));
    }

    private SendMessageResult requestSyncData(final SyncMessage.Request.Type type) {
        var r = new SyncMessage.Request.Builder().type(type).build();
        var message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        return context.getSendHelper().sendSyncMessage(message);
    }

    private Optional<DeviceContactAvatar> createContactAvatarAttachment(RecipientAddress address) throws IOException {
        final var streamDetails = context.getAvatarStore().retrieveContactAvatar(address);
        if (streamDetails == null) {
            return Optional.empty();
        }

        return Optional.of(new DeviceContactAvatar(streamDetails.getStream(),
                streamDetails.getLength(),
                streamDetails.getContentType()));
    }

    private void storeContactAvatar(DeviceContactAvatar avatar, RecipientAddress address) {
        try {
            context.getAvatarStore()
                    .storeContactAvatar(address,
                            outputStream -> IOUtils.copyStream(avatar.getInputStream(), outputStream));
        } catch (IOException e) {
            logger.warn("Failed to download avatar for contact {}, ignoring: {}", address, e.getMessage());
        }
    }

    private MessageRequestResponseMessage.Type localToRemoteType(final MessageRequestResponse.Type type) {
        return switch (type) {
            case UNKNOWN -> MessageRequestResponseMessage.Type.UNKNOWN;
            case ACCEPT -> MessageRequestResponseMessage.Type.ACCEPT;
            case DELETE -> MessageRequestResponseMessage.Type.DELETE;
            case BLOCK -> MessageRequestResponseMessage.Type.BLOCK;
            case BLOCK_AND_DELETE -> MessageRequestResponseMessage.Type.BLOCK_AND_DELETE;
            case UNBLOCK_AND_ACCEPT -> MessageRequestResponseMessage.Type.UNBLOCK_AND_ACCEPT;
            case SPAM -> MessageRequestResponseMessage.Type.SPAM;
            case BLOCK_AND_SPAM -> MessageRequestResponseMessage.Type.BLOCK_AND_SPAM;
        };
    }
}

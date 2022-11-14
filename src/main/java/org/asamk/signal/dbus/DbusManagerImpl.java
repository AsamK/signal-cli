package org.asamk.signal.dbus;

import org.asamk.Signal;
import org.asamk.signal.DbusConfig;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.AttachmentInvalidException;
import org.asamk.signal.manager.api.Configuration;
import org.asamk.signal.manager.api.Device;
import org.asamk.signal.manager.api.Group;
import org.asamk.signal.manager.api.Identity;
import org.asamk.signal.manager.api.InactiveGroupLinkException;
import org.asamk.signal.manager.api.InvalidDeviceLinkException;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.NotPrimaryDeviceException;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.ReceiveConfig;
import org.asamk.signal.manager.api.Recipient;
import org.asamk.signal.manager.api.RecipientAddress;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.SendGroupMessageResults;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.manager.api.StickerPack;
import org.asamk.signal.manager.api.StickerPackInvalidException;
import org.asamk.signal.manager.api.StickerPackUrl;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.api.UpdateGroup;
import org.asamk.signal.manager.api.UpdateProfile;
import org.asamk.signal.manager.api.UserStatus;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupPermission;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.LastGroupAdminException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.storage.recipients.Contact;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.freedesktop.dbus.DBusMap;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.types.Variant;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class implements the Manager interface using the DBus Signal interface, where possible.
 * It's used for the signal-cli dbus client mode (--dbus, --dbus-system)
 */
public class DbusManagerImpl implements Manager {

    private final Signal signal;
    private final DBusConnection connection;

    private final Set<ReceiveMessageHandler> weakHandlers = new HashSet<>();
    private final Set<ReceiveMessageHandler> messageHandlers = new HashSet<>();
    private final List<Runnable> closedListeners = new ArrayList<>();
    private DBusSigHandler<Signal.MessageReceivedV2> dbusMsgHandler;
    private DBusSigHandler<Signal.ReceiptReceivedV2> dbusRcptHandler;
    private DBusSigHandler<Signal.SyncMessageReceivedV2> dbusSyncHandler;

    public DbusManagerImpl(final Signal signal, DBusConnection connection) {
        this.signal = signal;
        this.connection = connection;
    }

    @Override
    public String getSelfNumber() {
        return signal.getSelfNumber();
    }

    @Override
    public Map<String, UserStatus> getUserStatus(final Set<String> numbers) throws IOException {
        final var numbersList = new ArrayList<>(numbers);
        final var registered = signal.isRegistered(numbersList);

        final var result = new HashMap<String, UserStatus>();
        for (var i = 0; i < numbersList.size(); i++) {
            result.put(numbersList.get(i),
                    new UserStatus(numbersList.get(i),
                            registered.get(i) ? RecipientAddress.UNKNOWN_UUID : null,
                            false));
        }
        return result;
    }

    @Override
    public void updateAccountAttributes(final String deviceName) throws IOException {
        if (deviceName != null) {
            final var devicePath = signal.getThisDevice();
            getRemoteObject(devicePath, Signal.Device.class).Set("org.asamk.Signal.Device", "Name", deviceName);
        }
    }

    @Override
    public Configuration getConfiguration() {
        final var configuration = getRemoteObject(new DBusPath(signal.getObjectPath() + "/Configuration"),
                Signal.Configuration.class).GetAll("org.asamk.Signal.Configuration");
        return new Configuration(Optional.of((Boolean) configuration.get("ReadReceipts").getValue()),
                Optional.of((Boolean) configuration.get("UnidentifiedDeliveryIndicators").getValue()),
                Optional.of((Boolean) configuration.get("TypingIndicators").getValue()),
                Optional.of((Boolean) configuration.get("LinkPreviews").getValue()));
    }

    @Override
    public void updateConfiguration(Configuration newConfiguration) throws IOException {
        final var configuration = getRemoteObject(new DBusPath(signal.getObjectPath() + "/Configuration"),
                Signal.Configuration.class);
        newConfiguration.readReceipts()
                .ifPresent(v -> configuration.Set("org.asamk.Signal.Configuration", "ReadReceipts", v));
        newConfiguration.unidentifiedDeliveryIndicators()
                .ifPresent(v -> configuration.Set("org.asamk.Signal.Configuration",
                        "UnidentifiedDeliveryIndicators",
                        v));
        newConfiguration.typingIndicators()
                .ifPresent(v -> configuration.Set("org.asamk.Signal.Configuration", "TypingIndicators", v));
        newConfiguration.linkPreviews()
                .ifPresent(v -> configuration.Set("org.asamk.Signal.Configuration", "LinkPreviews", v));
    }

    @Override
    public void updateProfile(UpdateProfile updateProfile) throws IOException {
        signal.updateProfile(emptyIfNull(updateProfile.getGivenName()),
                emptyIfNull(updateProfile.getFamilyName()),
                emptyIfNull(updateProfile.getAbout()),
                emptyIfNull(updateProfile.getAboutEmoji()),
                updateProfile.getAvatar() == null ? "" : updateProfile.getAvatar(),
                updateProfile.isDeleteAvatar());
    }

    @Override
    public void unregister() throws IOException {
        signal.unregister();
    }

    @Override
    public void deleteAccount() throws IOException {
        signal.deleteAccount();
    }

    @Override
    public void submitRateLimitRecaptchaChallenge(final String challenge, final String captcha) throws IOException {
        signal.submitRateLimitChallenge(challenge, captcha);
    }

    @Override
    public List<Device> getLinkedDevices() throws IOException {
        final var thisDevice = signal.getThisDevice();
        return signal.listDevices().stream().map(d -> {
            final var device = getRemoteObject(d.getObjectPath(),
                    Signal.Device.class).GetAll("org.asamk.Signal.Device");
            return new Device((Integer) device.get("Id").getValue(),
                    (String) device.get("Name").getValue(),
                    (long) device.get("Created").getValue(),
                    (long) device.get("LastSeen").getValue(),
                    thisDevice.equals(d.getObjectPath()));
        }).toList();
    }

    @Override
    public void removeLinkedDevices(final int deviceId) throws IOException {
        final var devicePath = signal.getDevice(deviceId);
        getRemoteObject(devicePath, Signal.Device.class).removeDevice();
    }

    @Override
    public void addDeviceLink(final URI linkUri) throws IOException, InvalidDeviceLinkException {
        signal.addDevice(linkUri.toString());
    }

    @Override
    public void setRegistrationLockPin(final Optional<String> pin) throws IOException {
        if (pin.isPresent()) {
            signal.setPin(pin.get());
        } else {
            signal.removePin();
        }
    }

    @Override
    public Profile getRecipientProfile(final RecipientIdentifier.Single recipient) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Group> getGroups() {
        final var groups = signal.listGroups();
        return groups.stream().map(Signal.StructGroup::getObjectPath).map(this::getGroup).toList();
    }

    @Override
    public SendGroupMessageResults quitGroup(
            final GroupId groupId, final Set<RecipientIdentifier.Single> groupAdmins
    ) throws GroupNotFoundException, IOException, NotAGroupMemberException, LastGroupAdminException {
        if (groupAdmins.size() > 0) {
            throw new UnsupportedOperationException();
        }
        final var group = getRemoteObject(signal.getGroup(groupId.serialize()), Signal.Group.class);
        group.quitGroup();
        return new SendGroupMessageResults(0, List.of());
    }

    @Override
    public void deleteGroup(final GroupId groupId) throws IOException {
        final var group = getRemoteObject(signal.getGroup(groupId.serialize()), Signal.Group.class);
        group.deleteGroup();
    }

    @Override
    public Pair<GroupId, SendGroupMessageResults> createGroup(
            final String name, final Set<RecipientIdentifier.Single> members, final String avatarFile
    ) throws IOException, AttachmentInvalidException {
        final var newGroupId = signal.createGroup(emptyIfNull(name),
                members.stream().map(RecipientIdentifier.Single::getIdentifier).toList(),
                avatarFile == null ? "" : avatarFile);
        return new Pair<>(GroupId.unknownVersion(newGroupId), new SendGroupMessageResults(0, List.of()));
    }

    @Override
    public SendGroupMessageResults updateGroup(
            final GroupId groupId, final UpdateGroup updateGroup
    ) throws IOException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException, GroupSendingNotAllowedException {
        final var group = getRemoteObject(signal.getGroup(groupId.serialize()), Signal.Group.class);
        if (updateGroup.getName() != null) {
            group.Set("org.asamk.Signal.Group", "Name", updateGroup.getName());
        }
        if (updateGroup.getDescription() != null) {
            group.Set("org.asamk.Signal.Group", "Description", updateGroup.getDescription());
        }
        if (updateGroup.getAvatarFile() != null) {
            group.Set("org.asamk.Signal.Group",
                    "Avatar",
                    updateGroup.getAvatarFile() == null ? "" : updateGroup.getAvatarFile());
        }
        if (updateGroup.getExpirationTimer() != null) {
            group.Set("org.asamk.Signal.Group", "MessageExpirationTimer", updateGroup.getExpirationTimer());
        }
        if (updateGroup.getAddMemberPermission() != null) {
            group.Set("org.asamk.Signal.Group", "PermissionAddMember", updateGroup.getAddMemberPermission().name());
        }
        if (updateGroup.getEditDetailsPermission() != null) {
            group.Set("org.asamk.Signal.Group", "PermissionEditDetails", updateGroup.getEditDetailsPermission().name());
        }
        if (updateGroup.getIsAnnouncementGroup() != null) {
            group.Set("org.asamk.Signal.Group",
                    "PermissionSendMessage",
                    updateGroup.getIsAnnouncementGroup()
                            ? GroupPermission.ONLY_ADMINS.name()
                            : GroupPermission.EVERY_MEMBER.name());
        }
        if (updateGroup.getMembers() != null) {
            group.addMembers(updateGroup.getMembers().stream().map(RecipientIdentifier.Single::getIdentifier).toList());
        }
        if (updateGroup.getRemoveMembers() != null) {
            group.removeMembers(updateGroup.getRemoveMembers()
                    .stream()
                    .map(RecipientIdentifier.Single::getIdentifier)
                    .toList());
        }
        if (updateGroup.getAdmins() != null) {
            group.addAdmins(updateGroup.getAdmins().stream().map(RecipientIdentifier.Single::getIdentifier).toList());
        }
        if (updateGroup.getRemoveAdmins() != null) {
            group.removeAdmins(updateGroup.getRemoveAdmins()
                    .stream()
                    .map(RecipientIdentifier.Single::getIdentifier)
                    .toList());
        }
        if (updateGroup.isResetGroupLink()) {
            group.resetLink();
        }
        if (updateGroup.getGroupLinkState() != null) {
            switch (updateGroup.getGroupLinkState()) {
                case DISABLED -> group.disableLink();
                case ENABLED -> group.enableLink(false);
                case ENABLED_WITH_APPROVAL -> group.enableLink(true);
            }
        }
        return new SendGroupMessageResults(0, List.of());
    }

    @Override
    public Pair<GroupId, SendGroupMessageResults> joinGroup(final GroupInviteLinkUrl inviteLinkUrl) throws IOException, InactiveGroupLinkException {
        final var newGroupId = signal.joinGroup(inviteLinkUrl.getUrl());
        return new Pair<>(GroupId.unknownVersion(newGroupId), new SendGroupMessageResults(0, List.of()));
    }

    @Override
    public SendMessageResults sendTypingMessage(
            final TypingAction action, final Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        return handleMessage(recipients, numbers -> {
            numbers.forEach(n -> signal.sendTyping(n, action == TypingAction.STOP));
            return 0L;
        }, () -> {
            signal.sendTyping(signal.getSelfNumber(), action == TypingAction.STOP);
            return 0L;
        }, groupId -> {
            signal.sendGroupTyping(groupId, action == TypingAction.STOP);
            return 0L;
        });
    }

    @Override
    public SendMessageResults sendReadReceipt(
            final RecipientIdentifier.Single sender, final List<Long> messageIds
    ) {
        signal.sendReadReceipt(sender.getIdentifier(), messageIds);
        return new SendMessageResults(0, Map.of());
    }

    @Override
    public SendMessageResults sendViewedReceipt(
            final RecipientIdentifier.Single sender, final List<Long> messageIds
    ) {
        signal.sendViewedReceipt(sender.getIdentifier(), messageIds);
        return new SendMessageResults(0, Map.of());
    }

    @Override
    public SendMessageResults sendMessage(
            final Message message, final Set<RecipientIdentifier> recipients
    ) throws IOException, AttachmentInvalidException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        return handleMessage(recipients,
                numbers -> signal.sendMessage(message.messageText(), message.attachments(), numbers),
                () -> signal.sendNoteToSelfMessage(message.messageText(), message.attachments()),
                groupId -> signal.sendGroupMessage(message.messageText(), message.attachments(), groupId));
    }

    @Override
    public SendMessageResults sendRemoteDeleteMessage(
            final long targetSentTimestamp, final Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        return handleMessage(recipients,
                numbers -> signal.sendRemoteDeleteMessage(targetSentTimestamp, numbers),
                () -> signal.sendRemoteDeleteMessage(targetSentTimestamp, signal.getSelfNumber()),
                groupId -> signal.sendGroupRemoteDeleteMessage(targetSentTimestamp, groupId));
    }

    @Override
    public SendMessageResults sendMessageReaction(
            final String emoji,
            final boolean remove,
            final RecipientIdentifier.Single targetAuthor,
            final long targetSentTimestamp,
            final Set<RecipientIdentifier> recipients,
            final boolean isStory
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        return handleMessage(recipients,
                numbers -> signal.sendMessageReaction(emoji,
                        remove,
                        targetAuthor.getIdentifier(),
                        targetSentTimestamp,
                        numbers),
                () -> signal.sendMessageReaction(emoji,
                        remove,
                        targetAuthor.getIdentifier(),
                        targetSentTimestamp,
                        signal.getSelfNumber()),
                groupId -> signal.sendGroupMessageReaction(emoji,
                        remove,
                        targetAuthor.getIdentifier(),
                        targetSentTimestamp,
                        groupId));
    }

    @Override
    public SendMessageResults sendPaymentNotificationMessage(
            final byte[] receipt, final String note, final RecipientIdentifier.Single recipient
    ) throws IOException {
        final var timestamp = signal.sendPaymentNotification(receipt, note, recipient.getIdentifier());
        return new SendMessageResults(timestamp, Map.of());
    }

    @Override
    public SendMessageResults sendEndSessionMessage(final Set<RecipientIdentifier.Single> recipients) throws IOException {
        signal.sendEndSessionMessage(recipients.stream().map(RecipientIdentifier.Single::getIdentifier).toList());
        return new SendMessageResults(0, Map.of());
    }

    @Override
    public void deleteRecipient(final RecipientIdentifier.Single recipient) {
        signal.deleteRecipient(recipient.getIdentifier());
    }

    @Override
    public void deleteContact(final RecipientIdentifier.Single recipient) {
        signal.deleteContact(recipient.getIdentifier());
    }

    @Override
    public void setContactName(
            final RecipientIdentifier.Single recipient, final String givenName, final String familyName
    ) throws NotPrimaryDeviceException {
        signal.setContactName(recipient.getIdentifier(), givenName);
    }

    @Override
    public void setContactsBlocked(
            final Collection<RecipientIdentifier.Single> recipients, final boolean blocked
    ) throws NotPrimaryDeviceException, IOException {
        for (final var recipient : recipients) {
            signal.setContactBlocked(recipient.getIdentifier(), blocked);
        }
    }

    @Override
    public void setGroupsBlocked(
            final Collection<GroupId> groupIds, final boolean blocked
    ) throws GroupNotFoundException, IOException {
        for (final var groupId : groupIds) {
            setGroupProperty(groupId, "IsBlocked", blocked);
        }
    }

    private void setGroupProperty(final GroupId groupId, final String propertyName, final boolean blocked) {
        final var group = getRemoteObject(signal.getGroup(groupId.serialize()), Signal.Group.class);
        group.Set("org.asamk.Signal.Group", propertyName, blocked);
    }

    @Override
    public void setExpirationTimer(
            final RecipientIdentifier.Single recipient, final int messageExpirationTimer
    ) throws IOException {
        signal.setExpirationTimer(recipient.getIdentifier(), messageExpirationTimer);
    }

    @Override
    public StickerPackUrl uploadStickerPack(final File path) throws IOException, StickerPackInvalidException {
        try {
            return StickerPackUrl.fromUri(new URI(signal.uploadStickerPack(path.getPath())));
        } catch (URISyntaxException | StickerPackUrl.InvalidStickerPackLinkException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public List<StickerPack> getStickerPacks() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void requestAllSyncData() throws IOException {
        signal.sendSyncRequest();
    }

    @Override
    public void addReceiveHandler(final ReceiveMessageHandler handler, final boolean isWeakListener) {
        synchronized (messageHandlers) {
            if (isWeakListener) {
                weakHandlers.add(handler);
            } else {
                if (messageHandlers.size() == 0) {
                    installMessageHandlers();
                }
                messageHandlers.add(handler);
            }
        }
    }

    @Override
    public void removeReceiveHandler(final ReceiveMessageHandler handler) {
        synchronized (messageHandlers) {
            weakHandlers.remove(handler);
            messageHandlers.remove(handler);
            if (messageHandlers.size() == 0) {
                uninstallMessageHandlers();
            }
        }
    }

    @Override
    public boolean isReceiving() {
        synchronized (messageHandlers) {
            return messageHandlers.size() > 0;
        }
    }

    @Override
    public void receiveMessages(
            Optional<Duration> timeout, Optional<Integer> maxMessages, ReceiveMessageHandler handler
    ) throws IOException {
        final var remainingMessages = new AtomicInteger(maxMessages.orElse(-1));
        final var lastMessage = new AtomicLong(System.currentTimeMillis());
        final var thread = Thread.currentThread();

        final ReceiveMessageHandler receiveHandler = (envelope, e) -> {
            lastMessage.set(System.currentTimeMillis());
            handler.handleMessage(envelope, e);
            if (remainingMessages.get() > 0) {
                if (remainingMessages.decrementAndGet() <= 0) {
                    remainingMessages.set(0);
                    thread.interrupt();
                }
            }
        };
        addReceiveHandler(receiveHandler);
        if (timeout.isPresent()) {
            while (remainingMessages.get() != 0) {
                try {
                    final var passedTime = System.currentTimeMillis() - lastMessage.get();
                    final var sleepTimeRemaining = timeout.get().toMillis() - passedTime;
                    if (sleepTimeRemaining < 0) {
                        break;
                    }
                    Thread.sleep(sleepTimeRemaining);
                } catch (InterruptedException ignored) {
                }
            }
        } else {
            try {
                synchronized (this) {
                    this.wait();
                }
            } catch (InterruptedException ignored) {
            }
        }

        removeReceiveHandler(receiveHandler);
    }

    @Override
    public void setReceiveConfig(final ReceiveConfig receiveConfig) {
    }

    @Override
    public boolean hasCaughtUpWithOldMessages() {
        return true;
    }

    @Override
    public boolean isContactBlocked(final RecipientIdentifier.Single recipient) {
        return signal.isContactBlocked(recipient.getIdentifier());
    }

    @Override
    public void sendContacts() throws IOException {
        signal.sendContacts();
    }

    @Override
    public List<Recipient> getRecipients(
            final boolean onlyContacts,
            final Optional<Boolean> blocked,
            final Collection<RecipientIdentifier.Single> addresses,
            final Optional<String> name
    ) {
        final var numbers = addresses.stream()
                .filter(s -> s instanceof RecipientIdentifier.Number)
                .map(s -> ((RecipientIdentifier.Number) s).number())
                .collect(Collectors.toSet());
        return signal.listNumbers().stream().filter(n -> addresses.isEmpty() || numbers.contains(n)).map(n -> {
            final var contactBlocked = signal.isContactBlocked(n);
            if (blocked.isPresent() && blocked.get() != contactBlocked) {
                return null;
            }
            final var contactName = signal.getContactName(n);
            if (onlyContacts && contactName.length() == 0) {
                return null;
            }
            if (name.isPresent() && !name.get().equals(contactName)) {
                return null;
            }
            return Recipient.newBuilder()
                    .withAddress(new RecipientAddress(null, n))
                    .withContact(new Contact(contactName, null, null, 0, contactBlocked, false, false))
                    .build();
        }).filter(Objects::nonNull).toList();
    }

    @Override
    public String getContactOrProfileName(final RecipientIdentifier.Single recipient) {
        return signal.getContactName(recipient.getIdentifier());
    }

    @Override
    public Group getGroup(final GroupId groupId) {
        final var groupPath = signal.getGroup(groupId.serialize());
        return getGroup(groupPath);
    }

    @SuppressWarnings("unchecked")
    private Group getGroup(final DBusPath groupPath) {
        final var group = getRemoteObject(groupPath, Signal.Group.class).GetAll("org.asamk.Signal.Group");
        final var id = (byte[]) group.get("Id").getValue();
        try {
            return new Group(GroupId.unknownVersion(id),
                    (String) group.get("Name").getValue(),
                    (String) group.get("Description").getValue(),
                    GroupInviteLinkUrl.fromUri((String) group.get("GroupInviteLink").getValue()),
                    ((List<String>) group.get("Members").getValue()).stream()
                            .map(m -> new RecipientAddress(null, m))
                            .collect(Collectors.toSet()),
                    ((List<String>) group.get("PendingMembers").getValue()).stream()
                            .map(m -> new RecipientAddress(null, m))
                            .collect(Collectors.toSet()),
                    ((List<String>) group.get("RequestingMembers").getValue()).stream()
                            .map(m -> new RecipientAddress(null, m))
                            .collect(Collectors.toSet()),
                    ((List<String>) group.get("Admins").getValue()).stream()
                            .map(m -> new RecipientAddress(null, m))
                            .collect(Collectors.toSet()),
                    ((List<String>) group.get("Banned").getValue()).stream()
                            .map(m -> new RecipientAddress(null, m))
                            .collect(Collectors.toSet()),
                    (boolean) group.get("IsBlocked").getValue(),
                    (int) group.get("MessageExpirationTimer").getValue(),
                    GroupPermission.valueOf((String) group.get("PermissionAddMember").getValue()),
                    GroupPermission.valueOf((String) group.get("PermissionEditDetails").getValue()),
                    GroupPermission.valueOf((String) group.get("PermissionSendMessage").getValue()),
                    (boolean) group.get("IsMember").getValue(),
                    (boolean) group.get("IsAdmin").getValue());
        } catch (GroupInviteLinkUrl.InvalidGroupLinkException | GroupInviteLinkUrl.UnknownGroupLinkVersionException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public List<Identity> getIdentities() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Identity> getIdentities(final RecipientIdentifier.Single recipient) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean trustIdentityVerified(final RecipientIdentifier.Single recipient, final byte[] fingerprint) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean trustIdentityVerifiedSafetyNumber(
            final RecipientIdentifier.Single recipient, final String safetyNumber
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean trustIdentityVerifiedSafetyNumber(
            final RecipientIdentifier.Single recipient, final byte[] safetyNumber
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean trustIdentityAllKeys(final RecipientIdentifier.Single recipient) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addAddressChangedListener(final Runnable listener) {
    }

    @Override
    public void addClosedListener(final Runnable listener) {
        synchronized (closedListeners) {
            closedListeners.add(listener);
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            this.notify();
        }
        synchronized (messageHandlers) {
            if (messageHandlers.size() > 0) {
                uninstallMessageHandlers();
            }
            weakHandlers.clear();
            messageHandlers.clear();
        }
        synchronized (closedListeners) {
            closedListeners.forEach(Runnable::run);
            closedListeners.clear();
        }
    }

    private SendMessageResults handleMessage(
            Set<RecipientIdentifier> recipients,
            Function<List<String>, Long> recipientsHandler,
            Supplier<Long> noteToSelfHandler,
            Function<byte[], Long> groupHandler
    ) {
        long timestamp = 0;
        final var singleRecipients = recipients.stream()
                .filter(r -> r instanceof RecipientIdentifier.Single)
                .map(RecipientIdentifier.Single.class::cast)
                .map(RecipientIdentifier.Single::getIdentifier)
                .toList();
        if (singleRecipients.size() > 0) {
            timestamp = recipientsHandler.apply(singleRecipients);
        }

        if (recipients.contains(RecipientIdentifier.NoteToSelf.INSTANCE)) {
            timestamp = noteToSelfHandler.get();
        }
        final var groupRecipients = recipients.stream()
                .filter(r -> r instanceof RecipientIdentifier.Group)
                .map(RecipientIdentifier.Group.class::cast)
                .map(RecipientIdentifier.Group::groupId)
                .toList();
        for (final var groupId : groupRecipients) {
            timestamp = groupHandler.apply(groupId.serialize());
        }
        return new SendMessageResults(timestamp, Map.of());
    }

    private String emptyIfNull(final String string) {
        return string == null ? "" : string;
    }

    private <T extends DBusInterface> T getRemoteObject(final DBusPath path, final Class<T> type) {
        try {
            return connection.getRemoteObject(DbusConfig.getBusname(), path.getPath(), type);
        } catch (DBusException e) {
            throw new AssertionError(e);
        }
    }

    private void installMessageHandlers() {
        try {
            this.dbusMsgHandler = messageReceived -> {
                final var extras = messageReceived.getExtras();
                final var envelope = new MessageEnvelope(Optional.of(new RecipientAddress(null,
                        messageReceived.getSender())),
                        0,
                        messageReceived.getTimestamp(),
                        0,
                        0,
                        false,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(new MessageEnvelope.Data(messageReceived.getTimestamp(),
                                messageReceived.getGroupId().length > 0
                                        ? Optional.of(new MessageEnvelope.Data.GroupContext(GroupId.unknownVersion(
                                        messageReceived.getGroupId()), false, 0))
                                        : Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(messageReceived.getMessage()),
                                0,
                                false,
                                false,
                                false,
                                false,
                                false,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                getAttachments(extras),
                                Optional.empty(),
                                Optional.empty(),
                                List.of(),
                                List.of(),
                                List.of())),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty());
                notifyMessageHandlers(envelope);
            };
            connection.addSigHandler(Signal.MessageReceivedV2.class, signal, this.dbusMsgHandler);

            this.dbusRcptHandler = receiptReceived -> {
                final var type = switch (receiptReceived.getReceiptType()) {
                    case "read" -> MessageEnvelope.Receipt.Type.READ;
                    case "viewed" -> MessageEnvelope.Receipt.Type.VIEWED;
                    case "delivery" -> MessageEnvelope.Receipt.Type.DELIVERY;
                    default -> MessageEnvelope.Receipt.Type.UNKNOWN;
                };
                final var envelope = new MessageEnvelope(Optional.of(new RecipientAddress(null,
                        receiptReceived.getSender())),
                        0,
                        receiptReceived.getTimestamp(),
                        0,
                        0,
                        false,
                        Optional.of(new MessageEnvelope.Receipt(receiptReceived.getTimestamp(),
                                type,
                                List.of(receiptReceived.getTimestamp()))),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty());
                notifyMessageHandlers(envelope);
            };
            connection.addSigHandler(Signal.ReceiptReceivedV2.class, signal, this.dbusRcptHandler);

            this.dbusSyncHandler = syncReceived -> {
                final var extras = syncReceived.getExtras();
                final var envelope = new MessageEnvelope(Optional.of(new RecipientAddress(null,
                        syncReceived.getSource())),
                        0,
                        syncReceived.getTimestamp(),
                        0,
                        0,
                        false,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(new MessageEnvelope.Sync(Optional.of(new MessageEnvelope.Sync.Sent(syncReceived.getTimestamp(),
                                syncReceived.getTimestamp(),
                                syncReceived.getDestination().isEmpty()
                                        ? Optional.empty()
                                        : Optional.of(new RecipientAddress(null, syncReceived.getDestination())),
                                Set.of(),
                                Optional.of(new MessageEnvelope.Data(syncReceived.getTimestamp(),
                                        syncReceived.getGroupId().length > 0
                                                ? Optional.of(new MessageEnvelope.Data.GroupContext(GroupId.unknownVersion(
                                                syncReceived.getGroupId()), false, 0))
                                                : Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(syncReceived.getMessage()),
                                        0,
                                        false,
                                        false,
                                        false,
                                        false,
                                        false,
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        getAttachments(extras),
                                        Optional.empty(),
                                        Optional.empty(),
                                        List.of(),
                                        List.of(),
                                        List.of())),
                                Optional.empty())),
                                Optional.empty(),
                                List.of(),
                                List.of(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty())),
                        Optional.empty(),
                        Optional.empty());
                notifyMessageHandlers(envelope);
            };
            connection.addSigHandler(Signal.SyncMessageReceivedV2.class, signal, this.dbusSyncHandler);
        } catch (DBusException e) {
            e.printStackTrace();
        }
        signal.subscribeReceive();
    }

    private void notifyMessageHandlers(final MessageEnvelope envelope) {
        synchronized (messageHandlers) {
            Stream.concat(messageHandlers.stream(), weakHandlers.stream())
                    .forEach(h -> h.handleMessage(envelope, null));
        }
    }

    private void uninstallMessageHandlers() {
        try {
            signal.unsubscribeReceive();
            connection.removeSigHandler(Signal.MessageReceivedV2.class, signal, this.dbusMsgHandler);
            connection.removeSigHandler(Signal.ReceiptReceivedV2.class, signal, this.dbusRcptHandler);
            connection.removeSigHandler(Signal.SyncMessageReceivedV2.class, signal, this.dbusSyncHandler);
        } catch (DBusException e) {
            e.printStackTrace();
        }
    }

    private List<MessageEnvelope.Data.Attachment> getAttachments(final Map<String, Variant<?>> extras) {
        if (!extras.containsKey("attachments")) {
            return List.of();
        }

        final List<DBusMap<String, Variant<?>>> attachments = getValue(extras, "attachments");
        return attachments.stream().map(a -> {
            final String file = a.containsKey("file") ? getValue(a, "file") : null;
            return new MessageEnvelope.Data.Attachment(a.containsKey("remoteId")
                    ? Optional.of(getValue(a, "remoteId"))
                    : Optional.empty(),
                    file != null ? Optional.of(new File(file)) : Optional.empty(),
                    Optional.empty(),
                    getValue(a, "contentType"),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    getValue(a, "isVoiceNote"),
                    getValue(a, "isGif"),
                    getValue(a, "isBorderless"));
        }).toList();
    }

    @Override
    public InputStream retrieveAttachment(final String id) throws IOException {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    private <T> T getValue(
            final Map<String, Variant<?>> stringVariantMap, final String field
    ) {
        return (T) stringVariantMap.get(field).getValue();
    }
}

package org.asamk.signal.dbus;

import org.asamk.Signal;
import org.asamk.signal.DbusConfig;
import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotMasterDeviceException;
import org.asamk.signal.manager.StickerPackInvalidException;
import org.asamk.signal.manager.UntrustedIdentityException;
import org.asamk.signal.manager.api.Device;
import org.asamk.signal.manager.api.Group;
import org.asamk.signal.manager.api.Identity;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.SendGroupMessageResults;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupLinkState;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupPermission;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.LastGroupAdminException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.storage.recipients.Contact;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This class implements the Manager interface using the DBus Signal interface, where possible.
 * It's used for the signal-cli dbus client mode (--dbus, --dbus-system)
 */
public class DbusManagerImpl implements Manager {

    private final Signal signal;
    private final DBusConnection connection;

    public DbusManagerImpl(final Signal signal, DBusConnection connection) {
        this.signal = signal;
        this.connection = connection;
    }

    @Override
    public String getSelfNumber() {
        return signal.getSelfNumber();
    }

    @Override
    public void checkAccountState() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Pair<String, UUID>> areUsersRegistered(final Set<String> numbers) throws IOException {
        final var numbersList = new ArrayList<>(numbers);
        final var registered = signal.isRegistered(numbersList);

        final var result = new HashMap<String, Pair<String, UUID>>();
        for (var i = 0; i < numbersList.size(); i++) {
            result.put(numbersList.get(i),
                    new Pair<>(numbersList.get(i), registered.get(i) ? UuidUtil.UNKNOWN_UUID : null));
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
    public void updateConfiguration(
            final Boolean readReceipts,
            final Boolean unidentifiedDeliveryIndicators,
            final Boolean typingIndicators,
            final Boolean linkPreviews
    ) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setProfile(
            final String givenName,
            final String familyName,
            final String about,
            final String aboutEmoji,
            final Optional<File> avatar
    ) throws IOException {
        signal.updateProfile(emptyIfNull(givenName),
                emptyIfNull(familyName),
                emptyIfNull(about),
                emptyIfNull(aboutEmoji),
                avatar == null ? "" : avatar.transform(File::getPath).or(""),
                avatar != null && !avatar.isPresent());
    }

    @Override
    public void unregister() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteAccount() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void submitRateLimitRecaptchaChallenge(final String challenge, final String captcha) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Device> getLinkedDevices() throws IOException {
        final var thisDevice = signal.getThisDevice();
        return signal.listDevices().stream().map(d -> {
            final var device = getRemoteObject(d.getObjectPath(),
                    Signal.Device.class).GetAll("org.asamk.Signal.Device");
            return new Device((long) device.get("Id").getValue(),
                    (String) device.get("Name").getValue(),
                    (long) device.get("Created").getValue(),
                    (long) device.get("LastSeen").getValue(),
                    thisDevice.equals(d.getObjectPath()));
        }).collect(Collectors.toList());
    }

    @Override
    public void removeLinkedDevices(final long deviceId) throws IOException {
        final var devicePath = signal.getDevice(deviceId);
        getRemoteObject(devicePath, Signal.Device.class).removeDevice();
    }

    @Override
    public void addDeviceLink(final URI linkUri) throws IOException, InvalidKeyException {
        signal.addDevice(linkUri.toString());
    }

    @Override
    public void setRegistrationLockPin(final Optional<String> pin) throws IOException, UnauthenticatedResponseException {
        if (pin.isPresent()) {
            signal.setPin(pin.get());
        } else {
            signal.removePin();
        }
    }

    @Override
    public Profile getRecipientProfile(final RecipientIdentifier.Single recipient) throws UnregisteredUserException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Group> getGroups() {
        final var groupIds = signal.getGroupIds();
        return groupIds.stream().map(id -> getGroup(GroupId.unknownVersion(id))).collect(Collectors.toList());
    }

    @Override
    public SendGroupMessageResults quitGroup(
            final GroupId groupId, final Set<RecipientIdentifier.Single> groupAdmins
    ) throws GroupNotFoundException, IOException, NotAGroupMemberException, LastGroupAdminException {
        if (groupAdmins.size() > 0) {
            throw new UnsupportedOperationException();
        }
        signal.quitGroup(groupId.serialize());
        return new SendGroupMessageResults(0, List.of());
    }

    @Override
    public void deleteGroup(final GroupId groupId) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Pair<GroupId, SendGroupMessageResults> createGroup(
            final String name, final Set<RecipientIdentifier.Single> members, final File avatarFile
    ) throws IOException, AttachmentInvalidException {
        final var newGroupId = signal.updateGroup(new byte[0],
                emptyIfNull(name),
                members.stream().map(RecipientIdentifier.Single::getIdentifier).collect(Collectors.toList()),
                avatarFile == null ? "" : avatarFile.getPath());
        return new Pair<>(GroupId.unknownVersion(newGroupId), new SendGroupMessageResults(0, List.of()));
    }

    @Override
    public SendGroupMessageResults updateGroup(
            final GroupId groupId,
            final String name,
            final String description,
            final Set<RecipientIdentifier.Single> members,
            final Set<RecipientIdentifier.Single> removeMembers,
            final Set<RecipientIdentifier.Single> admins,
            final Set<RecipientIdentifier.Single> removeAdmins,
            final boolean resetGroupLink,
            final GroupLinkState groupLinkState,
            final GroupPermission addMemberPermission,
            final GroupPermission editDetailsPermission,
            final File avatarFile,
            final Integer expirationTimer,
            final Boolean isAnnouncementGroup
    ) throws IOException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException, GroupSendingNotAllowedException {
        signal.updateGroup(groupId.serialize(),
                emptyIfNull(name),
                members.stream().map(RecipientIdentifier.Single::getIdentifier).collect(Collectors.toList()),
                avatarFile == null ? "" : avatarFile.getPath());
        return new SendGroupMessageResults(0, List.of());
    }

    @Override
    public Pair<GroupId, SendGroupMessageResults> joinGroup(final GroupInviteLinkUrl inviteLinkUrl) throws IOException, GroupLinkNotActiveException {
        final var newGroupId = signal.joinGroup(inviteLinkUrl.getUrl());
        return new Pair<>(GroupId.unknownVersion(newGroupId), new SendGroupMessageResults(0, List.of()));
    }

    @Override
    public void sendTypingMessage(
            final TypingAction action, final Set<RecipientIdentifier> recipients
    ) throws IOException, UntrustedIdentityException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        for (final var recipient : recipients) {
            if (recipient instanceof RecipientIdentifier.Single) {
                signal.sendTyping(((RecipientIdentifier.Single) recipient).getIdentifier(),
                        action == TypingAction.STOP);
            } else if (recipient instanceof RecipientIdentifier.Group) {
                throw new UnsupportedOperationException();
            }
        }
    }

    @Override
    public void sendReadReceipt(
            final RecipientIdentifier.Single sender, final List<Long> messageIds
    ) throws IOException, UntrustedIdentityException {
        signal.sendReadReceipt(sender.getIdentifier(), messageIds);
    }

    @Override
    public void sendViewedReceipt(
            final RecipientIdentifier.Single sender, final List<Long> messageIds
    ) throws IOException, UntrustedIdentityException {
        throw new UnsupportedOperationException();
    }

    @Override
    public SendMessageResults sendMessage(
            final Message message, final Set<RecipientIdentifier> recipients
    ) throws IOException, AttachmentInvalidException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        return handleMessage(recipients,
                numbers -> signal.sendMessage(message.getMessageText(), message.getAttachments(), numbers),
                () -> signal.sendNoteToSelfMessage(message.getMessageText(), message.getAttachments()),
                groupId -> signal.sendGroupMessage(message.getMessageText(), message.getAttachments(), groupId));
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
            final Set<RecipientIdentifier> recipients
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
    public SendMessageResults sendEndSessionMessage(final Set<RecipientIdentifier.Single> recipients) throws IOException {
        signal.sendEndSessionMessage(recipients.stream()
                .map(RecipientIdentifier.Single::getIdentifier)
                .collect(Collectors.toList()));
        return new SendMessageResults(0, Map.of());
    }

    @Override
    public void setContactName(
            final RecipientIdentifier.Single recipient, final String name
    ) throws NotMasterDeviceException, UnregisteredUserException {
        signal.setContactName(recipient.getIdentifier(), name);
    }

    @Override
    public void setContactBlocked(
            final RecipientIdentifier.Single recipient, final boolean blocked
    ) throws NotMasterDeviceException, IOException {
        signal.setContactBlocked(recipient.getIdentifier(), blocked);
    }

    @Override
    public void setGroupBlocked(
            final GroupId groupId, final boolean blocked
    ) throws GroupNotFoundException, IOException {
        signal.setGroupBlocked(groupId.serialize(), blocked);
    }

    @Override
    public void setExpirationTimer(
            final RecipientIdentifier.Single recipient, final int messageExpirationTimer
    ) throws IOException {
        signal.setExpirationTimer(recipient.getIdentifier(), messageExpirationTimer);
    }

    @Override
    public URI uploadStickerPack(final File path) throws IOException, StickerPackInvalidException {
        try {
            return new URI(signal.uploadStickerPack(path.getPath()));
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public void requestAllSyncData() throws IOException {
        signal.sendSyncRequest();
    }

    @Override
    public void receiveMessages(
            final long timeout,
            final TimeUnit unit,
            final boolean returnOnTimeout,
            final boolean ignoreAttachments,
            final ReceiveMessageHandler handler
    ) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasCaughtUpWithOldMessages() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isContactBlocked(final RecipientIdentifier.Single recipient) {
        return signal.isContactBlocked(recipient.getIdentifier());
    }

    @Override
    public File getAttachmentFile(final SignalServiceAttachmentRemoteId attachmentId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sendContacts() throws IOException {
        signal.sendContacts();
    }

    @Override
    public List<Pair<RecipientAddress, Contact>> getContacts() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getContactOrProfileName(final RecipientIdentifier.Single recipient) {
        return signal.getContactName(recipient.getIdentifier());
    }

    @Override
    public Group getGroup(final GroupId groupId) {
        final var id = groupId.serialize();
        return new Group(groupId,
                signal.getGroupName(id),
                null,
                null,
                signal.getGroupMembers(id).stream().map(m -> new RecipientAddress(null, m)).collect(Collectors.toSet()),
                Set.of(),
                Set.of(),
                Set.of(),
                signal.isGroupBlocked(id),
                0,
                false,
                signal.isMember(id));
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
    public String computeSafetyNumber(
            final SignalServiceAddress theirAddress, final IdentityKey theirIdentityKey
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SignalServiceAddress resolveSignalServiceAddress(final SignalServiceAddress address) {
        return address;
    }

    @Override
    public void close() throws IOException {
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
                .collect(Collectors.toList());
        if (singleRecipients.size() > 0) {
            timestamp = recipientsHandler.apply(singleRecipients);
        }

        if (recipients.contains(RecipientIdentifier.NoteToSelf.INSTANCE)) {
            timestamp = noteToSelfHandler.get();
        }
        final var groupRecipients = recipients.stream()
                .filter(r -> r instanceof RecipientIdentifier.Group)
                .map(RecipientIdentifier.Group.class::cast)
                .map(g -> g.groupId)
                .collect(Collectors.toList());
        for (final var groupId : groupRecipients) {
            timestamp = groupHandler.apply(groupId.serialize());
        }
        return new SendMessageResults(timestamp, Map.of());
    }

    private String emptyIfNull(final String string) {
        return string == null ? "" : string;
    }

    private <T extends DBusInterface> T getRemoteObject(final DBusPath devicePath, final Class<T> type) {
        try {
            return connection.getRemoteObject(DbusConfig.getBusname(), devicePath.getPath(), type);
        } catch (DBusException e) {
            throw new AssertionError(e);
        }
    }
}

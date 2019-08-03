/*
  Copyright (C) 2015-2018 AsamK

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.asamk.signal.manager;

import org.asamk.Signal;
import org.asamk.signal.*;
import org.asamk.signal.storage.SignalAccount;
import org.asamk.signal.storage.contacts.ContactInfo;
import org.asamk.signal.storage.groups.GroupInfo;
import org.asamk.signal.storage.groups.JsonGroupStore;
import org.asamk.signal.storage.protocol.JsonIdentityKeyStore;
import org.asamk.signal.storage.threads.ThreadInfo;
import org.asamk.signal.util.IOUtils;
import org.asamk.signal.util.Util;
import org.signal.libsignal.metadata.*;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.Medium;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.*;
import org.whispersystems.signalservice.api.messages.multidevice.*;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageException;
import org.whispersystems.signalservice.internal.util.Base64;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Manager implements Signal {

    private final String settingsPath;
    private final String dataPath;
    private final String attachmentsPath;
    private final String avatarsPath;
    private final SleepTimer timer = new UptimeSleepTimer();

    private SignalAccount account;
    private String username;
    private SignalServiceAccountManager accountManager;
    private SignalServiceMessagePipe messagePipe = null;
    private SignalServiceMessagePipe unidentifiedMessagePipe = null;

    public Manager(String username, String settingsPath) {
        this.username = username;
        this.settingsPath = settingsPath;
        this.dataPath = this.settingsPath + "/data";
        this.attachmentsPath = this.settingsPath + "/attachments";
        this.avatarsPath = this.settingsPath + "/avatars";

    }

    public String getUsername() {
        return username;
    }

    private IdentityKey getIdentity() {
        return account.getSignalProtocolStore().getIdentityKeyPair().getPublicKey();
    }

    public int getDeviceId() {
        return account.getDeviceId();
    }

    private String getMessageCachePath() {
        return this.dataPath + "/" + username + ".d/msg-cache";
    }

    private String getMessageCachePath(String sender) {
        return getMessageCachePath() + "/" + sender.replace("/", "_");
    }

    private File getMessageCacheFile(String sender, long now, long timestamp) throws IOException {
        String cachePath = getMessageCachePath(sender);
        IOUtils.createPrivateDirectories(cachePath);
        return new File(cachePath + "/" + now + "_" + timestamp);
    }

    public boolean userHasKeys() {
        return account != null && account.getSignalProtocolStore() != null;
    }

    public void init() throws IOException {
        if (!SignalAccount.userExists(dataPath, username)) {
            return;
        }
        account = SignalAccount.load(dataPath, username);

        migrateLegacyConfigs();

        accountManager = new SignalServiceAccountManager(BaseConfig.serviceConfiguration, username, account.getPassword(), account.getDeviceId(), BaseConfig.USER_AGENT, timer);
        try {
            if (account.isRegistered() && accountManager.getPreKeysCount() < BaseConfig.PREKEY_MINIMUM_COUNT) {
                refreshPreKeys();
                account.save();
            }
        } catch (AuthorizationFailedException e) {
            System.err.println("Authorization failed, was the number registered elsewhere?");
            throw e;
        }
    }

    private void migrateLegacyConfigs() {
        // Copy group avatars that were previously stored in the attachments folder
        // to the new avatar folder
        if (JsonGroupStore.groupsWithLegacyAvatarId.size() > 0) {
            for (GroupInfo g : JsonGroupStore.groupsWithLegacyAvatarId) {
                File avatarFile = getGroupAvatarFile(g.groupId);
                File attachmentFile = getAttachmentFile(g.getAvatarId());
                if (!avatarFile.exists() && attachmentFile.exists()) {
                    try {
                        IOUtils.createPrivateDirectories(avatarsPath);
                        Files.copy(attachmentFile.toPath(), avatarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            JsonGroupStore.groupsWithLegacyAvatarId.clear();
            account.save();
        }
        if (account.getProfileKey() == null) {
            // Old config file, creating new profile key
            account.setProfileKey(KeyUtils.createProfileKey());
            account.save();
        }
    }

    private void createNewIdentity() throws IOException {
        IdentityKeyPair identityKey = KeyHelper.generateIdentityKeyPair();
        int registrationId = KeyHelper.generateRegistrationId(false);
        if (username == null) {
            account = SignalAccount.createTemporaryAccount(identityKey, registrationId);
        } else {
            byte[] profileKey = KeyUtils.createProfileKey();
            account = SignalAccount.create(dataPath, username, identityKey, registrationId, profileKey);
            account.save();
        }
    }

    public boolean isRegistered() {
        return account != null && account.isRegistered();
    }

    public void register(boolean voiceVerification) throws IOException {
        if (account == null) {
            createNewIdentity();
        }
        account.setPassword(KeyUtils.createPassword());
        accountManager = new SignalServiceAccountManager(BaseConfig.serviceConfiguration, account.getUsername(), account.getPassword(), BaseConfig.USER_AGENT, timer);

        if (voiceVerification) {
            accountManager.requestVoiceVerificationCode(Locale.getDefault(), Optional.<String>absent(), Optional.<String>absent());
        } else {
            accountManager.requestSmsVerificationCode(false, Optional.<String>absent(), Optional.<String>absent());
        }

        account.setRegistered(false);
        account.save();
    }

    public void updateAccountAttributes() throws IOException {
        accountManager.setAccountAttributes(account.getSignalingKey(), account.getSignalProtocolStore().getLocalRegistrationId(), true, account.getRegistrationLockPin(), getSelfUnidentifiedAccessKey(), false);
    }

    public void unregister() throws IOException {
        // When setting an empty GCM id, the Signal-Server also sets the fetchesMessages property to false.
        // If this is the master device, other users can't send messages to this number anymore.
        // If this is a linked device, other users can still send messages, but this device doesn't receive them anymore.
        accountManager.setGcmId(Optional.<String>absent());

        account.setRegistered(false);
        account.save();
    }

    public String getDeviceLinkUri() throws TimeoutException, IOException {
        if (account == null) {
            createNewIdentity();
        }
        account.setPassword(KeyUtils.createPassword());
        accountManager = new SignalServiceAccountManager(BaseConfig.serviceConfiguration, username, account.getPassword(), BaseConfig.USER_AGENT, timer);
        String uuid = accountManager.getNewDeviceUuid();

        return Utils.createDeviceLinkUri(new Utils.DeviceLinkInfo(uuid, getIdentity().getPublicKey()));
    }

    public void finishDeviceLink(String deviceName) throws IOException, InvalidKeyException, TimeoutException, UserAlreadyExists {
        account.setSignalingKey(KeyUtils.createSignalingKey());
        SignalServiceAccountManager.NewDeviceRegistrationReturn ret = accountManager.finishNewDeviceRegistration(account.getSignalProtocolStore().getIdentityKeyPair(), account.getSignalingKey(), false, true, account.getSignalProtocolStore().getLocalRegistrationId(), deviceName);

        username = ret.getNumber();
        // TODO do this check before actually registering
        if (SignalAccount.userExists(dataPath, username)) {
            throw new UserAlreadyExists(username, SignalAccount.getFileName(dataPath, username));
        }

        // Create new account with the synced identity
        byte[] profileKey = ret.getProfileKey();
        if (profileKey == null) {
            profileKey = KeyUtils.createProfileKey();
        }
        account = SignalAccount.createLinkedAccount(dataPath, username, account.getPassword(), ret.getDeviceId(), ret.getIdentity(), account.getSignalProtocolStore().getLocalRegistrationId(), account.getSignalingKey(), profileKey);

        refreshPreKeys();

        requestSyncGroups();
        requestSyncContacts();
        requestSyncBlocked();
        requestSyncConfiguration();

        account.save();
    }

    public List<DeviceInfo> getLinkedDevices() throws IOException {
        List<DeviceInfo> devices = accountManager.getDevices();
        account.setMultiDevice(devices.size() > 1);
        account.save();
        return devices;
    }

    public void removeLinkedDevices(int deviceId) throws IOException {
        accountManager.removeDevice(deviceId);
        List<DeviceInfo> devices = accountManager.getDevices();
        account.setMultiDevice(devices.size() > 1);
        account.save();
    }

    public void addDeviceLink(URI linkUri) throws IOException, InvalidKeyException {
        Utils.DeviceLinkInfo info = Utils.parseDeviceLinkUri(linkUri);

        addDevice(info.deviceIdentifier, info.deviceKey);
    }

    private void addDevice(String deviceIdentifier, ECPublicKey deviceKey) throws IOException, InvalidKeyException {
        IdentityKeyPair identityKeyPair = account.getSignalProtocolStore().getIdentityKeyPair();
        String verificationCode = accountManager.getNewDeviceVerificationCode();

        accountManager.addDevice(deviceIdentifier, deviceKey, identityKeyPair, Optional.of(account.getProfileKey()), verificationCode);
        account.setMultiDevice(true);
        account.save();
    }

    private List<PreKeyRecord> generatePreKeys() {
        List<PreKeyRecord> records = new ArrayList<>(BaseConfig.PREKEY_BATCH_SIZE);

        final int offset = account.getPreKeyIdOffset();
        for (int i = 0; i < BaseConfig.PREKEY_BATCH_SIZE; i++) {
            int preKeyId = (offset + i) % Medium.MAX_VALUE;
            ECKeyPair keyPair = Curve.generateKeyPair();
            PreKeyRecord record = new PreKeyRecord(preKeyId, keyPair);

            records.add(record);
        }

        account.addPreKeys(records);
        account.save();

        return records;
    }

    private SignedPreKeyRecord generateSignedPreKey(IdentityKeyPair identityKeyPair) {
        try {
            ECKeyPair keyPair = Curve.generateKeyPair();
            byte[] signature = Curve.calculateSignature(identityKeyPair.getPrivateKey(), keyPair.getPublicKey().serialize());
            SignedPreKeyRecord record = new SignedPreKeyRecord(account.getNextSignedPreKeyId(), System.currentTimeMillis(), keyPair, signature);

            account.addSignedPreKey(record);
            account.save();

            return record;
        } catch (InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    public void verifyAccount(String verificationCode, String pin) throws IOException {
        verificationCode = verificationCode.replace("-", "");
        account.setSignalingKey(KeyUtils.createSignalingKey());
        // TODO make unrestricted unidentified access configurable
        accountManager.verifyAccountWithCode(verificationCode, account.getSignalingKey(), account.getSignalProtocolStore().getLocalRegistrationId(), true, pin, getSelfUnidentifiedAccessKey(), false);

        //accountManager.setGcmId(Optional.of(GoogleCloudMessaging.getInstance(this).register(REGISTRATION_ID)));
        account.setRegistered(true);
        account.setRegistrationLockPin(pin);

        refreshPreKeys();
        account.save();
    }

    public void setRegistrationLockPin(Optional<String> pin) throws IOException {
        accountManager.setPin(pin);
        if (pin.isPresent()) {
            account.setRegistrationLockPin(pin.get());
        } else {
            account.setRegistrationLockPin(null);
        }
        account.save();
    }

    private void refreshPreKeys() throws IOException {
        List<PreKeyRecord> oneTimePreKeys = generatePreKeys();
        final IdentityKeyPair identityKeyPair = account.getSignalProtocolStore().getIdentityKeyPair();
        SignedPreKeyRecord signedPreKeyRecord = generateSignedPreKey(identityKeyPair);

        accountManager.setPreKeys(getIdentity(), signedPreKeyRecord, oneTimePreKeys);
    }

    private Optional<SignalServiceAttachmentStream> createGroupAvatarAttachment(byte[] groupId) throws IOException {
        File file = getGroupAvatarFile(groupId);
        if (!file.exists()) {
            return Optional.absent();
        }

        return Optional.of(Utils.createAttachment(file));
    }

    private Optional<SignalServiceAttachmentStream> createContactAvatarAttachment(String number) throws IOException {
        File file = getContactAvatarFile(number);
        if (!file.exists()) {
            return Optional.absent();
        }

        return Optional.of(Utils.createAttachment(file));
    }

    private GroupInfo getGroupForSending(byte[] groupId) throws GroupNotFoundException, NotAGroupMemberException {
        GroupInfo g = account.getGroupStore().getGroup(groupId);
        if (g == null) {
            throw new GroupNotFoundException(groupId);
        }
        for (String member : g.members) {
            if (member.equals(this.username)) {
                return g;
            }
        }
        throw new NotAGroupMemberException(groupId, g.name);
    }

    public List<GroupInfo> getGroups() {
        return account.getGroupStore().getGroups();
    }

    @Override
    public void sendGroupMessage(String messageText, List<String> attachments,
                                 byte[] groupId)
            throws IOException, EncapsulatedExceptions, GroupNotFoundException, AttachmentInvalidException {
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().withBody(messageText);
        if (attachments != null) {
            messageBuilder.withAttachments(Utils.getSignalServiceAttachments(attachments));
        }
        if (groupId != null) {
            SignalServiceGroup group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.DELIVER)
                    .withId(groupId)
                    .build();
            messageBuilder.asGroupMessage(group);
        }
        ThreadInfo thread = account.getThreadStore().getThread(Base64.encodeBytes(groupId));
        if (thread != null) {
            messageBuilder.withExpiration(thread.messageExpirationTime);
        }

        final GroupInfo g = getGroupForSending(groupId);

        // Don't send group message to ourself
        final List<String> membersSend = new ArrayList<>(g.members);
        membersSend.remove(this.username);
        sendMessageLegacy(messageBuilder, membersSend);
    }

    public void sendQuitGroupMessage(byte[] groupId) throws GroupNotFoundException, IOException, EncapsulatedExceptions {
        SignalServiceGroup group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.QUIT)
                .withId(groupId)
                .build();

        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group);

        final GroupInfo g = getGroupForSending(groupId);
        g.members.remove(this.username);
        account.getGroupStore().updateGroup(g);

        sendMessageLegacy(messageBuilder, g.members);
    }

    private byte[] sendUpdateGroupMessage(byte[] groupId, String name, Collection<String> members, String avatarFile) throws IOException, EncapsulatedExceptions, GroupNotFoundException, AttachmentInvalidException {
        GroupInfo g;
        if (groupId == null) {
            // Create new group
            g = new GroupInfo(KeyUtils.createGroupId());
            g.members.add(username);
        } else {
            g = getGroupForSending(groupId);
        }

        if (name != null) {
            g.name = name;
        }

        if (members != null) {
            Set<String> newMembers = new HashSet<>();
            for (String member : members) {
                try {
                    member = Utils.canonicalizeNumber(member, username);
                } catch (InvalidNumberException e) {
                    System.err.println("Failed to add member \"" + member + "\" to group: " + e.getMessage());
                    System.err.println("Aborting…");
                    System.exit(1);
                }
                if (g.members.contains(member)) {
                    continue;
                }
                newMembers.add(member);
                g.members.add(member);
            }
            final List<ContactTokenDetails> contacts = accountManager.getContacts(newMembers);
            if (contacts.size() != newMembers.size()) {
                // Some of the new members are not registered on Signal
                for (ContactTokenDetails contact : contacts) {
                    newMembers.remove(contact.getNumber());
                }
                System.err.println("Failed to add members " + Util.join(", ", newMembers) + " to group: Not registered on Signal");
                System.err.println("Aborting…");
                System.exit(1);
            }
        }

        if (avatarFile != null) {
            IOUtils.createPrivateDirectories(avatarsPath);
            File aFile = getGroupAvatarFile(g.groupId);
            Files.copy(Paths.get(avatarFile), aFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        account.getGroupStore().updateGroup(g);

        SignalServiceDataMessage.Builder messageBuilder = getGroupUpdateMessageBuilder(g);

        // Don't send group message to ourself
        final List<String> membersSend = new ArrayList<>(g.members);
        membersSend.remove(this.username);
        sendMessageLegacy(messageBuilder, membersSend);
        return g.groupId;
    }

    private void sendUpdateGroupMessage(byte[] groupId, String recipient) throws IOException, EncapsulatedExceptions {
        if (groupId == null) {
            return;
        }
        GroupInfo g = getGroupForSending(groupId);

        if (!g.members.contains(recipient)) {
            return;
        }

        SignalServiceDataMessage.Builder messageBuilder = getGroupUpdateMessageBuilder(g);

        // Send group message only to the recipient who requested it
        final List<String> membersSend = new ArrayList<>();
        membersSend.add(recipient);
        sendMessageLegacy(messageBuilder, membersSend);
    }

    private SignalServiceDataMessage.Builder getGroupUpdateMessageBuilder(GroupInfo g) {
        SignalServiceGroup.Builder group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.UPDATE)
                .withId(g.groupId)
                .withName(g.name)
                .withMembers(new ArrayList<>(g.members));

        File aFile = getGroupAvatarFile(g.groupId);
        if (aFile.exists()) {
            try {
                group.withAvatar(Utils.createAttachment(aFile));
            } catch (IOException e) {
                throw new AttachmentInvalidException(aFile.toString(), e);
            }
        }

        return SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group.build());
    }

    private void sendGroupInfoRequest(byte[] groupId, String recipient) throws IOException, EncapsulatedExceptions {
        if (groupId == null) {
            return;
        }

        SignalServiceGroup.Builder group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.REQUEST_INFO)
                .withId(groupId);

        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group.build());

        // Send group info request message to the recipient who sent us a message with this groupId
        final List<String> membersSend = new ArrayList<>();
        membersSend.add(recipient);
        sendMessageLegacy(messageBuilder, membersSend);
    }

    @Override
    public void sendMessage(String message, List<String> attachments, String recipient)
            throws EncapsulatedExceptions, AttachmentInvalidException, IOException {
        List<String> recipients = new ArrayList<>(1);
        recipients.add(recipient);
        sendMessage(message, attachments, recipients);
    }

    @Override
    public void sendMessage(String messageText, List<String> attachments,
                            List<String> recipients)
            throws IOException, EncapsulatedExceptions, AttachmentInvalidException {
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().withBody(messageText);
        if (attachments != null) {
            messageBuilder.withAttachments(Utils.getSignalServiceAttachments(attachments));
        }
        sendMessageLegacy(messageBuilder, recipients);
    }

    @Override
    public void sendEndSessionMessage(List<String> recipients) throws IOException, EncapsulatedExceptions {
        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .asEndSessionMessage();

        sendMessageLegacy(messageBuilder, recipients);
    }

    @Override
    public String getContactName(String number) {
        ContactInfo contact = account.getContactStore().getContact(number);
        if (contact == null) {
            return "";
        } else {
            return contact.name;
        }
    }

    @Override
    public void setContactName(String number, String name) {
        ContactInfo contact = account.getContactStore().getContact(number);
        if (contact == null) {
            contact = new ContactInfo();
            contact.number = number;
            System.err.println("Add contact " + number + " named " + name);
        } else {
            System.err.println("Updating contact " + number + " name " + contact.name + " -> " + name);
        }
        contact.name = name;
        account.getContactStore().updateContact(contact);
        account.save();
    }

    @Override
    public List<byte[]> getGroupIds() {
        List<GroupInfo> groups = getGroups();
        List<byte[]> ids = new ArrayList<>(groups.size());
        for (GroupInfo group : groups) {
            ids.add(group.groupId);
        }
        return ids;
    }

    @Override
    public String getGroupName(byte[] groupId) {
        GroupInfo group = getGroup(groupId);
        if (group == null) {
            return "";
        } else {
            return group.name;
        }
    }

    @Override
    public List<String> getGroupMembers(byte[] groupId) {
        GroupInfo group = getGroup(groupId);
        if (group == null) {
            return new ArrayList<>();
        } else {
            return new ArrayList<>(group.members);
        }
    }

    @Override
    public byte[] updateGroup(byte[] groupId, String name, List<String> members, String avatar) throws IOException, EncapsulatedExceptions, GroupNotFoundException, AttachmentInvalidException {
        if (groupId.length == 0) {
            groupId = null;
        }
        if (name.isEmpty()) {
            name = null;
        }
        if (members.size() == 0) {
            members = null;
        }
        if (avatar.isEmpty()) {
            avatar = null;
        }
        return sendUpdateGroupMessage(groupId, name, members, avatar);
    }

    /**
     * Change the expiration timer for a thread (number of groupId)
     *
     * @param numberOrGroupId
     * @param messageExpirationTimer
     */
    public void setExpirationTimer(String numberOrGroupId, int messageExpirationTimer) {
        ThreadInfo thread = account.getThreadStore().getThread(numberOrGroupId);
        thread.messageExpirationTime = messageExpirationTimer;
        account.getThreadStore().updateThread(thread);
    }

    private void requestSyncGroups() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.GROUPS).build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            e.printStackTrace();
        }
    }

    private void requestSyncContacts() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.CONTACTS).build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            e.printStackTrace();
        }
    }

    private void requestSyncBlocked() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.BLOCKED).build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            e.printStackTrace();
        }
    }

    private void requestSyncConfiguration() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.CONFIGURATION).build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            e.printStackTrace();
        }
    }

    private byte[] getSelfUnidentifiedAccessKey() {
        return UnidentifiedAccess.deriveAccessKeyFrom(account.getProfileKey());
    }

    private byte[] getTargetUnidentifiedAccessKey(SignalServiceAddress recipient) {
        // TODO implement
        return null;
    }

    private Optional<UnidentifiedAccessPair> getAccessForSync() {
        // TODO implement
        return Optional.absent();
    }

    private List<Optional<UnidentifiedAccessPair>> getAccessFor(Collection<SignalServiceAddress> recipients) {
        List<Optional<UnidentifiedAccessPair>> result = new ArrayList<>(recipients.size());
        for (SignalServiceAddress recipient : recipients) {
            result.add(Optional.<UnidentifiedAccessPair>absent());
        }
        return result;
    }

    private Optional<UnidentifiedAccessPair> getAccessFor(SignalServiceAddress recipient) {
        // TODO implement
        return Optional.absent();
    }

    private void sendSyncMessage(SignalServiceSyncMessage message)
            throws IOException, UntrustedIdentityException {
        SignalServiceMessageSender messageSender = new SignalServiceMessageSender(BaseConfig.serviceConfiguration, username, account.getPassword(),
                account.getDeviceId(), account.getSignalProtocolStore(), BaseConfig.USER_AGENT, account.isMultiDevice(), Optional.fromNullable(messagePipe), Optional.fromNullable(unidentifiedMessagePipe), Optional.<SignalServiceMessageSender.EventListener>absent());
        try {
            messageSender.sendMessage(message, getAccessForSync());
        } catch (UntrustedIdentityException e) {
            account.getSignalProtocolStore().saveIdentity(e.getE164Number(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
            throw e;
        }
    }

    /**
     * This method throws an EncapsulatedExceptions exception instead of returning a list of SendMessageResult.
     */
    private void sendMessageLegacy(SignalServiceDataMessage.Builder messageBuilder, Collection<String> recipients)
            throws EncapsulatedExceptions, IOException {
        List<SendMessageResult> results = sendMessage(messageBuilder, recipients);

        List<UntrustedIdentityException> untrustedIdentities = new LinkedList<>();
        List<UnregisteredUserException> unregisteredUsers = new LinkedList<>();
        List<NetworkFailureException> networkExceptions = new LinkedList<>();

        for (SendMessageResult result : results) {
            if (result.isUnregisteredFailure()) {
                unregisteredUsers.add(new UnregisteredUserException(result.getAddress().getNumber(), null));
            } else if (result.isNetworkFailure()) {
                networkExceptions.add(new NetworkFailureException(result.getAddress().getNumber(), null));
            } else if (result.getIdentityFailure() != null) {
                untrustedIdentities.add(new UntrustedIdentityException("Untrusted", result.getAddress().getNumber(), result.getIdentityFailure().getIdentityKey()));
            }
        }
        if (!untrustedIdentities.isEmpty() || !unregisteredUsers.isEmpty() || !networkExceptions.isEmpty()) {
            throw new EncapsulatedExceptions(untrustedIdentities, unregisteredUsers, networkExceptions);
        }
    }

    private List<SendMessageResult> sendMessage(SignalServiceDataMessage.Builder messageBuilder, Collection<String> recipients)
            throws IOException {
        Set<SignalServiceAddress> recipientsTS = Utils.getSignalServiceAddresses(recipients, username);
        if (recipientsTS == null) {
            account.save();
            return Collections.emptyList();
        }

        SignalServiceDataMessage message = null;
        try {
            SignalServiceMessageSender messageSender = new SignalServiceMessageSender(BaseConfig.serviceConfiguration, username, account.getPassword(),
                    account.getDeviceId(), account.getSignalProtocolStore(), BaseConfig.USER_AGENT, account.isMultiDevice(), Optional.fromNullable(messagePipe), Optional.fromNullable(unidentifiedMessagePipe), Optional.<SignalServiceMessageSender.EventListener>absent());

            message = messageBuilder.build();
            if (message.getGroupInfo().isPresent()) {
                try {
                    final boolean isRecipientUpdate = true;
                    List<SendMessageResult> result = messageSender.sendMessage(new ArrayList<>(recipientsTS), getAccessFor(recipientsTS), isRecipientUpdate, message);
                    for (SendMessageResult r : result) {
                        if (r.getIdentityFailure() != null) {
                            account.getSignalProtocolStore().saveIdentity(r.getAddress().getNumber(), r.getIdentityFailure().getIdentityKey(), TrustLevel.UNTRUSTED);
                        }
                    }
                    return result;
                } catch (UntrustedIdentityException e) {
                    account.getSignalProtocolStore().saveIdentity(e.getE164Number(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
                    return Collections.emptyList();
                }
            } else if (recipientsTS.size() == 1 && recipientsTS.contains(new SignalServiceAddress(username))) {
                SignalServiceAddress recipient = new SignalServiceAddress(username);
                final Optional<UnidentifiedAccessPair> unidentifiedAccess = getAccessFor(recipient);
                SentTranscriptMessage transcript = new SentTranscriptMessage(recipient.getNumber(),
                        message.getTimestamp(),
                        message,
                        message.getExpiresInSeconds(),
                        Collections.singletonMap(recipient.getNumber(), unidentifiedAccess.isPresent()),
                        false);
                SignalServiceSyncMessage syncMessage = SignalServiceSyncMessage.forSentTranscript(transcript);

                List<SendMessageResult> results = new ArrayList<>(recipientsTS.size());
                try {
                    messageSender.sendMessage(syncMessage, unidentifiedAccess);
                } catch (UntrustedIdentityException e) {
                    account.getSignalProtocolStore().saveIdentity(e.getE164Number(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
                    results.add(SendMessageResult.identityFailure(recipient, e.getIdentityKey()));
                }
                return results;
            } else {
                // Send to all individually, so sync messages are sent correctly
                List<SendMessageResult> results = new ArrayList<>(recipientsTS.size());
                for (SignalServiceAddress address : recipientsTS) {
                    ThreadInfo thread = account.getThreadStore().getThread(address.getNumber());
                    if (thread != null) {
                        messageBuilder.withExpiration(thread.messageExpirationTime);
                    } else {
                        messageBuilder.withExpiration(0);
                    }
                    message = messageBuilder.build();
                    try {
                        SendMessageResult result = messageSender.sendMessage(address, getAccessFor(address), message);
                        results.add(result);
                    } catch (UntrustedIdentityException e) {
                        account.getSignalProtocolStore().saveIdentity(e.getE164Number(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
                        results.add(SendMessageResult.identityFailure(address, e.getIdentityKey()));
                    }
                }
                return results;
            }
        } finally {
            if (message != null && message.isEndSession()) {
                for (SignalServiceAddress recipient : recipientsTS) {
                    handleEndSession(recipient.getNumber());
                }
            }
            account.save();
        }
    }

    private SignalServiceContent decryptMessage(SignalServiceEnvelope envelope) throws InvalidMetadataMessageException, ProtocolInvalidMessageException, ProtocolDuplicateMessageException, ProtocolLegacyMessageException, ProtocolInvalidKeyIdException, InvalidMetadataVersionException, ProtocolInvalidVersionException, ProtocolNoSessionException, ProtocolInvalidKeyException, ProtocolUntrustedIdentityException, SelfSendException, UnsupportedDataMessageException {
        SignalServiceCipher cipher = new SignalServiceCipher(new SignalServiceAddress(username), account.getSignalProtocolStore(), Utils.getCertificateValidator());
        try {
            return cipher.decrypt(envelope);
        } catch (ProtocolUntrustedIdentityException e) {
            // TODO We don't get the new untrusted identity from ProtocolUntrustedIdentityException anymore ... we need to get it from somewhere else
//            account.getSignalProtocolStore().saveIdentity(e.getSender(), e.getUntrustedIdentity(), TrustLevel.UNTRUSTED);
            throw e;
        }
    }

    private void handleEndSession(String source) {
        account.getSignalProtocolStore().deleteAllSessions(source);
    }

    private void handleSignalServiceDataMessage(SignalServiceDataMessage message, boolean isSync, String source, String destination, boolean ignoreAttachments) {
        String threadId;
        if (message.getGroupInfo().isPresent()) {
            SignalServiceGroup groupInfo = message.getGroupInfo().get();
            threadId = Base64.encodeBytes(groupInfo.getGroupId());
            GroupInfo group = account.getGroupStore().getGroup(groupInfo.getGroupId());
            switch (groupInfo.getType()) {
                case UPDATE:
                    if (group == null) {
                        group = new GroupInfo(groupInfo.getGroupId());
                    }

                    if (groupInfo.getAvatar().isPresent()) {
                        SignalServiceAttachment avatar = groupInfo.getAvatar().get();
                        if (avatar.isPointer()) {
                            try {
                                retrieveGroupAvatarAttachment(avatar.asPointer(), group.groupId);
                            } catch (IOException | InvalidMessageException e) {
                                System.err.println("Failed to retrieve group avatar (" + avatar.asPointer().getId() + "): " + e.getMessage());
                            }
                        }
                    }

                    if (groupInfo.getName().isPresent()) {
                        group.name = groupInfo.getName().get();
                    }

                    if (groupInfo.getMembers().isPresent()) {
                        group.members.addAll(groupInfo.getMembers().get());
                    }

                    account.getGroupStore().updateGroup(group);
                    break;
                case DELIVER:
                    if (group == null) {
                        try {
                            sendGroupInfoRequest(groupInfo.getGroupId(), source);
                        } catch (IOException | EncapsulatedExceptions e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case QUIT:
                    if (group == null) {
                        try {
                            sendGroupInfoRequest(groupInfo.getGroupId(), source);
                        } catch (IOException | EncapsulatedExceptions e) {
                            e.printStackTrace();
                        }
                    } else {
                        group.members.remove(source);
                        account.getGroupStore().updateGroup(group);
                    }
                    break;
                case REQUEST_INFO:
                    if (group != null) {
                        try {
                            sendUpdateGroupMessage(groupInfo.getGroupId(), source);
                        } catch (IOException | EncapsulatedExceptions e) {
                            e.printStackTrace();
                        } catch (NotAGroupMemberException e) {
                            // We have left this group, so don't send a group update message
                        }
                    }
                    break;
            }
        } else {
            if (isSync) {
                threadId = destination;
            } else {
                threadId = source;
            }
        }
        if (message.isEndSession()) {
            handleEndSession(isSync ? destination : source);
        }
        if (message.isExpirationUpdate() || message.getBody().isPresent()) {
            ThreadInfo thread = account.getThreadStore().getThread(threadId);
            if (thread == null) {
                thread = new ThreadInfo();
                thread.id = threadId;
            }
            if (thread.messageExpirationTime != message.getExpiresInSeconds()) {
                thread.messageExpirationTime = message.getExpiresInSeconds();
                account.getThreadStore().updateThread(thread);
            }
        }
        if (message.getAttachments().isPresent() && !ignoreAttachments) {
            for (SignalServiceAttachment attachment : message.getAttachments().get()) {
                if (attachment.isPointer()) {
                    try {
                        retrieveAttachment(attachment.asPointer());
                    } catch (IOException | InvalidMessageException e) {
                        System.err.println("Failed to retrieve attachment (" + attachment.asPointer().getId() + "): " + e.getMessage());
                    }
                }
            }
        }
        if (message.getProfileKey().isPresent() && message.getProfileKey().get().length == 32) {
            if (source.equals(username)) {
                this.account.setProfileKey(message.getProfileKey().get());
            }
            ContactInfo contact = account.getContactStore().getContact(source);
            if (contact == null) {
                contact = new ContactInfo();
                contact.number = source;
            }
            contact.profileKey = Base64.encodeBytes(message.getProfileKey().get());
        }
    }

    private void retryFailedReceivedMessages(ReceiveMessageHandler handler, boolean ignoreAttachments) {
        final File cachePath = new File(getMessageCachePath());
        if (!cachePath.exists()) {
            return;
        }
        for (final File dir : Objects.requireNonNull(cachePath.listFiles())) {
            if (!dir.isDirectory()) {
                continue;
            }

            for (final File fileEntry : Objects.requireNonNull(dir.listFiles())) {
                if (!fileEntry.isFile()) {
                    continue;
                }
                SignalServiceEnvelope envelope;
                try {
                    envelope = Utils.loadEnvelope(fileEntry);
                    if (envelope == null) {
                        continue;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }
                SignalServiceContent content = null;
                if (!envelope.isReceipt()) {
                    try {
                        content = decryptMessage(envelope);
                    } catch (Exception e) {
                        continue;
                    }
                    handleMessage(envelope, content, ignoreAttachments);
                }
                account.save();
                handler.handleMessage(envelope, content, null);
                try {
                    Files.delete(fileEntry.toPath());
                } catch (IOException e) {
                    System.err.println("Failed to delete cached message file “" + fileEntry + "”: " + e.getMessage());
                }
            }
            // Try to delete directory if empty
            dir.delete();
        }
    }

    public void receiveMessages(long timeout, TimeUnit unit, boolean returnOnTimeout, boolean ignoreAttachments, ReceiveMessageHandler handler) throws IOException {
        retryFailedReceivedMessages(handler, ignoreAttachments);
        final SignalServiceMessageReceiver messageReceiver = new SignalServiceMessageReceiver(BaseConfig.serviceConfiguration, username, account.getPassword(), account.getDeviceId(), account.getSignalingKey(), BaseConfig.USER_AGENT, null, timer);

        try {
            if (messagePipe == null) {
                messagePipe = messageReceiver.createMessagePipe();
            }

            while (true) {
                SignalServiceEnvelope envelope;
                SignalServiceContent content = null;
                Exception exception = null;
                final long now = new Date().getTime();
                try {
                    envelope = messagePipe.read(timeout, unit, new SignalServiceMessagePipe.MessagePipeCallback() {
                        @Override
                        public void onMessage(SignalServiceEnvelope envelope) {
                            // store message on disk, before acknowledging receipt to the server
                            try {
                                File cacheFile = getMessageCacheFile(envelope.getSource(), now, envelope.getTimestamp());
                                Utils.storeEnvelope(envelope, cacheFile);
                            } catch (IOException e) {
                                System.err.println("Failed to store encrypted message in disk cache, ignoring: " + e.getMessage());
                            }
                        }
                    });
                } catch (TimeoutException e) {
                    if (returnOnTimeout)
                        return;
                    continue;
                } catch (InvalidVersionException e) {
                    System.err.println("Ignoring error: " + e.getMessage());
                    continue;
                }
                if (!envelope.isReceipt()) {
                    try {
                        content = decryptMessage(envelope);
                    } catch (Exception e) {
                        exception = e;
                    }
                    handleMessage(envelope, content, ignoreAttachments);
                }
                account.save();
                handler.handleMessage(envelope, content, exception);
                if (!(exception instanceof ProtocolUntrustedIdentityException)) {
                    File cacheFile = null;
                    try {
                        cacheFile = getMessageCacheFile(envelope.getSource(), now, envelope.getTimestamp());
                        Files.delete(cacheFile.toPath());
                        // Try to delete directory if empty
                        new File(getMessageCachePath()).delete();
                    } catch (IOException e) {
                        System.err.println("Failed to delete cached message file “" + cacheFile + "”: " + e.getMessage());
                    }
                }
            }
        } finally {
            if (messagePipe != null) {
                messagePipe.shutdown();
                messagePipe = null;
            }
        }
    }

    private void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, boolean ignoreAttachments) {
        if (content != null) {
            if (content.getDataMessage().isPresent()) {
                SignalServiceDataMessage message = content.getDataMessage().get();
                handleSignalServiceDataMessage(message, false, envelope.getSource(), username, ignoreAttachments);
            }
            if (content.getSyncMessage().isPresent()) {
                account.setMultiDevice(true);
                SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();
                if (syncMessage.getSent().isPresent()) {
                    SignalServiceDataMessage message = syncMessage.getSent().get().getMessage();
                    handleSignalServiceDataMessage(message, true, envelope.getSource(), syncMessage.getSent().get().getDestination().get(), ignoreAttachments);
                }
                if (syncMessage.getRequest().isPresent()) {
                    RequestMessage rm = syncMessage.getRequest().get();
                    if (rm.isContactsRequest()) {
                        try {
                            sendContacts();
                        } catch (UntrustedIdentityException | IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (rm.isGroupsRequest()) {
                        try {
                            sendGroups();
                        } catch (UntrustedIdentityException | IOException e) {
                            e.printStackTrace();
                        }
                    }
                    // TODO Handle rm.isBlockedListRequest(); rm.isConfigurationRequest();
                }
                if (syncMessage.getGroups().isPresent()) {
                    File tmpFile = null;
                    try {
                        tmpFile = IOUtils.createTempFile();
                        try (InputStream attachmentAsStream = retrieveAttachmentAsStream(syncMessage.getGroups().get().asPointer(), tmpFile)) {
                            DeviceGroupsInputStream s = new DeviceGroupsInputStream(attachmentAsStream);
                            DeviceGroup g;
                            while ((g = s.read()) != null) {
                                GroupInfo syncGroup = account.getGroupStore().getGroup(g.getId());
                                if (syncGroup == null) {
                                    syncGroup = new GroupInfo(g.getId());
                                }
                                if (g.getName().isPresent()) {
                                    syncGroup.name = g.getName().get();
                                }
                                syncGroup.members.addAll(g.getMembers());
                                syncGroup.active = g.isActive();
                                if (g.getColor().isPresent()) {
                                    syncGroup.color = g.getColor().get();
                                }

                                if (g.getAvatar().isPresent()) {
                                    retrieveGroupAvatarAttachment(g.getAvatar().get(), syncGroup.groupId);
                                }
                                account.getGroupStore().updateGroup(syncGroup);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (tmpFile != null) {
                            try {
                                Files.delete(tmpFile.toPath());
                            } catch (IOException e) {
                                System.err.println("Failed to delete received groups temp file “" + tmpFile + "”: " + e.getMessage());
                            }
                        }
                    }
                }
                if (syncMessage.getBlockedList().isPresent()) {
                    // TODO store list of blocked numbers
                }
                if (syncMessage.getContacts().isPresent()) {
                    File tmpFile = null;
                    try {
                        tmpFile = IOUtils.createTempFile();
                        final ContactsMessage contactsMessage = syncMessage.getContacts().get();
                        try (InputStream attachmentAsStream = retrieveAttachmentAsStream(contactsMessage.getContactsStream().asPointer(), tmpFile)) {
                            DeviceContactsInputStream s = new DeviceContactsInputStream(attachmentAsStream);
                            if (contactsMessage.isComplete()) {
                                account.getContactStore().clear();
                            }
                            DeviceContact c;
                            while ((c = s.read()) != null) {
                                if (c.getNumber().equals(account.getUsername()) && c.getProfileKey().isPresent()) {
                                    account.setProfileKey(c.getProfileKey().get());
                                }
                                ContactInfo contact = account.getContactStore().getContact(c.getNumber());
                                if (contact == null) {
                                    contact = new ContactInfo();
                                    contact.number = c.getNumber();
                                }
                                if (c.getName().isPresent()) {
                                    contact.name = c.getName().get();
                                }
                                if (c.getColor().isPresent()) {
                                    contact.color = c.getColor().get();
                                }
                                if (c.getProfileKey().isPresent()) {
                                    contact.profileKey = Base64.encodeBytes(c.getProfileKey().get());
                                }
                                if (c.getVerified().isPresent()) {
                                    final VerifiedMessage verifiedMessage = c.getVerified().get();
                                    account.getSignalProtocolStore().saveIdentity(verifiedMessage.getDestination(), verifiedMessage.getIdentityKey(), TrustLevel.fromVerifiedState(verifiedMessage.getVerified()));
                                }
                                if (c.getExpirationTimer().isPresent()) {
                                    ThreadInfo thread = account.getThreadStore().getThread(c.getNumber());
                                    if (thread == null) {
                                        thread = new ThreadInfo();
                                        thread.id = c.getNumber();
                                    }
                                    thread.messageExpirationTime = c.getExpirationTimer().get();
                                    account.getThreadStore().updateThread(thread);
                                }
                                if (c.isBlocked()) {
                                    // TODO store list of blocked numbers
                                }
                                account.getContactStore().updateContact(contact);

                                if (c.getAvatar().isPresent()) {
                                    retrieveContactAvatarAttachment(c.getAvatar().get(), contact.number);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (tmpFile != null) {
                            try {
                                Files.delete(tmpFile.toPath());
                            } catch (IOException e) {
                                System.err.println("Failed to delete received contacts temp file “" + tmpFile + "”: " + e.getMessage());
                            }
                        }
                    }
                }
                if (syncMessage.getVerified().isPresent()) {
                    final VerifiedMessage verifiedMessage = syncMessage.getVerified().get();
                    account.getSignalProtocolStore().saveIdentity(verifiedMessage.getDestination(), verifiedMessage.getIdentityKey(), TrustLevel.fromVerifiedState(verifiedMessage.getVerified()));
                }
                if (syncMessage.getConfiguration().isPresent()) {
                    // TODO
                }
            }
        }
    }

    private File getContactAvatarFile(String number) {
        return new File(avatarsPath, "contact-" + number);
    }

    private File retrieveContactAvatarAttachment(SignalServiceAttachment attachment, String number) throws IOException, InvalidMessageException {
        IOUtils.createPrivateDirectories(avatarsPath);
        if (attachment.isPointer()) {
            SignalServiceAttachmentPointer pointer = attachment.asPointer();
            return retrieveAttachment(pointer, getContactAvatarFile(number), false);
        } else {
            SignalServiceAttachmentStream stream = attachment.asStream();
            return Utils.retrieveAttachment(stream, getContactAvatarFile(number));
        }
    }

    private File getGroupAvatarFile(byte[] groupId) {
        return new File(avatarsPath, "group-" + Base64.encodeBytes(groupId).replace("/", "_"));
    }

    private File retrieveGroupAvatarAttachment(SignalServiceAttachment attachment, byte[] groupId) throws IOException, InvalidMessageException {
        IOUtils.createPrivateDirectories(avatarsPath);
        if (attachment.isPointer()) {
            SignalServiceAttachmentPointer pointer = attachment.asPointer();
            return retrieveAttachment(pointer, getGroupAvatarFile(groupId), false);
        } else {
            SignalServiceAttachmentStream stream = attachment.asStream();
            return Utils.retrieveAttachment(stream, getGroupAvatarFile(groupId));
        }
    }

    public File getAttachmentFile(long attachmentId) {
        return new File(attachmentsPath, attachmentId + "");
    }

    private File retrieveAttachment(SignalServiceAttachmentPointer pointer) throws IOException, InvalidMessageException {
        IOUtils.createPrivateDirectories(attachmentsPath);
        return retrieveAttachment(pointer, getAttachmentFile(pointer.getId()), true);
    }

    private File retrieveAttachment(SignalServiceAttachmentPointer pointer, File outputFile, boolean storePreview) throws IOException, InvalidMessageException {
        if (storePreview && pointer.getPreview().isPresent()) {
            File previewFile = new File(outputFile + ".preview");
            try (OutputStream output = new FileOutputStream(previewFile)) {
                byte[] preview = pointer.getPreview().get();
                output.write(preview, 0, preview.length);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            }
        }

        final SignalServiceMessageReceiver messageReceiver = new SignalServiceMessageReceiver(BaseConfig.serviceConfiguration, username, account.getPassword(), account.getDeviceId(), account.getSignalingKey(), BaseConfig.USER_AGENT, null, timer);

        File tmpFile = IOUtils.createTempFile();
        try (InputStream input = messageReceiver.retrieveAttachment(pointer, tmpFile, BaseConfig.MAX_ATTACHMENT_SIZE)) {
            try (OutputStream output = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[4096];
                int read;

                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            }
        } finally {
            try {
                Files.delete(tmpFile.toPath());
            } catch (IOException e) {
                System.err.println("Failed to delete received attachment temp file “" + tmpFile + "”: " + e.getMessage());
            }
        }
        return outputFile;
    }

    private InputStream retrieveAttachmentAsStream(SignalServiceAttachmentPointer pointer, File tmpFile) throws IOException, InvalidMessageException {
        final SignalServiceMessageReceiver messageReceiver = new SignalServiceMessageReceiver(BaseConfig.serviceConfiguration, username, account.getPassword(), account.getDeviceId(), account.getSignalingKey(), BaseConfig.USER_AGENT, null, timer);
        return messageReceiver.retrieveAttachment(pointer, tmpFile, BaseConfig.MAX_ATTACHMENT_SIZE);
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    private void sendGroups() throws IOException, UntrustedIdentityException {
        File groupsFile = IOUtils.createTempFile();

        try {
            try (OutputStream fos = new FileOutputStream(groupsFile)) {
                DeviceGroupsOutputStream out = new DeviceGroupsOutputStream(fos);
                for (GroupInfo record : account.getGroupStore().getGroups()) {
                    ThreadInfo info = account.getThreadStore().getThread(Base64.encodeBytes(record.groupId));
                    out.write(new DeviceGroup(record.groupId, Optional.fromNullable(record.name),
                            new ArrayList<>(record.members), createGroupAvatarAttachment(record.groupId),
                            record.active, Optional.fromNullable(info != null ? info.messageExpirationTime : null),
                            Optional.fromNullable(record.color), false));
                }
            }

            if (groupsFile.exists() && groupsFile.length() > 0) {
                try (FileInputStream groupsFileStream = new FileInputStream(groupsFile)) {
                    SignalServiceAttachmentStream attachmentStream = SignalServiceAttachment.newStreamBuilder()
                            .withStream(groupsFileStream)
                            .withContentType("application/octet-stream")
                            .withLength(groupsFile.length())
                            .build();

                    sendSyncMessage(SignalServiceSyncMessage.forGroups(attachmentStream));
                }
            }
        } finally {
            try {
                Files.delete(groupsFile.toPath());
            } catch (IOException e) {
                System.err.println("Failed to delete groups temp file “" + groupsFile + "”: " + e.getMessage());
            }
        }
    }

    private void sendContacts() throws IOException, UntrustedIdentityException {
        File contactsFile = IOUtils.createTempFile();

        try {
            try (OutputStream fos = new FileOutputStream(contactsFile)) {
                DeviceContactsOutputStream out = new DeviceContactsOutputStream(fos);
                for (ContactInfo record : account.getContactStore().getContacts()) {
                    VerifiedMessage verifiedMessage = null;
                    ThreadInfo info = account.getThreadStore().getThread(record.number);
                    if (getIdentities().containsKey(record.number)) {
                        JsonIdentityKeyStore.Identity currentIdentity = null;
                        for (JsonIdentityKeyStore.Identity id : getIdentities().get(record.number)) {
                            if (currentIdentity == null || id.getDateAdded().after(currentIdentity.getDateAdded())) {
                                currentIdentity = id;
                            }
                        }
                        if (currentIdentity != null) {
                            verifiedMessage = new VerifiedMessage(record.number, currentIdentity.getIdentityKey(), currentIdentity.getTrustLevel().toVerifiedState(), currentIdentity.getDateAdded().getTime());
                        }
                    }

                    byte[] profileKey = record.profileKey == null ? null : Base64.decode(record.profileKey);
                    // TODO store list of blocked numbers
                    boolean blocked = false;
                    out.write(new DeviceContact(record.number, Optional.fromNullable(record.name),
                            createContactAvatarAttachment(record.number), Optional.fromNullable(record.color),
                            Optional.fromNullable(verifiedMessage), Optional.fromNullable(profileKey), blocked, Optional.fromNullable(info != null ? info.messageExpirationTime : null)));
                }

                if (account.getProfileKey() != null) {
                    // Send our own profile key as well
                    out.write(new DeviceContact(account.getUsername(),
                            Optional.<String>absent(), Optional.<SignalServiceAttachmentStream>absent(),
                            Optional.<String>absent(), Optional.<VerifiedMessage>absent(),
                            Optional.of(account.getProfileKey()),
                            false, Optional.<Integer>absent()));
                }
            }

            if (contactsFile.exists() && contactsFile.length() > 0) {
                try (FileInputStream contactsFileStream = new FileInputStream(contactsFile)) {
                    SignalServiceAttachmentStream attachmentStream = SignalServiceAttachment.newStreamBuilder()
                            .withStream(contactsFileStream)
                            .withContentType("application/octet-stream")
                            .withLength(contactsFile.length())
                            .build();

                    sendSyncMessage(SignalServiceSyncMessage.forContacts(new ContactsMessage(attachmentStream, true)));
                }
            }
        } finally {
            try {
                Files.delete(contactsFile.toPath());
            } catch (IOException e) {
                System.err.println("Failed to delete contacts temp file “" + contactsFile + "”: " + e.getMessage());
            }
        }
    }

    private void sendVerifiedMessage(String destination, IdentityKey identityKey, TrustLevel trustLevel) throws IOException, UntrustedIdentityException {
        VerifiedMessage verifiedMessage = new VerifiedMessage(destination, identityKey, trustLevel.toVerifiedState(), System.currentTimeMillis());
        sendSyncMessage(SignalServiceSyncMessage.forVerified(verifiedMessage));
    }

    public ContactInfo getContact(String number) {
        return account.getContactStore().getContact(number);
    }

    public GroupInfo getGroup(byte[] groupId) {
        return account.getGroupStore().getGroup(groupId);
    }

    public Map<String, List<JsonIdentityKeyStore.Identity>> getIdentities() {
        return account.getSignalProtocolStore().getIdentities();
    }

    public List<JsonIdentityKeyStore.Identity> getIdentities(String number) {
        return account.getSignalProtocolStore().getIdentities(number);
    }

    /**
     * Trust this the identity with this fingerprint
     *
     * @param name        username of the identity
     * @param fingerprint Fingerprint
     */
    public boolean trustIdentityVerified(String name, byte[] fingerprint) {
        List<JsonIdentityKeyStore.Identity> ids = account.getSignalProtocolStore().getIdentities(name);
        if (ids == null) {
            return false;
        }
        for (JsonIdentityKeyStore.Identity id : ids) {
            if (!Arrays.equals(id.getIdentityKey().serialize(), fingerprint)) {
                continue;
            }

            account.getSignalProtocolStore().saveIdentity(name, id.getIdentityKey(), TrustLevel.TRUSTED_VERIFIED);
            try {
                sendVerifiedMessage(name, id.getIdentityKey(), TrustLevel.TRUSTED_VERIFIED);
            } catch (IOException | UntrustedIdentityException e) {
                e.printStackTrace();
            }
            account.save();
            return true;
        }
        return false;
    }

    /**
     * Trust this the identity with this safety number
     *
     * @param name         username of the identity
     * @param safetyNumber Safety number
     */
    public boolean trustIdentityVerifiedSafetyNumber(String name, String safetyNumber) {
        List<JsonIdentityKeyStore.Identity> ids = account.getSignalProtocolStore().getIdentities(name);
        if (ids == null) {
            return false;
        }
        for (JsonIdentityKeyStore.Identity id : ids) {
            if (!safetyNumber.equals(computeSafetyNumber(name, id.getIdentityKey()))) {
                continue;
            }

            account.getSignalProtocolStore().saveIdentity(name, id.getIdentityKey(), TrustLevel.TRUSTED_VERIFIED);
            try {
                sendVerifiedMessage(name, id.getIdentityKey(), TrustLevel.TRUSTED_VERIFIED);
            } catch (IOException | UntrustedIdentityException e) {
                e.printStackTrace();
            }
            account.save();
            return true;
        }
        return false;
    }

    /**
     * Trust all keys of this identity without verification
     *
     * @param name username of the identity
     */
    public boolean trustIdentityAllKeys(String name) {
        List<JsonIdentityKeyStore.Identity> ids = account.getSignalProtocolStore().getIdentities(name);
        if (ids == null) {
            return false;
        }
        for (JsonIdentityKeyStore.Identity id : ids) {
            if (id.getTrustLevel() == TrustLevel.UNTRUSTED) {
                account.getSignalProtocolStore().saveIdentity(name, id.getIdentityKey(), TrustLevel.TRUSTED_UNVERIFIED);
                try {
                    sendVerifiedMessage(name, id.getIdentityKey(), TrustLevel.TRUSTED_UNVERIFIED);
                } catch (IOException | UntrustedIdentityException e) {
                    e.printStackTrace();
                }
            }
        }
        account.save();
        return true;
    }

    public String computeSafetyNumber(String theirUsername, IdentityKey theirIdentityKey) {
        return Utils.computeSafetyNumber(username, getIdentity(), theirUsername, theirIdentityKey);
    }

    public interface ReceiveMessageHandler {

        void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent decryptedContent, Throwable e);
    }
}

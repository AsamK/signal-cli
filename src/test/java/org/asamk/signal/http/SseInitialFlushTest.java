package org.asamk.signal.http;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.CallInfo;
import org.asamk.signal.manager.api.CallOffer;
import org.asamk.signal.manager.api.Configuration;
import org.asamk.signal.manager.api.Device;
import org.asamk.signal.manager.api.DeviceLinkUrl;
import org.asamk.signal.manager.api.Group;
import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.GroupInviteLinkUrl;
import org.asamk.signal.manager.api.Identity;
import org.asamk.signal.manager.api.IdentityVerificationCode;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.ReceiveConfig;
import org.asamk.signal.manager.api.Recipient;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.SendGroupMessageResults;
import org.asamk.signal.manager.api.SendMessageResult;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.manager.api.StickerPack;
import org.asamk.signal.manager.api.StickerPackId;
import org.asamk.signal.manager.api.StickerPackUrl;
import org.asamk.signal.manager.api.TurnServer;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.api.UpdateGroup;
import org.asamk.signal.manager.api.UpdateProfile;
import org.asamk.signal.manager.api.UserStatus;
import org.asamk.signal.manager.api.UsernameLinkUrl;
import org.asamk.signal.manager.api.UsernameStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for the SSE initial-flush bug:
 * HttpServerHandler used to flush the initial SSE response only after a later
 * write in the 15-second keep-alive loop, meaning the HTTP response headers
 * were not flushed to the client until then.
 * Clients with a shorter connection timeout (e.g. 10 s) would time out before
 * receiving the initial response.
 *
 * This test verifies that the endpoint returns HTTP 200 within 2 seconds of
 * connecting to GET /api/v1/events.
 */
class SseInitialFlushTest {

    private HttpServerHandler handler;
    private int port;

    /** Finds a free local port. */
    private static int freePort() throws Exception {
        try (var ss = new ServerSocket(0)) {
            ss.setReuseAddress(true);
            return ss.getLocalPort();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        port = freePort();
        handler = new HttpServerHandler(new InetSocketAddress("127.0.0.1", port), new MinimalStubManager());
        handler.init();
    }

    @AfterEach
    void tearDown() {
        if (handler != null) {
            handler.close();
        }
    }

    /**
     * The SSE endpoint MUST flush the initial HTTP response immediately upon
     * connection, before the 15-second keep-alive loop fires. A read timeout of
     * 2 000 ms is used — well below the 15-second wait interval but generous
     * enough to survive any CI scheduling jitter.
     */
    @Test
    void sseEndpointReturnsHeadersWithinTwoSeconds() {
        assertDoesNotThrow(() -> {
            var url = new URI("http", null, "127.0.0.1", port, "/api/v1/events", null, null).toURL();
            var conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setReadTimeout(2_000);   // 2 s — fails before fix (15 s flush), passes after
            conn.setConnectTimeout(2_000);
            try {
                conn.connect();
                assertEquals(200, conn.getResponseCode());
            } finally {
                conn.disconnect();
            }
        }, "SSE endpoint did not return the initial response within 2 seconds");
    }

    // -------------------------------------------------------------------------
    // Minimal Manager stub — only receive-handler methods need real behaviour;
    // everything else is a no-op stub.
    // -------------------------------------------------------------------------

    private static final class MinimalStubManager implements Manager {

        @Override
        public String getSelfNumber() {
            return "+10000000000";
        }

        @Override
        public void addReceiveHandler(ReceiveMessageHandler handler, boolean isWeakListener) {
            // no-op
        }

        @Override
        public void removeReceiveHandler(ReceiveMessageHandler handler) {
            // no-op
        }

        @Override
        public boolean isReceiving() {
            return false;
        }

        @Override
        public void receiveMessages(Optional<Duration> timeout, Optional<Integer> maxMessages, ReceiveMessageHandler handler) {
        }

        @Override
        public void stopReceiveMessages() {
        }

        @Override
        public void setReceiveConfig(ReceiveConfig receiveConfig) {
        }

        @Override
        public Map<String, UserStatus> getUserStatus(Set<String> numbers) {
            return Map.of();
        }

        @Override
        public Map<String, UsernameStatus> getUsernameStatus(Set<String> usernames) {
            return Map.of();
        }

        @Override
        public void updateAccountAttributes(String deviceName, Boolean unidentifiedDeliveryIndicators, Boolean discoverableByNumber, Boolean numberSharing) {
        }

        @Override
        public Configuration getConfiguration() {
            return null;
        }

        @Override
        public void updateConfiguration(Configuration configuration) {
        }

        @Override
        public void updateProfile(UpdateProfile updateProfile) {
        }

        @Override
        public String getUsername() {
            return null;
        }

        @Override
        public UsernameLinkUrl getUsernameLink() {
            return null;
        }

        @Override
        public void setUsername(String username) {
        }

        @Override
        public void deleteUsername() {
        }

        @Override
        public void startChangeNumber(String newNumber, boolean voiceVerification, String captcha) {
        }

        @Override
        public void finishChangeNumber(String newNumber, String verificationCode, String pin) {
        }

        @Override
        public void unregister() {
        }

        @Override
        public void deleteAccount() {
        }

        @Override
        public void submitRateLimitRecaptchaChallenge(String challenge, String captcha) {
        }

        @Override
        public List<Device> getLinkedDevices() {
            return List.of();
        }

        @Override
        public void updateLinkedDevice(int deviceId, String name) {
        }

        @Override
        public void removeLinkedDevices(int deviceId) {
        }

        @Override
        public void addDeviceLink(DeviceLinkUrl deviceLinkUrl) {
        }

        @Override
        public void setRegistrationLockPin(Optional<String> pin) {
        }

        @Override
        public List<Group> getGroups() {
            return List.of();
        }

        @Override
        public List<Group> getGroups(Collection<GroupId> groupIds) {
            return List.of();
        }

        @Override
        public SendGroupMessageResults quitGroup(GroupId groupId, Set<RecipientIdentifier.Single> administrators) {
            return null;
        }

        @Override
        public void deleteGroup(GroupId groupId) {
        }

        @Override
        public Pair<GroupId, SendGroupMessageResults> createGroup(String name, Set<RecipientIdentifier.Single> members, String avatarFile) {
            return null;
        }

        @Override
        public SendGroupMessageResults updateGroup(GroupId groupId, UpdateGroup updateGroup) {
            return null;
        }

        @Override
        public Pair<GroupId, SendGroupMessageResults> joinGroup(GroupInviteLinkUrl inviteLinkUrl) {
            return null;
        }

        @Override
        public SendMessageResults sendTypingMessage(TypingAction action, Set<RecipientIdentifier> recipients) {
            return null;
        }

        @Override
        public SendMessageResults sendReadReceipt(RecipientIdentifier.Single sender, List<Long> messageIds) {
            return null;
        }

        @Override
        public SendMessageResults sendViewedReceipt(RecipientIdentifier.Single sender, List<Long> messageIds) {
            return null;
        }

        @Override
        public SendMessageResults sendMessage(Message message, Set<RecipientIdentifier> recipients, boolean notifySelf) {
            return null;
        }

        @Override
        public SendMessageResults sendEditMessage(Message message, Set<RecipientIdentifier> recipients, long targetSentTimestamp) {
            return null;
        }

        @Override
        public SendMessageResults sendRemoteDeleteMessage(long targetSentTimestamp, Set<RecipientIdentifier> recipients) {
            return null;
        }

        @Override
        public SendMessageResults sendMessageReaction(String emoji, boolean remove, RecipientIdentifier.Single targetAuthor, long targetSentTimestamp, Set<RecipientIdentifier> recipients, boolean notifySelf, boolean story) {
            return null;
        }

        @Override
        public SendMessageResults sendAdminDelete(RecipientIdentifier.Single targetAuthor, long targetSentTimestamp, Set<RecipientIdentifier.Group> recipients, boolean notifySelf, boolean story) {
            return null;
        }

        @Override
        public SendMessageResults sendPinMessage(int duration, RecipientIdentifier.Single targetAuthor, long targetSentTimestamp, Set<RecipientIdentifier> recipients, boolean notifySelf, boolean story) {
            return null;
        }

        @Override
        public SendMessageResults sendUnpinMessage(RecipientIdentifier.Single targetAuthor, long targetSentTimestamp, Set<RecipientIdentifier> recipients, boolean notifySelf, boolean story) {
            return null;
        }

        @Override
        public SendMessageResults sendPaymentNotificationMessage(byte[] receipt, String note, RecipientIdentifier.Single recipient) {
            return null;
        }

        @Override
        public void sendEndSessionMessage(Set<RecipientIdentifier.Single> recipients) {
        }

        @Override
        public SendMessageResults sendMessageRequestResponse(MessageEnvelope.Sync.MessageRequestResponse.Type type, Set<RecipientIdentifier> recipients) {
            return null;
        }

        @Override
        public SendMessageResults sendPollCreateMessage(String question, boolean multipleChoice, List<String> options, Set<RecipientIdentifier> recipients, boolean notifySelf) {
            return null;
        }

        @Override
        public SendMessageResults sendPollVoteMessage(RecipientIdentifier.Single author, long timestamp, List<Integer> optionIds, int version, Set<RecipientIdentifier> recipients, boolean notifySelf) {
            return null;
        }

        @Override
        public SendMessageResults sendPollTerminateMessage(long timestamp, Set<RecipientIdentifier> recipients, boolean notifySelf) {
            return null;
        }

        @Override
        public void hideRecipient(RecipientIdentifier.Single recipient) {
        }

        @Override
        public void deleteRecipient(RecipientIdentifier.Single recipient) {
        }

        @Override
        public void deleteContact(RecipientIdentifier.Single recipient) {
        }

        @Override
        public void setContactName(RecipientIdentifier.Single recipient, String givenName, String familyName, String newGivenName, String newFamilyName, String nick) {
        }

        @Override
        public void setContactsBlocked(Collection<RecipientIdentifier.Single> recipients, boolean blocked) {
        }

        @Override
        public void setGroupsBlocked(Collection<GroupId> groupIds, boolean blocked) {
        }

        @Override
        public void setExpirationTimer(RecipientIdentifier.Single recipient, int messageExpirationTimer) {
        }

        @Override
        public StickerPackUrl uploadStickerPack(File path) {
            return null;
        }

        @Override
        public void installStickerPack(StickerPackUrl url) {
        }

        @Override
        public List<StickerPack> getStickerPacks() {
            return List.of();
        }

        @Override
        public void requestAllSyncData() {
        }

        @Override
        public boolean isContactBlocked(RecipientIdentifier.Single recipient) {
            return false;
        }

        @Override
        public void sendContacts() {
        }

        @Override
        public List<Recipient> getRecipients(boolean onlyWithProfile, Optional<Boolean> blocked, Collection<RecipientIdentifier.Single> addresses, Optional<String> name) {
            return List.of();
        }

        @Override
        public String getContactOrProfileName(RecipientIdentifier.Single recipient) {
            return null;
        }

        @Override
        public Group getGroup(GroupId groupId) {
            return null;
        }

        @Override
        public List<Identity> getIdentities() {
            return List.of();
        }

        @Override
        public List<Identity> getIdentities(RecipientIdentifier.Single recipient) {
            return List.of();
        }

        @Override
        public boolean trustIdentityVerified(RecipientIdentifier.Single recipient, IdentityVerificationCode verificationCode) {
            return false;
        }

        @Override
        public boolean trustIdentityAllKeys(RecipientIdentifier.Single recipient) {
            return false;
        }

        @Override
        public void addAddressChangedListener(Runnable listener) {
        }

        @Override
        public void addClosedListener(Runnable listener) {
        }

        @Override
        public InputStream retrieveAttachment(String id) {
            return null;
        }

        @Override
        public InputStream retrieveContactAvatar(RecipientIdentifier.Single recipient) {
            return null;
        }

        @Override
        public InputStream retrieveProfileAvatar(RecipientIdentifier.Single recipient) {
            return null;
        }

        @Override
        public InputStream retrieveGroupAvatar(GroupId groupId) {
            return null;
        }

        @Override
        public InputStream retrieveSticker(StickerPackId stickerPackId, int stickerId) {
            return null;
        }

        @Override
        public CallInfo startCall(RecipientIdentifier.Single recipient) {
            return null;
        }

        @Override
        public CallInfo acceptCall(long callId) {
            return null;
        }

        @Override
        public void hangupCall(long callId) {
        }

        @Override
        public SendMessageResult rejectCall(long callId) {
            return null;
        }

        @Override
        public List<CallInfo> listActiveCalls() {
            return List.of();
        }

        @Override
        public void sendCallOffer(RecipientIdentifier.Single recipient, CallOffer callOffer) {
        }

        @Override
        public void sendCallAnswer(RecipientIdentifier.Single recipient, long callId, byte[] answer) {
        }

        @Override
        public void sendIceUpdate(RecipientIdentifier.Single recipient, long callId, List<byte[]> iceCandidates) {
        }

        @Override
        public void sendHangup(RecipientIdentifier.Single recipient, long callId, MessageEnvelope.Call.Hangup.Type type) {
        }

        @Override
        public void sendBusy(RecipientIdentifier.Single recipient, long callId) {
        }

        @Override
        public List<TurnServer> getTurnServerInfo() {
            return List.of();
        }

        @Override
        public void close() {
        }

        @Override
        public void addCallEventListener(CallEventListener listener) {
        }

        @Override
        public void removeCallEventListener(CallEventListener listener) {
        }
    }
}

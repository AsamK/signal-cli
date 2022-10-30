use std::path::Path;

use jsonrpc_client_transports::{transports::ipc, RpcError};
use jsonrpc_core::serde::Deserialize;
use jsonrpc_derive::rpc;
use tokio::net::ToSocketAddrs;

pub type SignalCliClient = gen_client::Client;

#[rpc(client, params = "named")]
pub trait Rpc {
    #[rpc(name = "addDevice", params = "named")]
    fn add_device(&self, account: Option<String>, uri: String) -> Result<Value>;

    #[rpc(name = "block", params = "named")]
    fn block(
        &self,
        account: Option<String>,
        recipients: Vec<String>,
        #[allow(non_snake_case)] groupIds: Vec<String>,
    ) -> Result<Value>;

    #[rpc(name = "deleteLocalAccountData", params = "named")]
    fn delete_local_account_data(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] ignoreRegistered: Option<bool>,
    ) -> Result<Value>;

    #[rpc(name = "getUserStatus", params = "named")]
    fn get_user_status(&self, account: Option<String>, recipients: Vec<String>) -> Result<Value>;

    #[rpc(name = "joinGroup", params = "named")]
    fn join_group(&self, account: Option<String>, uri: String) -> Result<Value>;

    #[rpc(name = "finishLink", params = "named")]
    fn finish_link(
        &self,
        #[allow(non_snake_case)] deviceLinkUri: String,
        #[allow(non_snake_case)] deviceName: String,
    ) -> Result<Value>;

    #[rpc(name = "listAccounts", params = "named")]
    fn list_accounts(&self) -> Result<Value>;

    #[rpc(name = "listContacts", params = "named")]
    fn list_contacts(
        &self,
        account: Option<String>,
        recipients: Vec<String>,
        #[allow(non_snake_case)] allRecipients: bool,
        blocked: Option<bool>,
        name: Option<String>,
    ) -> Result<Value>;

    #[rpc(name = "listDevices", params = "named")]
    fn list_devices(&self, account: Option<String>) -> Result<Value>;

    #[rpc(name = "listGroups", params = "named")]
    fn list_groups(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] groupIds: Vec<String>,
    ) -> Result<Value>;

    #[rpc(name = "listIdentities", params = "named")]
    fn list_identities(&self, account: Option<String>, number: Option<String>) -> Result<Value>;

    #[rpc(name = "listStickerPacks", params = "named")]
    fn list_sticker_packs(&self, account: Option<String>) -> Result<Value>;

    #[rpc(name = "quitGroup", params = "named")]
    fn quit_group(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] groupId: String,
        delete: bool,
        admins: Vec<String>,
    ) -> Result<Value>;

    #[rpc(name = "register", params = "named")]
    fn register(
        &self,
        account: Option<String>,
        voice: bool,
        captcha: Option<String>,
    ) -> Result<Value>;

    #[rpc(name = "removeContact", params = "named")]
    fn remove_contact(
        &self,
        account: Option<String>,
        recipient: String,
        forget: bool,
    ) -> Result<Value>;

    #[rpc(name = "removeDevice", params = "named")]
    fn remove_device(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] deviceId: u32,
    ) -> Result<Value>;

    #[rpc(name = "removePin", params = "named")]
    fn remove_pin(&self, account: Option<String>) -> Result<Value>;

    #[rpc(name = "remoteDelete", params = "named")]
    fn remote_delete(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] targetTimestamp: u64,
        recipients: Vec<String>,
        #[allow(non_snake_case)] groupIds: Vec<String>,
        #[allow(non_snake_case)] noteToSelf: bool,
    ) -> Result<Value>;

    #[rpc(name = "send", params = "named")]
    fn send(
        &self,
        account: Option<String>,
        recipients: Vec<String>,
        #[allow(non_snake_case)] groupIds: Vec<String>,
        #[allow(non_snake_case)] noteToSelf: bool,
        #[allow(non_snake_case)] endSession: bool,
        message: String,
        attachments: Vec<String>,
        mentions: Vec<String>,
        #[allow(non_snake_case)] quoteTimestamp: Option<u64>,
        #[allow(non_snake_case)] quoteAuthor: Option<String>,
        #[allow(non_snake_case)] quoteMessage: Option<String>,
        #[allow(non_snake_case)] quoteMention: Vec<String>,
        sticker: Option<String>,
        #[allow(non_snake_case)] storyTimestamp: Option<u64>,
        #[allow(non_snake_case)] storyAuthor: Option<String>,
    ) -> Result<Value>;

    #[rpc(name = "sendContacts", params = "named")]
    fn send_contacts(&self, account: Option<String>) -> Result<Value>;

    #[rpc(name = "sendPaymentNotification", params = "named")]
    fn send_payment_notification(
        &self,
        account: Option<String>,
        recipient: String,
        receipt: String,
        note: String,
    ) -> Result<Value>;

    #[rpc(name = "sendReaction", params = "named")]
    fn send_reaction(
        &self,
        account: Option<String>,
        recipients: Vec<String>,
        #[allow(non_snake_case)] groupIds: Vec<String>,
        #[allow(non_snake_case)] noteToSelf: bool,
        emoji: String,
        #[allow(non_snake_case)] targetAuthor: String,
        #[allow(non_snake_case)] targetTimestamp: u64,
        remove: bool,
        story: bool,
    ) -> Result<Value>;

    #[rpc(name = "sendReceipt", params = "named")]
    fn send_receipt(
        &self,
        account: Option<String>,
        recipient: String,
        #[allow(non_snake_case)] targetTimestamps: Vec<u64>,
        r#type: String,
    ) -> Result<Value>;

    #[rpc(name = "sendSyncRequest", params = "named")]
    fn send_sync_request(&self, account: Option<String>) -> Result<Value>;

    #[rpc(name = "sendTyping", params = "named")]
    fn send_typing(
        &self,
        account: Option<String>,
        recipients: Vec<String>,
        #[allow(non_snake_case)] groupIds: Vec<String>,
        stop: bool,
    ) -> Result<Value>;

    #[rpc(name = "setPin", params = "named")]
    fn set_pin(&self, account: Option<String>, pin: String) -> Result<Value>;

    #[rpc(name = "submitRateLimitChallenge", params = "named")]
    fn submit_rate_limit_challenge(
        &self,
        account: Option<String>,
        challenge: String,
        captcha: String,
    ) -> Result<Value>;

    #[rpc(name = "startLink", params = "named")]
    fn start_link(&self, account: Option<String>) -> Result<JsonLink>;

    #[rpc(name = "trust", params = "named")]
    fn trust(
        &self,
        account: Option<String>,
        recipient: String,
        #[allow(non_snake_case)] trustAllKnownKeys: bool,
        #[allow(non_snake_case)] verifiedSafetyNumber: Option<String>,
    ) -> Result<Value>;

    #[rpc(name = "unblock", params = "named")]
    fn unblock(
        &self,
        account: Option<String>,
        recipients: Vec<String>,
        #[allow(non_snake_case)] groupIds: Vec<String>,
    ) -> Result<Value>;

    #[rpc(name = "unregister", params = "named")]
    fn unregister(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] deleteAccount: bool,
    ) -> Result<Value>;

    #[rpc(name = "updateAccount", params = "named")]
    fn update_account(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] deviceName: Option<String>,
    ) -> Result<Value>;

    #[rpc(name = "updateConfiguration", params = "named")]
    fn update_configuration(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] readReceiptes: Option<bool>,
        #[allow(non_snake_case)] unidentifiedDeliveryIndicators: Option<bool>,
        #[allow(non_snake_case)] typingIndicators: Option<bool>,
        #[allow(non_snake_case)] linkPreviews: Option<bool>,
    ) -> Result<Value>;

    #[rpc(name = "updateContact", params = "named")]
    fn update_contact(
        &self,
        account: Option<String>,
        recipient: String,
        name: Option<String>,
        expiration: Option<u32>,
    ) -> Result<Value>;

    #[rpc(name = "updateGroup", params = "named")]
    fn update_group(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] groupId: Option<String>,
        name: Option<String>,
        description: Option<String>,
        avatar: Option<String>,
        member: Vec<String>,
        #[allow(non_snake_case)] removeMember: Vec<String>,
        admin: Vec<String>,
        #[allow(non_snake_case)] removeAdmin: Vec<String>,
        ban: Vec<String>,
        unban: Vec<String>,
        #[allow(non_snake_case)] resetLink: bool,
        #[allow(non_snake_case)] link: Option<String>,
        #[allow(non_snake_case)] setPermissionAddMember: Option<String>,
        #[allow(non_snake_case)] setPermissionEditDetails: Option<String>,
        #[allow(non_snake_case)] setPermissionSendMessages: Option<String>,
        expiration: Option<u32>,
    ) -> Result<Value>;

    #[rpc(name = "updateProfile", params = "named")]
    fn update_profile(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] givenName: Option<String>,
        #[allow(non_snake_case)] familyName: Option<String>,
        about: Option<String>,
        #[allow(non_snake_case)] aboutEmoji: Option<String>,
        #[allow(non_snake_case)] mobileCoinAddress: Option<String>,
        avatar: Option<String>,
        #[allow(non_snake_case)] removeAvatar: bool,
    ) -> Result<Value>;

    #[rpc(name = "uploadStickerPack", params = "named")]
    fn upload_sticker_pack(&self, account: Option<String>, path: String) -> Result<Value>;

    #[rpc(name = "verify", params = "named")]
    fn verify(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] verificationCode: String,
        pin: Option<String>,
    ) -> Result<Value>;

    #[pubsub(
        subscription = "receive",
        subscribe,
        name = "subscribeReceive",
        params = "named"
    )]
    fn subscribe_receive(&self, _: Self::Metadata, _: Subscriber<Value>, account: Option<String>);

    #[pubsub(subscription = "receive", unsubscribe, name = "unsubscribeReceive")]
    fn unsubscribe_receive(&self, _: Option<Self::Metadata>, _: SubscriptionId) -> Result<bool>;

    #[rpc(name = "version")]
    fn version(&self) -> Result<Value>;
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JsonLink {
    pub device_link_uri: String,
}

pub async fn connect_tcp(tcp: impl ToSocketAddrs) -> Result<SignalCliClient, RpcError> {
    super::tcp::connect::<_, SignalCliClient>(tcp).await
}

pub async fn connect_unix(socket_path: impl AsRef<Path>) -> Result<SignalCliClient, RpcError> {
    ipc::connect::<_, SignalCliClient>(socket_path).await
}

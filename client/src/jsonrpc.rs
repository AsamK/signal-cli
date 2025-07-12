use std::path::Path;

use jsonrpsee::async_client::ClientBuilder;
use jsonrpsee::core::client::{Error, SubscriptionClientT};
use jsonrpsee::http_client::HttpClientBuilder;
use jsonrpsee::proc_macros::rpc;
use serde::Deserialize;
use serde_json::Value;
use tokio::net::ToSocketAddrs;

#[rpc(client)]
pub trait Rpc {
    #[method(name = "addDevice", param_kind = map)]
    async fn add_device(
        &self,
        account: Option<String>,
        uri: String,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "addStickerPack", param_kind = map)]
    async fn add_sticker_pack(
        &self,
        account: Option<String>,
        uri: String,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "block", param_kind = map)]
    fn block(
        &self,
        account: Option<String>,
        recipients: Vec<String>,
        #[allow(non_snake_case)] groupIds: Vec<String>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "deleteLocalAccountData", param_kind = map)]
    fn delete_local_account_data(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] ignoreRegistered: Option<bool>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "getAttachment", param_kind = map)]
    fn get_attachment(
        &self,
        account: Option<String>,
        id: String,
        recipient: Option<String>,
        #[allow(non_snake_case)] groupId: Option<String>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "getAvatar", param_kind = map)]
    fn get_avatar(
        &self,
        account: Option<String>,
        contact: Option<String>,
        profile: Option<String>,
        #[allow(non_snake_case)] groupId: Option<String>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "getSticker", param_kind = map)]
    fn get_sticker(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] packId: String,
        #[allow(non_snake_case)] stickerId: u32,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "getUserStatus", param_kind = map)]
    fn get_user_status(
        &self,
        account: Option<String>,
        recipients: Vec<String>,
        usernames: Vec<String>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "joinGroup", param_kind = map)]
    fn join_group(&self, account: Option<String>, uri: String) -> Result<Value, ErrorObjectOwned>;

    #[allow(non_snake_case)]
    #[method(name = "finishChangeNumber", param_kind = map)]
    fn finish_change_number(
        &self,
        account: Option<String>,
        number: String,
        verificationCode: String,
        pin: Option<String>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "finishLink", param_kind = map)]
    fn finish_link(
        &self,
        #[allow(non_snake_case)] deviceLinkUri: String,
        #[allow(non_snake_case)] deviceName: String,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "listAccounts", param_kind = map)]
    fn list_accounts(&self) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "listContacts", param_kind = map)]
    fn list_contacts(
        &self,
        account: Option<String>,
        recipients: Vec<String>,
        #[allow(non_snake_case)] allRecipients: bool,
        blocked: Option<bool>,
        name: Option<String>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "listDevices", param_kind = map)]
    fn list_devices(&self, account: Option<String>) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "listGroups", param_kind = map)]
    fn list_groups(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] groupIds: Vec<String>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "listIdentities", param_kind = map)]
    fn list_identities(
        &self,
        account: Option<String>,
        number: Option<String>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "listStickerPacks", param_kind = map)]
    fn list_sticker_packs(&self, account: Option<String>) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "quitGroup", param_kind = map)]
    fn quit_group(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] groupId: String,
        delete: bool,
        admins: Vec<String>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "register", param_kind = map)]
    fn register(
        &self,
        account: Option<String>,
        voice: bool,
        captcha: Option<String>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "removeContact", param_kind = map)]
    fn remove_contact(
        &self,
        account: Option<String>,
        recipient: String,
        forget: bool,
        hide: bool,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "removeDevice", param_kind = map)]
    fn remove_device(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] deviceId: u32,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "removePin", param_kind = map)]
    fn remove_pin(&self, account: Option<String>) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "remoteDelete", param_kind = map)]
    fn remote_delete(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] targetTimestamp: u64,
        recipients: Vec<String>,
        #[allow(non_snake_case)] groupIds: Vec<String>,
        #[allow(non_snake_case)] noteToSelf: bool,
    ) -> Result<Value, ErrorObjectOwned>;

    #[allow(non_snake_case)]
    #[method(name = "send", param_kind = map)]
    fn send(
        &self,
        account: Option<String>,
        recipients: Vec<String>,
        groupIds: Vec<String>,
        noteToSelf: bool,
        endSession: bool,
        message: String,
        attachments: Vec<String>,
        mentions: Vec<String>,
        textStyle: Vec<String>,
        quoteTimestamp: Option<u64>,
        quoteAuthor: Option<String>,
        quoteMessage: Option<String>,
        quoteMention: Vec<String>,
        quoteTextStyle: Vec<String>,
        quoteAttachment: Vec<String>,
        preview_url: Option<String>,
        preview_title: Option<String>,
        preview_description: Option<String>,
        preview_image: Option<String>,
        sticker: Option<String>,
        storyTimestamp: Option<u64>,
        storyAuthor: Option<String>,
        editTimestamp: Option<u64>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "sendContacts", param_kind = map)]
    fn send_contacts(&self, account: Option<String>) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "sendPaymentNotification", param_kind = map)]
    fn send_payment_notification(
        &self,
        account: Option<String>,
        recipient: String,
        receipt: String,
        note: String,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "sendReaction", param_kind = map)]
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
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "sendReceipt", param_kind = map)]
    fn send_receipt(
        &self,
        account: Option<String>,
        recipient: String,
        #[allow(non_snake_case)] targetTimestamps: Vec<u64>,
        r#type: String,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "sendSyncRequest", param_kind = map)]
    fn send_sync_request(&self, account: Option<String>) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "sendTyping", param_kind = map)]
    fn send_typing(
        &self,
        account: Option<String>,
        recipients: Vec<String>,
        #[allow(non_snake_case)] groupIds: Vec<String>,
        stop: bool,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "sendMessageRequestResponse", param_kind = map)]
    fn send_message_request_response(
        &self,
        account: Option<String>,
        recipients: Vec<String>,
        #[allow(non_snake_case)] groupIds: Vec<String>,
        r#type: String,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "setPin", param_kind = map)]
    fn set_pin(&self, account: Option<String>, pin: String) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "submitRateLimitChallenge", param_kind = map)]
    fn submit_rate_limit_challenge(
        &self,
        account: Option<String>,
        challenge: String,
        captcha: String,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "startChangeNumber", param_kind = map)]
    fn start_change_number(
        &self,
        account: Option<String>,
        number: String,
        voice: bool,
        captcha: Option<String>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "startLink", param_kind = map)]
    fn start_link(&self, account: Option<String>) -> Result<JsonLink, ErrorObjectOwned>;

    #[method(name = "trust", param_kind = map)]
    fn trust(
        &self,
        account: Option<String>,
        recipient: String,
        #[allow(non_snake_case)] trustAllKnownKeys: bool,
        #[allow(non_snake_case)] verifiedSafetyNumber: Option<String>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "unblock", param_kind = map)]
    fn unblock(
        &self,
        account: Option<String>,
        recipients: Vec<String>,
        #[allow(non_snake_case)] groupIds: Vec<String>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "unregister", param_kind = map)]
    fn unregister(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] deleteAccount: bool,
    ) -> Result<Value, ErrorObjectOwned>;

    #[allow(non_snake_case)]
    #[method(name = "updateAccount", param_kind = map)]
    fn update_account(
        &self,
        account: Option<String>,
        deviceName: Option<String>,
        unrestrictedUnidentifiedSender: Option<bool>,
        discoverableByNumber: Option<bool>,
        numberSharing: Option<bool>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "updateConfiguration", param_kind = map)]
    fn update_configuration(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] readReceipts: Option<bool>,
        #[allow(non_snake_case)] unidentifiedDeliveryIndicators: Option<bool>,
        #[allow(non_snake_case)] typingIndicators: Option<bool>,
        #[allow(non_snake_case)] linkPreviews: Option<bool>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "updateContact", param_kind = map)]
    fn update_contact(
        &self,
        account: Option<String>,
        recipient: String,
        name: Option<String>,
        expiration: Option<u32>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "updateGroup", param_kind = map)]
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
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "updateProfile", param_kind = map)]
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
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "uploadStickerPack", param_kind = map)]
    fn upload_sticker_pack(
        &self,
        account: Option<String>,
        path: String,
    ) -> Result<Value, ErrorObjectOwned>;

    #[method(name = "verify", param_kind = map)]
    fn verify(
        &self,
        account: Option<String>,
        #[allow(non_snake_case)] verificationCode: String,
        pin: Option<String>,
    ) -> Result<Value, ErrorObjectOwned>;

    #[subscription(
        name = "subscribeReceive" => "receive",
        unsubscribe = "unsubscribeReceive",
        item = Value,
        param_kind = map
    )]
    async fn subscribe_receive(&self, account: Option<String>) -> SubscriptionResult;

    #[method(name = "version")]
    fn version(&self) -> Result<Value, ErrorObjectOwned>;
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JsonLink {
    pub device_link_uri: String,
}

pub async fn connect_tcp(
    tcp: impl ToSocketAddrs,
) -> Result<impl SubscriptionClientT, std::io::Error> {
    let (sender, receiver) = super::transports::tcp::connect(tcp).await?;

    Ok(ClientBuilder::default().build_with_tokio(sender, receiver))
}

pub async fn connect_unix(
    socket_path: impl AsRef<Path>,
) -> Result<impl SubscriptionClientT, std::io::Error> {
    let (sender, receiver) = super::transports::ipc::connect(socket_path).await?;

    Ok(ClientBuilder::default().build_with_tokio(sender, receiver))
}

pub async fn connect_http(uri: &str) -> Result<impl SubscriptionClientT + use<>, Error> {
    HttpClientBuilder::default().build(uri)
}

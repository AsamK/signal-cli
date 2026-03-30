use std::{ffi::OsString, net::SocketAddr};

use clap::{crate_version, Parser, Subcommand, ValueEnum};

/// JSON-RPC client for signal-cli
#[derive(Parser, Debug)]
#[command(rename_all = "kebab-case", version = crate_version!())]
pub struct Cli {
    /// Account to use (for daemon in multi-account mode)
    #[arg(short = 'a', long)]
    pub account: Option<String>,

    #[arg(long)]
    pub output: Option<String>,

    /// TCP host and port of signal-cli daemon
    #[arg(long, conflicts_with = "json_rpc_http")]
    pub json_rpc_tcp: Option<Option<SocketAddr>>,

    /// UNIX socket address and port of signal-cli daemon
    #[cfg(unix)]
    #[arg(long, conflicts_with = "json_rpc_tcp")]
    pub json_rpc_socket: Option<Option<OsString>>,

    /// HTTP URL of signal-cli daemon
    #[arg(long, conflicts_with = "json_rpc_socket")]
    pub json_rpc_http: Option<Option<String>>,

    #[arg(long)]
    pub verbose: bool,

    #[command(subcommand)]
    pub command: CliCommands,
}

#[allow(clippy::large_enum_variant)]
#[derive(Subcommand, Debug)]
#[command(rename_all = "camelCase", version = crate_version!())]
pub enum CliCommands {
    AddDevice {
        #[arg(long)]
        uri: String,
    },
    AddStickerPack {
        #[arg(long)]
        uri: String,
    },
    #[command(rename_all = "kebab-case")]
    Block {
        recipient: Vec<String>,

        #[arg(short = 'g', long)]
        group_id: Vec<String>,
    },
    DeleteLocalAccountData {
        #[arg(long = "ignore-registered")]
        ignore_registered: Option<bool>,
    },
    FinishChangeNumber {
        number: String,
        #[arg(short = 'v', long = "verification-code")]
        verification_code: String,

        #[arg(short = 'p', long)]
        pin: Option<String>,
    },
    GetAttachment {
        #[arg(long)]
        id: String,
        #[arg(long)]
        recipient: Option<String>,
        #[arg(short = 'g', long = "group-id")]
        group_id: Option<String>,
    },
    GetAvatar {
        #[arg(long)]
        contact: Option<String>,
        #[arg(long)]
        profile: Option<String>,
        #[arg(short = 'g', long = "group-id")]
        group_id: Option<String>,
    },
    GetSticker {
        #[arg(long = "pack-id")]
        pack_id: String,
        #[arg(long = "sticker-id")]
        sticker_id: u32,
    },
    GetUserStatus {
        recipient: Vec<String>,
        #[arg(long)]
        username: Vec<String>,
    },
    JoinGroup {
        #[arg(long)]
        uri: String,
    },
    Link {
        #[arg(short = 'n', long)]
        name: Option<String>,
    },
    ListAccounts,
    ListContacts {
        recipient: Vec<String>,
        #[arg(short = 'a', long = "all-recipients")]
        all_recipients: bool,
        #[arg(long)]
        blocked: Option<bool>,
        #[arg(long)]
        name: Option<String>,
        #[arg(long)]
        detailed: bool,
        #[arg(long)]
        internal: bool,
    },
    ListDevices,
    ListGroups {
        #[arg(short = 'd', long)]
        detailed: bool,
        #[arg(short = 'g', long = "group-id")]
        group_id: Vec<String>,
    },
    ListIdentities {
        #[arg(short = 'n', long)]
        number: Option<String>,
    },
    ListStickerPacks,
    QuitGroup {
        #[arg(short = 'g', long = "group-id")]
        group_id: String,
        #[arg(long)]
        delete: bool,
        #[arg(long)]
        admin: Vec<String>,
    },
    Receive {
        #[arg(short = 't', long, default_value_t = 3.0)]
        timeout: f64,
    },
    Register {
        #[arg(short = 'v', long)]
        voice: bool,
        #[arg(long)]
        captcha: Option<String>,
        #[arg(long)]
        reregister: bool,
    },
    RemoveContact {
        recipient: String,
        #[arg(long)]
        forget: bool,
        #[arg(long)]
        hide: bool,
    },
    RemoveDevice {
        #[arg(short = 'd', long = "device-id")]
        device_id: u32,
    },
    RemovePin,
    RemoteDelete {
        #[arg(short = 't', long = "target-timestamp")]
        target_timestamp: u64,

        recipient: Vec<String>,

        #[arg(short = 'g', long = "group-id")]
        group_id: Vec<String>,

        #[arg(long = "note-to-self")]
        note_to_self: bool,
    },
    #[command(rename_all = "kebab-case")]
    Send {
        recipient: Vec<String>,

        #[arg(short = 'g', long)]
        group_id: Vec<String>,

        #[arg(short = 'u', long = "username")]
        username: Vec<String>,

        #[arg(long)]
        note_to_self: bool,

        #[arg(long)]
        notify_self: bool,

        #[arg(short = 'e', long)]
        end_session: bool,

        #[arg(short = 'm', long)]
        message: Option<String>,

        #[arg(long)]
        message_from_stdin: bool,

        #[arg(short = 'a', long)]
        attachment: Vec<String>,

        #[arg(long)]
        view_once: bool,

        #[arg(long)]
        mention: Vec<String>,

        #[arg(long)]
        text_style: Vec<String>,

        #[arg(long)]
        quote_timestamp: Option<u64>,

        #[arg(long)]
        quote_author: Option<String>,

        #[arg(long)]
        quote_message: Option<String>,

        #[arg(long)]
        quote_mention: Vec<String>,

        #[arg(long)]
        quote_text_style: Vec<String>,

        #[arg(long)]
        quote_attachment: Vec<String>,

        #[arg(long)]
        preview_url: Option<String>,

        #[arg(long)]
        preview_title: Option<String>,

        #[arg(long)]
        preview_description: Option<String>,

        #[arg(long)]
        preview_image: Option<String>,

        #[arg(long)]
        sticker: Option<String>,

        #[arg(long)]
        story_timestamp: Option<u64>,

        #[arg(long)]
        story_author: Option<String>,

        #[arg(long)]
        edit_timestamp: Option<u64>,

        #[arg(long = "no-urgent")]
        no_urgent: bool,
    },
    SendAdminDelete {
        #[arg(short = 'g', long = "group-id")]
        group_id: Vec<String>,

        #[arg(short = 'a', long = "target-author")]
        target_author: String,

        #[arg(short = 't', long = "target-timestamp")]
        target_timestamp: u64,

        #[arg(long)]
        story: bool,

        #[arg(long)]
        notify_self: bool,
    },
    SendContacts,
    SendPaymentNotification {
        recipient: String,

        #[arg(long)]
        receipt: String,

        #[arg(long)]
        note: String,
    },
    SendPinMessage {
        recipient: Vec<String>,

        #[arg(short = 'g', long = "group-id")]
        group_id: Vec<String>,

        #[arg(short = 'u', long = "username")]
        username: Vec<String>,

        #[arg(short = 'a', long = "target-author")]
        target_author: String,

        #[arg(short = 't', long = "target-timestamp")]
        target_timestamp: u64,

        #[arg(short = 'd', long = "pin-duration")]
        pin_duration: Option<i32>,

        #[arg(long = "note-to-self")]
        note_to_self: bool,

        #[arg(long)]
        notify_self: bool,

        #[arg(long)]
        story: bool,
    },
    SendPollCreate {
        recipient: Vec<String>,

        #[arg(short = 'g', long = "group-id")]
        group_id: Vec<String>,

        #[arg(short = 'u', long = "username")]
        username: Vec<String>,

        #[arg(short = 'q', long = "question")]
        question: String,

        #[arg(short = 'o', long = "option")]
        option: Vec<String>,

        #[arg(long = "no-multi")]
        no_multi: bool,

        #[arg(long = "note-to-self")]
        note_to_self: bool,

        #[arg(long)]
        notify_self: bool,
    },
    SendPollTerminate {
        recipient: Vec<String>,

        #[arg(short = 'g', long = "group-id")]
        group_id: Vec<String>,

        #[arg(short = 'u', long = "username")]
        username: Vec<String>,

        #[arg(long = "poll-timestamp")]
        poll_timestamp: u64,

        #[arg(long = "note-to-self")]
        note_to_self: bool,

        #[arg(long)]
        notify_self: bool,
    },
    SendPollVote {
        recipient: Vec<String>,

        #[arg(short = 'g', long = "group-id")]
        group_id: Vec<String>,

        #[arg(short = 'u', long = "username")]
        username: Vec<String>,

        #[arg(long = "poll-author")]
        poll_author: Option<String>,

        #[arg(long = "poll-timestamp")]
        poll_timestamp: u64,

        #[arg(short = 'o', long = "option")]
        option: Vec<i32>,

        #[arg(long = "vote-count")]
        vote_count: i32,

        #[arg(long = "note-to-self")]
        note_to_self: bool,

        #[arg(long)]
        notify_self: bool,
    },
    SendReaction {
        recipient: Vec<String>,

        #[arg(short = 'g', long = "group-id")]
        group_id: Vec<String>,

        #[arg(short = 'u', long = "username")]
        username: Vec<String>,

        #[arg(long = "note-to-self")]
        note_to_self: bool,

        #[arg(long)]
        notify_self: bool,

        #[arg(short = 'e', long)]
        emoji: String,

        #[arg(short = 'a', long = "target-author")]
        target_author: String,

        #[arg(short = 't', long = "target-timestamp")]
        target_timestamp: u64,

        #[arg(short = 'r', long)]
        remove: bool,

        #[arg(long)]
        story: bool,
    },
    SendReceipt {
        recipient: String,

        #[arg(short = 'u', long = "username")]
        username: Vec<String>,

        #[arg(short = 't', long = "target-timestamp")]
        target_timestamp: Vec<u64>,

        #[arg(value_enum, long)]
        r#type: ReceiptType,
    },
    SendSyncRequest,
    SendTyping {
        recipient: Vec<String>,

        #[arg(short = 'g', long = "group-id")]
        group_id: Vec<String>,

        #[arg(short = 's', long)]
        stop: bool,
    },
    SendUnpinMessage {
        recipient: Vec<String>,

        #[arg(short = 'g', long = "group-id")]
        group_id: Vec<String>,

        #[arg(short = 'u', long = "username")]
        username: Vec<String>,

        #[arg(short = 'a', long = "target-author")]
        target_author: String,

        #[arg(short = 't', long = "target-timestamp")]
        target_timestamp: u64,

        #[arg(long = "note-to-self")]
        note_to_self: bool,

        #[arg(long)]
        notify_self: bool,

        #[arg(long)]
        story: bool,
    },
    SendMessageRequestResponse {
        recipient: Vec<String>,

        #[arg(short = 'g', long = "group-id")]
        group_id: Vec<String>,

        #[arg(long)]
        r#type: MessageRequestResponseType,
    },
    SetPin {
        pin: String,
    },
    StartChangeNumber {
        number: String,
        #[arg(short = 'v', long)]
        voice: bool,
        #[arg(long)]
        captcha: Option<String>,
    },
    SubmitRateLimitChallenge {
        challenge: String,
        captcha: String,
    },
    Trust {
        recipient: String,

        #[arg(short = 'a', long = "trust-all-known-keys")]
        trust_all_known_keys: bool,

        #[arg(short = 'v', long = "verified-safety-number")]
        verified_safety_number: Option<String>,
    },
    #[command(rename_all = "kebab-case")]
    Unblock {
        recipient: Vec<String>,

        #[arg(short = 'g', long)]
        group_id: Vec<String>,
    },
    Unregister {
        #[arg(long = "delete-account")]
        delete_account: bool,
    },
    UpdateAccount {
        #[arg(short = 'n', long = "device-name")]
        device_name: Option<String>,
        #[arg(long = "unrestricted-unidentified-sender")]
        unrestricted_unidentified_sender: Option<bool>,
        #[arg(long = "discoverable-by-number")]
        discoverable_by_number: Option<bool>,
        #[arg(long = "number-sharing")]
        number_sharing: Option<bool>,
        #[arg(short = 'u', long = "username")]
        username: Option<String>,
        #[arg(long = "delete-username")]
        delete_username: bool,
    },
    UpdateConfiguration {
        #[arg(long = "read-receipts")]
        read_receipts: Option<bool>,

        #[arg(long = "unidentified-delivery-indicators")]
        unidentified_delivery_indicators: Option<bool>,

        #[arg(long = "typing-indicators")]
        typing_indicators: Option<bool>,

        #[arg(long = "link-previews")]
        link_previews: Option<bool>,
    },
    UpdateContact {
        recipient: String,

        #[arg(short = 'e', long)]
        expiration: Option<u32>,

        #[arg(short = 'n', long)]
        name: Option<String>,

        #[arg(long = "given-name")]
        given_name: Option<String>,

        #[arg(long = "family-name")]
        family_name: Option<String>,

        #[arg(long = "nick-given-name")]
        nick_given_name: Option<String>,

        #[arg(long = "nick-family-name")]
        nick_family_name: Option<String>,

        #[arg(long)]
        note: Option<String>,
    },
    UpdateDevice {
        #[arg(short = 'd', long = "device-id")]
        device_id: u32,

        #[arg(short = 'n', long = "device-name")]
        device_name: String,
    },
    UpdateGroup {
        #[arg(short = 'g', long = "group-id")]
        group_id: Option<String>,

        #[arg(short = 'n', long)]
        name: Option<String>,

        #[arg(short = 'd', long)]
        description: Option<String>,

        #[arg(short = 'a', long)]
        avatar: Option<String>,

        #[arg(short = 'm', long)]
        member: Vec<String>,

        #[arg(short = 'r', long = "remove-member")]
        remove_member: Vec<String>,

        #[arg(long)]
        admin: Vec<String>,

        #[arg(long = "remove-admin")]
        remove_admin: Vec<String>,

        #[arg(long)]
        ban: Vec<String>,

        #[arg(long)]
        unban: Vec<String>,

        #[arg(long = "reset-link")]
        reset_link: bool,

        #[arg(value_enum, long)]
        link: Option<LinkState>,

        #[arg(value_enum, long = "set-permission-add-member")]
        set_permission_add_member: Option<GroupPermission>,

        #[arg(value_enum, long = "set-permission-edit-details")]
        set_permission_edit_details: Option<GroupPermission>,

        #[arg(value_enum, long = "set-permission-send-messages")]
        set_permission_send_messages: Option<GroupPermission>,

        #[arg(short = 'e', long)]
        expiration: Option<u32>,

        #[arg(long = "member-label-emoji")]
        member_label_emoji: Option<String>,

        #[arg(long = "member-label")]
        member_label: Option<String>,
    },
    UpdateProfile {
        #[arg(long = "given-name")]
        given_name: Option<String>,

        #[arg(long = "family-name")]
        family_name: Option<String>,

        #[arg(long)]
        about: Option<String>,

        #[arg(long = "about-emoji")]
        about_emoji: Option<String>,

        #[arg(long = "mobile-coin-address", visible_alias = "mobilecoin-address")]
        mobile_coin_address: Option<String>,

        #[arg(long)]
        avatar: Option<String>,

        #[arg(long = "remove-avatar")]
        remove_avatar: bool,
    },
    UploadStickerPack {
        path: String,
    },
    Verify {
        verification_code: String,

        #[arg(short = 'p', long)]
        pin: Option<String>,
    },
    Version,
}

#[derive(ValueEnum, Clone, Debug)]
#[value(rename_all = "kebab-case")]
pub enum ReceiptType {
    Read,
    Viewed,
}

#[derive(ValueEnum, Clone, Debug)]
#[value(rename_all = "kebab-case")]
pub enum LinkState {
    Enabled,
    EnabledWithApproval,
    Disabled,
}

#[derive(ValueEnum, Clone, Debug)]
#[value(rename_all = "kebab-case")]
pub enum GroupPermission {
    EveryMember,
    OnlyAdmins,
}

#[derive(ValueEnum, Clone, Debug)]
#[value(rename_all = "kebab-case")]
pub enum MessageRequestResponseType {
    Accept,
    Delete,
}

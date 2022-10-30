use clap::{crate_version, Parser, Subcommand, ValueEnum};
use std::{ffi::OsString, net::SocketAddr};

/// JSON-RPC client for signal-cli
#[derive(Parser, Debug)]
#[command(rename_all = "kebab-case", version=crate_version!())]
pub struct Cli {
    /// Account to use (for daemon in multi-account mode)
    #[arg(short = 'a', long)]
    pub account: Option<String>,

    /// TCP host and port of signal-cli daemon
    #[arg(long)]
    pub json_rpc_tcp: Option<Option<SocketAddr>>,

    /// UNIX socket address and port of signal-cli daemon
    #[arg(long)]
    pub json_rpc_socket: Option<Option<OsString>>,

    #[arg(value_enum, long, default_value_t = OutputTypes::Json)]
    pub output: OutputTypes,

    #[arg(long)]
    pub verbose: bool,

    #[command(subcommand)]
    pub command: CliCommands,
}

#[derive(ValueEnum, Clone, Debug)]
#[value(rename_all = "kebab-case")]
pub enum OutputTypes {
    PlainText,
    Json,
}

#[allow(clippy::large_enum_variant)]
#[derive(Subcommand, Debug)]
#[command(rename_all = "camelCase", version=crate_version!())]
pub enum CliCommands {
    AddDevice {
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
    GetUserStatus {
        recipient: Vec<String>,
    },
    JoinGroup {
        #[arg(long)]
        uri: String,
    },
    Link {
        #[arg(short = 'n', long)]
        name: String,
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
    },
    RemoveContact {
        recipient: String,
        #[arg(long)]
        forget: bool,
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

        #[arg(long)]
        note_to_self: bool,

        #[arg(short = 'e', long)]
        end_session: bool,

        #[arg(short = 'm', long)]
        message: Option<String>,

        #[arg(short = 'a', long)]
        attachment: Vec<String>,

        #[arg(long)]
        mention: Vec<String>,

        #[arg(long)]
        quote_timestamp: Option<u64>,

        #[arg(long)]
        quote_author: Option<String>,

        #[arg(long)]
        quote_message: Option<String>,

        #[arg(long)]
        quote_mention: Vec<String>,

        #[arg(long)]
        sticker: Option<String>,

        #[arg(long)]
        story_timestamp: Option<u64>,

        #[arg(long)]
        story_author: Option<String>,
    },
    SendContacts,
    SendPaymentNotification {
        recipient: String,

        #[arg(long)]
        receipt: String,

        #[arg(long)]
        note: String,
    },
    SendReaction {
        recipient: Vec<String>,

        #[arg(short = 'g', long = "group-id")]
        group_id: Vec<String>,

        #[arg(long = "note-to-self")]
        note_to_self: bool,

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
    SetPin {
        pin: String,
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

        #[arg(long = "mobile-coin-address")]
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

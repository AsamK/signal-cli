use clap::{crate_version, ArgEnum, Parser, Subcommand};
use std::{ffi::OsString, net::SocketAddr};

/// JSON-RPC client for signal-cli
#[derive(Parser, Debug)]
#[clap(rename_all = "kebab-case", version=crate_version!())]
pub struct Cli {
    /// Account to use (for daemon in multi-account mode)
    #[clap(short = 'a', long)]
    pub account: Option<String>,

    /// TCP host and port of signal-cli daemon
    #[clap(long)]
    pub json_rpc_tcp: Option<Option<SocketAddr>>,

    /// UNIX socket address and port of signal-cli daemon
    #[clap(long)]
    pub json_rpc_socket: Option<Option<OsString>>,

    #[clap(arg_enum, long, default_value_t = OutputTypes::Json)]
    pub output: OutputTypes,

    #[clap(long)]
    pub verbose: bool,

    #[clap(subcommand)]
    pub command: CliCommands,
}

#[derive(ArgEnum, Clone, Debug)]
#[clap(rename_all = "kebab-case")]
pub enum OutputTypes {
    PlainText,
    Json,
}

#[allow(clippy::large_enum_variant)]
#[derive(Subcommand, Debug)]
#[clap(rename_all = "camelCase", version=crate_version!())]
pub enum CliCommands {
    AddDevice {
        #[clap(long)]
        uri: String,
    },
    #[clap(rename_all = "kebab-case")]
    Block {
        recipient: Vec<String>,

        #[clap(short = 'g', long)]
        group_id: Vec<String>,
    },
    GetUserStatus {
        recipient: Vec<String>,
    },
    JoinGroup {
        #[clap(long)]
        uri: String,
    },
    Link {
        #[clap(short = 'n', long)]
        name: String,
    },
    ListAccounts,
    ListContacts,
    ListDevices,
    ListGroups {
        #[clap(short = 'd', long)]
        detailed: bool,
    },
    ListIdentities {
        #[clap(short = 'n', long)]
        number: Option<String>,
    },
    ListStickerPacks,
    QuitGroup {
        #[clap(short = 'g', long = "group-id")]
        group_id: String,
        #[clap(long)]
        delete: bool,
        #[clap(long)]
        admin: Vec<String>,
    },
    Receive {
        #[clap(short = 't', long, default_value_t = 3.0)]
        timeout: f64,
    },
    Register {
        #[clap(short = 'v', long)]
        voice: bool,
        #[clap(long)]
        captcha: Option<String>,
    },
    RemoveContact {
        recipient: String,
        #[clap(long)]
        forget: bool,
    },
    RemoveDevice {
        #[clap(short = 'd', long = "device-id")]
        device_id: u32,
    },
    RemovePin,
    RemoteDelete {
        #[clap(short = 't', long = "target-timestamp")]
        target_timestamp: u64,

        recipient: Vec<String>,

        #[clap(short = 'g', long = "group-id")]
        group_id: Vec<String>,

        #[clap(long = "note-to-self")]
        note_to_self: bool,
    },
    #[clap(rename_all = "kebab-case")]
    Send {
        recipient: Vec<String>,

        #[clap(short = 'g', long)]
        group_id: Vec<String>,

        #[clap(long)]
        note_to_self: bool,

        #[clap(short = 'e', long)]
        end_session: bool,

        #[clap(short = 'm', long)]
        message: Option<String>,

        #[clap(short = 'a', long)]
        attachment: Vec<String>,

        #[clap(long)]
        mention: Vec<String>,

        #[clap(long)]
        quote_timestamp: Option<u64>,

        #[clap(long)]
        quote_author: Option<String>,

        #[clap(long)]
        quote_message: Option<String>,

        #[clap(long)]
        quote_mention: Vec<String>,

        #[clap(long)]
        sticker: Option<String>,
    },
    SendContacts,
    SendReaction {
        recipient: Vec<String>,

        #[clap(short = 'g', long = "group-id")]
        group_id: Vec<String>,

        #[clap(long = "note-to-self")]
        note_to_self: bool,

        #[clap(short = 'e', long)]
        emoji: String,

        #[clap(short = 'a', long = "target-author")]
        target_author: String,

        #[clap(short = 't', long = "target-timestamp")]
        target_timestamp: u64,

        #[clap(short = 'r', long)]
        remove: bool,
    },
    SendReceipt {
        recipient: String,

        #[clap(short = 't', long = "target-timestamp")]
        target_timestamp: Vec<u64>,

        #[clap(arg_enum, long)]
        r#type: ReceiptType,
    },
    SendSyncRequest,
    SendTyping {
        recipient: Vec<String>,

        #[clap(short = 'g', long = "group-id")]
        group_id: Vec<String>,

        #[clap(short = 's', long)]
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

        #[clap(short = 'a', long = "trust-all-known-keys")]
        trust_all_known_keys: bool,

        #[clap(short = 'v', long = "verified-safety-number")]
        verified_safety_number: Option<String>,
    },
    #[clap(rename_all = "kebab-case")]
    Unblock {
        recipient: Vec<String>,

        #[clap(short = 'g', long)]
        group_id: Vec<String>,
    },
    Unregister {
        #[clap(long = "delete-account")]
        delete_account: bool,
    },
    UpdateAccount {
        #[clap(short = 'n', long = "device-name")]
        device_name: Option<String>,
    },
    UpdateConfiguration {
        #[clap(long = "read-receipts", parse(try_from_str))]
        read_receipts: Option<bool>,

        #[clap(long = "unidentified-delivery-indicators")]
        unidentified_delivery_indicators: Option<bool>,

        #[clap(long = "typing-indicators")]
        typing_indicators: Option<bool>,

        #[clap(long = "link-previews")]
        link_previews: Option<bool>,
    },
    UpdateContact {
        recipient: String,

        #[clap(short = 'e', long)]
        expiration: Option<u32>,

        #[clap(short = 'n', long)]
        name: Option<String>,
    },
    UpdateGroup {
        #[clap(short = 'g', long = "group-id")]
        group_id: Option<String>,

        #[clap(short = 'n', long)]
        name: Option<String>,

        #[clap(short = 'd', long)]
        description: Option<String>,

        #[clap(short = 'a', long)]
        avatar: Option<String>,

        #[clap(short = 'm', long)]
        member: Vec<String>,

        #[clap(short = 'r', long = "remove-member")]
        remove_member: Vec<String>,

        #[clap(long)]
        admin: Vec<String>,

        #[clap(long = "remove-admin")]
        remove_admin: Vec<String>,

        #[clap(long = "reset-link")]
        reset_link: bool,

        #[clap(arg_enum, long)]
        link: Option<LinkState>,

        #[clap(arg_enum, long = "set-permission-add-member")]
        set_permission_add_member: Option<GroupPermission>,

        #[clap(arg_enum, long = "set-permission-edit-details")]
        set_permission_edit_details: Option<GroupPermission>,

        #[clap(arg_enum, long = "set-permission-send-messages")]
        set_permission_send_messages: Option<GroupPermission>,

        #[clap(short = 'e', long)]
        expiration: Option<u32>,
    },
    UpdateProfile {
        #[clap(long = "given-name")]
        given_name: Option<String>,

        #[clap(long = "family-name")]
        family_name: Option<String>,

        #[clap(long)]
        about: Option<String>,

        #[clap(long = "about-emoji")]
        about_emoji: Option<String>,

        #[clap(long)]
        avatar: Option<String>,

        #[clap(long = "remove-avatar")]
        remove_avatar: bool,
    },
    UploadStickerPack {
        path: String,
    },
    Verify {
        verification_code: String,

        #[clap(short = 'p', long)]
        pin: Option<String>,
    },
    Version,
}

#[derive(ArgEnum, Clone, Debug)]
#[clap(rename_all = "kebab-case")]
pub enum ReceiptType {
    Read,
    Viewed,
}

#[derive(ArgEnum, Clone, Debug)]
#[clap(rename_all = "kebab-case")]
pub enum LinkState {
    Enabled,
    EnabledWithApproval,
    Disabled,
}

#[derive(ArgEnum, Clone, Debug)]
#[clap(rename_all = "kebab-case")]
pub enum GroupPermission {
    EveryMember,
    OnlyAdmins,
}

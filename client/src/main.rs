use std::{path::PathBuf, time::Duration};

use clap::Parser;
use jsonrpsee::core::client::{Error as RpcError, Subscription, SubscriptionClientT};
use serde_json::{Error, Value};
use tokio::{select, time::sleep};

use cli::Cli;

use crate::cli::{CliCommands, GroupPermission, LinkState};
use crate::jsonrpc::RpcClient;

mod cli;
#[allow(non_snake_case, clippy::too_many_arguments)]
mod jsonrpc;
mod transports;

const DEFAULT_TCP: &str = "127.0.0.1:7583";
const DEFAULT_SOCKET_SUFFIX: &str = "signal-cli/socket";
const DEFAULT_HTTP: &str = "http://localhost:8080/api/v1/rpc";

#[tokio::main]
async fn main() -> Result<(), anyhow::Error> {
    let cli = cli::Cli::parse();

    let result = connect(cli).await;

    match result {
        Ok(Value::Null) => {}
        Ok(v) => println!("{v}"),
        Err(e) => return Err(anyhow::anyhow!("JSON-RPC command failed: {e:?}")),
    }
    Ok(())
}

async fn handle_command(
    cli: Cli,
    client: impl SubscriptionClientT + Sync,
) -> Result<Value, RpcError> {
    match cli.command {
        CliCommands::Receive { timeout } => {
            let mut stream = client.subscribe_receive(cli.account).await?;

            {
                while let Some(v) = stream_next(timeout, &mut stream).await {
                    let v = v?;
                    println!("{v}");
                }
            }
            stream.unsubscribe().await?;
            Ok(Value::Null)
        }
        CliCommands::AddDevice { uri } => client.add_device(cli.account, uri).await,
        CliCommands::Block {
            recipient,
            group_id,
        } => client.block(cli.account, recipient, group_id).await,
        CliCommands::DeleteLocalAccountData { ignore_registered } => {
            client
                .delete_local_account_data(cli.account, ignore_registered)
                .await
        }
        CliCommands::GetUserStatus { recipient } => {
            client.get_user_status(cli.account, recipient).await
        }
        CliCommands::JoinGroup { uri } => client.join_group(cli.account, uri).await,
        CliCommands::Link { name } => {
            let url = client
                .start_link(cli.account)
                .await
                .map_err(|e| RpcError::Custom(format!("JSON-RPC command startLink failed: {e:?}")))?
                .device_link_uri;
            println!("{}", url);
            client.finish_link(url, name).await
        }
        CliCommands::ListAccounts => client.list_accounts().await,
        CliCommands::ListContacts {
            recipient,
            all_recipients,
            blocked,
            name,
        } => {
            client
                .list_contacts(cli.account, recipient, all_recipients, blocked, name)
                .await
        }
        CliCommands::ListDevices => client.list_devices(cli.account).await,
        CliCommands::ListGroups {
            detailed: _,
            group_id,
        } => client.list_groups(cli.account, group_id).await,
        CliCommands::ListIdentities { number } => client.list_identities(cli.account, number).await,
        CliCommands::ListStickerPacks => client.list_sticker_packs(cli.account).await,
        CliCommands::QuitGroup {
            group_id,
            delete,
            admin,
        } => {
            client
                .quit_group(cli.account, group_id, delete, admin)
                .await
        }
        CliCommands::Register { voice, captcha } => {
            client.register(cli.account, voice, captcha).await
        }
        CliCommands::RemoveContact {
            recipient,
            forget,
            hide,
        } => {
            client
                .remove_contact(cli.account, recipient, forget, hide)
                .await
        }
        CliCommands::RemoveDevice { device_id } => {
            client.remove_device(cli.account, device_id).await
        }
        CliCommands::RemovePin => client.remove_pin(cli.account).await,
        CliCommands::RemoteDelete {
            target_timestamp,
            recipient,
            group_id,
            note_to_self,
        } => {
            client
                .remote_delete(
                    cli.account,
                    target_timestamp,
                    recipient,
                    group_id,
                    note_to_self,
                )
                .await
        }
        CliCommands::Send {
            recipient,
            group_id,
            note_to_self,
            end_session,
            message,
            attachment,
            mention,
            text_style,
            quote_timestamp,
            quote_author,
            quote_message,
            quote_mention,
            quote_text_style,
            quote_attachment,
            preview_url,
            preview_title,
            preview_description,
            preview_image,
            sticker,
            story_timestamp,
            story_author,
            edit_timestamp,
        } => {
            client
                .send(
                    cli.account,
                    recipient,
                    group_id,
                    note_to_self,
                    end_session,
                    message.unwrap_or_default(),
                    attachment,
                    mention,
                    text_style,
                    quote_timestamp,
                    quote_author,
                    quote_message,
                    quote_mention,
                    quote_text_style,
                    quote_attachment,
                    preview_url,
                    preview_title,
                    preview_description,
                    preview_image,
                    sticker,
                    story_timestamp,
                    story_author,
                    edit_timestamp,
                )
                .await
        }
        CliCommands::SendContacts => client.send_contacts(cli.account).await,
        CliCommands::SendPaymentNotification {
            recipient,
            receipt,
            note,
        } => {
            client
                .send_payment_notification(cli.account, recipient, receipt, note)
                .await
        }
        CliCommands::SendReaction {
            recipient,
            group_id,
            note_to_self,
            emoji,
            target_author,
            target_timestamp,
            remove,
            story,
        } => {
            client
                .send_reaction(
                    cli.account,
                    recipient,
                    group_id,
                    note_to_self,
                    emoji,
                    target_author,
                    target_timestamp,
                    remove,
                    story,
                )
                .await
        }
        CliCommands::SendReceipt {
            recipient,
            target_timestamp,
            r#type,
        } => {
            client
                .send_receipt(
                    cli.account,
                    recipient,
                    target_timestamp,
                    match r#type {
                        cli::ReceiptType::Read => "read".to_owned(),
                        cli::ReceiptType::Viewed => "viewed".to_owned(),
                    },
                )
                .await
        }
        CliCommands::SendSyncRequest => client.send_sync_request(cli.account).await,
        CliCommands::SendTyping {
            recipient,
            group_id,
            stop,
        } => {
            client
                .send_typing(cli.account, recipient, group_id, stop)
                .await
        }
        CliCommands::SetPin { pin } => client.set_pin(cli.account, pin).await,
        CliCommands::SubmitRateLimitChallenge { challenge, captcha } => {
            client
                .submit_rate_limit_challenge(cli.account, challenge, captcha)
                .await
        }
        CliCommands::Trust {
            recipient,
            trust_all_known_keys,
            verified_safety_number,
        } => {
            client
                .trust(
                    cli.account,
                    recipient,
                    trust_all_known_keys,
                    verified_safety_number,
                )
                .await
        }
        CliCommands::Unblock {
            recipient,
            group_id,
        } => client.unblock(cli.account, recipient, group_id).await,
        CliCommands::Unregister { delete_account } => {
            client.unregister(cli.account, delete_account).await
        }
        CliCommands::UpdateAccount {
            device_name,
            unrestricted_unidentified_sender,
            discoverable_by_number,
            number_sharing,
        } => {
            client
                .update_account(
                    cli.account,
                    device_name,
                    unrestricted_unidentified_sender,
                    discoverable_by_number,
                    number_sharing,
                )
                .await
        }
        CliCommands::UpdateConfiguration {
            read_receipts,
            unidentified_delivery_indicators,
            typing_indicators,
            link_previews,
        } => {
            client
                .update_configuration(
                    cli.account,
                    read_receipts,
                    unidentified_delivery_indicators,
                    typing_indicators,
                    link_previews,
                )
                .await
        }
        CliCommands::UpdateContact {
            recipient,
            expiration,
            name,
        } => {
            client
                .update_contact(cli.account, recipient, name, expiration)
                .await
        }
        CliCommands::UpdateGroup {
            group_id,
            name,
            description,
            avatar,
            member,
            remove_member,
            admin,
            remove_admin,
            ban,
            unban,
            reset_link,
            link,
            set_permission_add_member,
            set_permission_edit_details,
            set_permission_send_messages,
            expiration,
        } => {
            client
                .update_group(
                    cli.account,
                    group_id,
                    name,
                    description,
                    avatar,
                    member,
                    remove_member,
                    admin,
                    remove_admin,
                    ban,
                    unban,
                    reset_link,
                    link.map(|link| match link {
                        LinkState::Enabled => "enabled".to_owned(),
                        LinkState::EnabledWithApproval => "enabledWithApproval".to_owned(),
                        LinkState::Disabled => "disabled".to_owned(),
                    }),
                    set_permission_add_member.map(|p| match p {
                        GroupPermission::EveryMember => "everyMember".to_owned(),
                        GroupPermission::OnlyAdmins => "onlyAdmins".to_owned(),
                    }),
                    set_permission_edit_details.map(|p| match p {
                        GroupPermission::EveryMember => "everyMember".to_owned(),
                        GroupPermission::OnlyAdmins => "onlyAdmins".to_owned(),
                    }),
                    set_permission_send_messages.map(|p| match p {
                        GroupPermission::EveryMember => "everyMember".to_owned(),
                        GroupPermission::OnlyAdmins => "onlyAdmins".to_owned(),
                    }),
                    expiration,
                )
                .await
        }
        CliCommands::UpdateProfile {
            given_name,
            family_name,
            about,
            about_emoji,
            mobile_coin_address,
            avatar,
            remove_avatar,
        } => {
            client
                .update_profile(
                    cli.account,
                    given_name,
                    family_name,
                    about,
                    about_emoji,
                    mobile_coin_address,
                    avatar,
                    remove_avatar,
                )
                .await
        }
        CliCommands::UploadStickerPack { path } => {
            client.upload_sticker_pack(cli.account, path).await
        }
        CliCommands::Verify {
            verification_code,
            pin,
        } => client.verify(cli.account, verification_code, pin).await,
        CliCommands::Version => client.version().await,
        CliCommands::AddStickerPack { uri } => client.add_sticker_pack(cli.account, uri).await,
        CliCommands::FinishChangeNumber {
            number,
            verification_code,
            pin,
        } => {
            client
                .finish_change_number(cli.account, number, verification_code, pin)
                .await
        }
        CliCommands::GetAttachment {
            id,
            recipient,
            group_id,
        } => {
            client
                .get_attachment(cli.account, id, recipient, group_id)
                .await
        }
        CliCommands::GetAvatar {
            contact,
            profile,
            group_id,
        } => {
            client
                .get_avatar(cli.account, contact, profile, group_id)
                .await
        }
        CliCommands::GetSticker {
            pack_id,
            sticker_id,
        } => client.get_sticker(cli.account, pack_id, sticker_id).await,
        CliCommands::StartChangeNumber {
            number,
            voice,
            captcha,
        } => {
            client
                .start_change_number(cli.account, number, voice, captcha)
                .await
        }
        CliCommands::SendMessageRequestResponse {
            recipient,
            group_id,
            r#type,
        } => {
            client
                .send_message_request_response(
                    cli.account,
                    recipient,
                    group_id,
                    match r#type {
                        cli::MessageRequestResponseType::Accept => "accept".to_owned(),
                        cli::MessageRequestResponseType::Delete => "delete".to_owned(),
                    },
                )
                .await
        }
    }
}

async fn connect(cli: Cli) -> Result<Value, RpcError> {
    if let Some(http) = &cli.json_rpc_http {
        let uri = if let Some(uri) = http {
            uri
        } else {
            DEFAULT_HTTP
        };
        let client = jsonrpc::connect_http(uri)
            .await
            .map_err(|e| RpcError::Custom(format!("Failed to connect to socket: {e}")))?;

        handle_command(cli, client).await
    } else if let Some(tcp) = cli.json_rpc_tcp {
        let socket_addr = tcp.unwrap_or_else(|| DEFAULT_TCP.parse().unwrap());
        let client = jsonrpc::connect_tcp(socket_addr)
            .await
            .map_err(|e| RpcError::Custom(format!("Failed to connect to socket: {e}")))?;

        handle_command(cli, client).await
    } else {
        let socket_path = cli
            .json_rpc_socket
            .clone()
            .unwrap_or(None)
            .or_else(|| {
                std::env::var_os("XDG_RUNTIME_DIR").map(|runtime_dir| {
                    PathBuf::from(runtime_dir)
                        .join(DEFAULT_SOCKET_SUFFIX)
                        .into()
                })
            })
            .unwrap_or_else(|| ("/run".to_owned() + DEFAULT_SOCKET_SUFFIX).into());
        let client = jsonrpc::connect_unix(socket_path)
            .await
            .map_err(|e| RpcError::Custom(format!("Failed to connect to socket: {e}")))?;

        handle_command(cli, client).await
    }
}

async fn stream_next(
    timeout: f64,
    stream: &mut Subscription<Value>,
) -> Option<Result<Value, Error>> {
    if timeout < 0.0 {
        stream.next().await
    } else {
        select! {
            v = stream.next() => v,
            _= sleep(Duration::from_millis((timeout * 1000.0) as u64)) => None,
        }
    }
}

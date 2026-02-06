# Changelog

## [Unreleased]

**Attention**: Now requires Java 25

### Breaking changes

- Remove isRegistered method without parameters from Signal dbus interface, which always returned `true`
- Remove `sandbox` value for --service-environment parameter, use `staging` instead
- The `daemon` command now requires at least one channel parameter (`--socket`, `--dbus`, ...) and no longer defaults to dbus

## [0.13.24] - 2026-02-05

Requires libsignal-client version 0.87.0.

### Improved

- Improve performance of first send to large group
- Improve envelope validation

## [0.13.23] - 2026-01-24

Requires libsignal-client version 0.86.12.

### Added

- Add sendPollCreate, sendPollVote, sendPollTerminate commands for polls
- Add updateDevice command to set device name of linked devices
- Add --ignore-avatars flag to prevent downloading avatars
- Add --ignore-stickers flag to prevent downloading sticker packs

### Changed

- Allow updating contact names from linked devices

### Fixed

- Start multi account mode even if some accounts have authorization failures

## [0.13.22] - 2025-11-14

Requires libsignal-client version 0.86.1.

### Fixed

- Fix timeout handling for receive command
- Fix device link URI parsing for unencoded trailing =
- Adapt setPin command to server changes

## [0.13.21] - 2025-10-25

Requires libsignal-client version 0.84.0.

### Changed

- Add isExpirationUpdate to json message output
- Improve error message when using verify without registering before

## [0.13.20] - 2025-09-23

Requires libsignal-client version 0.81.0.

### Fixed

- Fix sending group message to legacy targets without group send endorsements
- Fix registration commands in daemon mode for already registered accounts (Thanks @AntonKun)

### Improved

- Faster shutdown performance when using multiple accounts

## [0.13.19] - 2025-09-15

Requires libsignal-client version 0.80.2.

### Fixed

- Fixed hiding contacts (with `removeContact --hide`)
- Prevent splitting UTF-8 chars when reading message from stdin
- Handle unregistered username correctly when sending message

### Changed

- Update to signal service changes, mainly new group endorsements for group sending
- Reduced frequency of updating last received timstamp on disk
- Handle missing storage manifest version correctly
- Force a group refresh when using listGroups command with groupId

## [0.13.18] - 2025-07-16

Requires libsignal-client version 0.76.3.

### Added

- Added `--view-once` parameter to send command to send view once images

### Fixed

- Handle rate limit exception correctly when querying usernames

### Improved

- Shut down when dbus daemon connection goes away unexpectedly
- In daemon mode, exit immediately if account check fails at startup
- Improve behavior when sending to devices that have no available prekeys

## [0.13.17] - 2025-06-28

Requires libsignal-client version 0.76.0.

### Fixed

- Fix issue when loading an older inactive group
- Close attachment input streams after upload
- Fix storage sync behavior with unhandled fields

### Changed

- Improve behavior when pin data doesn't exist on the server

## [0.13.16] - 2025-06-07

Requires libsignal-client version 0.73.2.

### Changed

- Ensure every sent message gets a unique timestamp

## [0.13.15] - 2025-05-08

Requires libsignal-client version 0.70.0.

### Fixed

- Fix native access warning with Java 24
- Fix storage sync loop due to old removed e164 field

### Changed

- Increased compatibility of native build with older/virtual CPUs

## [0.13.14] - 2025-04-06

Requires libsignal-client version 0.68.1.

### Fixed

- Fix pre key import from old data files

### Changed

- Use websocket connection instead of HTTP for more requests
- Improve handling of messages with decryption error

## [0.13.13] - 2025-02-28

Requires libsignal-client version 0.66.2.

### Added
- Allow setting nickname and note with `updateContact` command

### Fixed
- Fix syncing nickname, note and expiration timer
- Fix check for registered users with a proxy
- Improve handling of storage records not yet supported by signal-cli
- Fix contact sync for networks requiring proxy

## [0.13.12] - 2025-01-18

Requires libsignal-client version 0.65.2.

### Fixed

- Fix sync of contact nick name
- Fix incorrectly marking recipients as unregistered after sync
- Fix cause of database deadlock (Thanks @dukhaSlayer)
- Fix parsing of account query param in events http endpoint

### Changed

- Enable sqlite WAL journal\_mode for improved performance

## [0.13.11] - 2024-12-26

Requires libsignal-client version 0.64.0.

### Fixed
- Fix issue with receiving messages that have an invalid destination

## [0.13.10] - 2024-11-30

Requires libsignal-client version 0.62.0.

### Fixed

- Fix receiving some unusual contact sync messages
- Fix receiving expiration timer updates

### Improved
- Add support for new storage encryption scheme

## [0.13.9] - 2024-10-28

### Fixed

- Fix verify command

## [0.13.8] - 2024-10-26

Requires libsignal-client version 0.58.2

### Fixed

- Fix sending large text messages
- Fix setting message expiration timer with recent Signal apps

### Improved

- Add group name and timestamps on json message (Thanks @jailson-dias)

## [0.13.7] - 2024-09-28

Requires libsignal-client version 0.58.0

### Fixed

- Fix unnecessary log output
- Fix issue with CDSI sync with invalid token

## [0.13.6] - 2024-09-08

Requires libsignal-client version 0.56.0

### Improved

- Send sync message to linked devices when sending read/viewed receipts

### Fixed

- Fix issue with sending to some groups
- Fix CDSI sync if no token is stored
- Fix possible db dead lock during storage sync

## [0.13.5] - 2024-07-25

Requires libsignal-client version 0.52.2

### Fixed

- Fixed device linking, due to new feature flag

## [0.13.4] - 2024-06-06

**Attention**: Now requires libsignal-client version 0.47.0

### Improved

- Improve username update error message
- Update groups when using listGroups command

### Fixed

- Update libsignal to fix graalvm native startup
- Fix issue with saving username link
- Fix sendMessageRequestResponse type parameter parsing in JSON RPC mode
- Fix getUserStatus command with only username parameter

## [0.13.3] - 2024-04-19

**Attention**: Now requires libsignal-client version 0.44.0

### Added

- Support for reading contact nickname and notes
- Add `--internal` and `--detailed` parameters to `listContacts` command

### Fixed

- Fix issue with sending messages when a new session is created

## [0.13.2] - 2024-03-23

**Attention**: Now requires libsignal-client version 0.40.1

### Added

- Add `--username` parameter to `getUserStatus` command

### Fixed

- Fixed setting and retrieving PIN after server changes

## [0.13.1] - 2024-02-27

### Added

- Add `--reregister` parameter to force registration of an already registered account

### Fixed

- Fixed rare issue with duplicate PNIs during migration

### Improved

- Show information when requesting voice verification without prior SMS verification
- Username can now be set with an explicit discriminator (e.g. testname.000)
- Improve behavior when PNI prekeys upload fails
- Improve `submitRateLimitChallenge` error message if captcha is rejected by server
- Only retry messages after an identity was trusted

### Changed

- Default number sharing to NOBODY, to match the official apps behavior.

## [0.13.0] - 2024-02-18

**Attention**: Now requires Java 21 and libsignal-client version 0.39.2

### Breaking changes

- Sending to the self number (+XXXX) now behaves the same as the `--note-to-self` parameter. To get the previous
  behavior with notification, the `--notify-self` parameter can be added.

### Added

- New `--hidden` parameter for `removeContact` command
- New `--notify-self` parameter for `send` command, for sending a non-sync message when self is part of the recipients
  or groups.
- New `--unrestricted-unidentified-sender`, `--discoverable-by-number`, `--number-sharing`, `--username`
  and `--delete-username` parameter for `updateAccount` command
- New `--bus-name` parameter for `daemon` command to use another D-Bus bus name
- New `getAvatar` and `getSticker` commands to get avatar and sticker images
- New `sendMessageRequestResponse` command to accept/delete message requests

### Fixed

- Improve issue with stale prekeys and receiving messages to PNI address

### Improved

- Better shutdown handling after Ctrl+C and SIGTERM
- Implemented full remote storage sync.
  Provides better contact and settings sync for linked devices.
- `listContacts` doesn't list unregistered users anymore

## [0.12.8] - 2024-02-06

### Fixes

- Update user agent

## [0.12.7] - 2023-12-15

**Attention**: Now requires native libsignal-client version 0.36.1

### Fixes

- Fix linking to an existing account

## [0.12.6] - 2023-12-11

### Fixes

- Fix linking to an existing account
- Fix migration from old account data

## [0.12.5] - 2023-11-21

### Fixes

- Fix issue with joining groups by group link for new accounts
- Fix receiving address of shared contact
- Fix receiving sync edit messages in groups

### Changed

- Create safety numbers based on ACI instead of phone number

## [0.12.4] - 2023-10-22

### Fixes

- Prevent ConcurrentModificationException
- Update captcha help text

## [0.12.3] - 2023-10-17

### Added

- Added `startChangeNumber` and `finishChangeNumber` commands to switch to another phone number
- Added `--quote-attachment` parameter to `send` command
- Added support for scannable safety numbers based on serviceId
- Added `EditMessageReceived` signal for D-Bus interface
- Added new exit code `5` for rate limit failures
- Added full CDSI refresh to get current ACI/PNIs for known numbers regularly

### Fixed

- Correctly respond with delivery receipts for edit messages

### Changed

- JSON-RPC requests are now executed in parallel.
  Clients should make sure to use the `id` field to get the correct response for a request.

## [0.12.2] - 2023-09-30

**Attention**: Now requires native libsignal-client version 0.32.1

### Added

- Added `--receive-mode` parameter for `jsonRpc` command
- Add `libsignal_client_path` build property to override libsignal-client jar file

### Changed

- `jsonRpc` command now supports multi-account mode including registering and linking

## [0.12.1] - 2023-08-26

### Added

- New `addStickerPack` command

### Fixed

- Fixed some issues with upgrading from older accounts

### Changed

- Reverted receive notification in JSON-RPC to old format, only explicit subscriptions should use the new format

## [0.12.0] - 2023-08-11

**Attention**: Now requires native libsignal-client version 0.30.0

### Breaking changes

- Adapt receive subscription notification in JSON-RPC to have payload in result field
    - Before: `{"jsonrpc":"2.0","method":"receive","params":{"envelope":{ ... },"account":"+XXX","subscription":0}}`
    - After:
      `{"jsonrpc":"2.0","method":"receive","params":{"subscription":0,"result":{"envelope":{ ... },"account":"+XXX"}}}`

### Added

- Manage identities via DBus (Thanks @bublath)
- Added support for SVR2 PINs

### Fixed

- Fixed finishLink/receive/register/verify commands for JSON-RPC
- Update to the latest libsignal to fix various issues

## [0.11.11] - 2023-05-24

**Attention**: Now requires native libsignal-client version 0.25.0

### Added

- New `--text-style` and `--quote-text-style` flags for `send` command

### Fixed

- Fixed migration of older account files
- Fix deleting old unregistered recipient

## [0.11.10] - 2023-05-11

**Attention**: Now requires native libsignal-client version 0.23.1

### Added

- Support for receiving and sending edit messages with `--edit-timestamp`

## [0.11.9.1] - 2023-04-23

### Fixed

- Fix build with Java 20

## [0.11.9] - 2023-04-22

### Fixed

- Workaround issue with linking to newer app versions

## [0.11.8] - 2023-04-05

### Added

- Added file attachment attributes to JSON output (Thanks @signals-from-outer-space)

### Fixed

- Scrub E164 number from dbus paths
- Fix sending large text messages to multiple recipient
- Fix deleting old group in dbus mode
- Fix issue with unknown identity serviceId

### Improved

- Relaxed Content-Type check in http daemon mode (Thanks @cedb)

## [0.11.7] - 2023-02-19

**Attention**: Now requires native libsignal-client version 0.22.0

### Fixed

- Fix issue with missing pni identity key
- Fix graalvm sqlite issue (Thanks @Marvin A. Ruder)
- Fix issue with forgetting recipient

### Changed

- Allow JSON-RPC commands without account param if only one account exists

## [0.11.6] - 2022-12-18

### Added

- Allow using data URIs for updateGroup/updateProfile
- New alive check endpoint for http daemon (Thanks @ced-b)

### Fixed

- Registration with voice verification now works if no system locale is set
- Fixed retrieving attachments in JSON RPC mode (Thanks @ced-b)

## [0.11.5.1] - 2022-11-09

### Fixed

- Fix updating from older signal-cli version

## [0.11.5] - 2022-11-07

**Attention**: Now requires native libsignal-client version 0.21.1

### Added

- Add `--http` flag to `daemon` command to provide a JSON-RPC http endpoint (`/api/v1/rpc`). (Thanks @ced-b)
- The `receive` method is now also available in JSON-RPC daemon mode, for polling new messages.
- Add `getAttachment` command to get attachment file base64 encoded. (Thanks @ced-b)
- Add `--disable-send-log` to disable the message send log.
- Add `--story` to `sendReaction` command, to react to stories.
- Add `--story-timestamp` and `--story-author` to `send` command, to reply to stories.
- Add `--max-messages` to `receive` command, to only receive a certain number of messages.

### Changed

- Send long text messages as attachment instead. This matches the behavior of the official clients.
- Store attachments with a file extension, for common file types.

## [0.11.4] - 2022-10-19

### Added

- Approve/Refuse group join requests, using same interface as adding/removing members
- Add --ignore-stories flag to prevent receiving story messages

### Fixed

- Fixed issue with receiving messages that can't be decrypted
- Do not discard incoming group join messages

### Improved

- Add code to receive new PNI after change number

## [0.11.3] - 2022-10-07

### Fixed

- Fix sending messages to groups (in non-daemon mode)
- Fix updating from older signal-cli version
- Fix issue with handling decryption error message
- Fix graalvm native build (Thanks @bentolor)

## [0.11.2] - 2022-10-06

### Fixed

- Update user agent version to work with new Signal-Server check

## [0.11.1] - 2022-10-05

### Fixed

- Fix sending group messages
- Fix store migration issue on Windows
- Fix building fat jars

## [0.11.0] - 2022-10-02

**Attention**: Now requires native libsignal-client version 0.20.0

### Breaking changes

- Changed meaning of `-v` flag from `--version` to `--verbose`.
  So now extended logging can be achieved with `-vv`.
- Remove deprecated fallback to reading from stdin if no message body is given.
  To read a message from stdin, use the `--message-from-stdin` flag.

### Added

- Migrate PIN to new KBS enclave when Signal updates it
- Add `--scrub-log` flag to remove possibly sensitive information from the log
- Add `sendPaymentNotification` dbus method

### Fixed

- Fix an issue where messages were sent without sender phone number

### Changed

- Store data except base account data in sqlite database
- Use new CDSI for contact discovery in compat mode

## [0.10.11] - 2022-08-17

**Attention**: Now requires native libsignal-client version 0.19.3

### Added

- Output content of received story messages

## [0.10.10] - 2022-07-30

**Attention**: Now requires native libsignal-client version 0.18.1

### Fixed

- Fix setPin/removePin commands which broke due to server side changes
- Workaround GraalVM 22.2.0 issue with daemon connection

## [0.10.9] - 2022-07-16

### Changed

- updateAccount command checks self number and PNI after updating account attributes

### Fixed

- Fixed small issue with syncing contacts from storage
- Fixed issue with opening older account files

## [0.10.8] - 2022-06-13

### Added

- Attachments can now be given as data: URIs with base64 data instead of just file paths (Thanks @KevinRoebert)
- `version` command can now be used on the commandline, in addition to the `--version` flag.
  In the next version the current short form `-v` will change its meaning to `--verbose`!

### Improved

- An account can now be registered on both LIVE and STAGING environment in the same config directory.
- Logging output for registering has been extended.

## [0.10.7] - 2022-05-29

### Added

- Added profile information to `listContacts` command output
- Added filter flags for `listContacts` command
- New `sendPaymentNotification` command to send payment receipt blobs
- New `--given-name` and `--family-name` parameters for `updateContact` command
- Implement sending link previews with `--preview-url`, `--preview-title` parameters for the `send` command
- New `--send-read-receipts` parameter for `receive` and `daemon` commands for automatically marking received messages
  as read

### Fixed

- Issue with endless growing `pre-keys-pni` data directory

## [0.10.6] - 2022-05-19

**Attention**: Now requires native libsignal-client version 0.17

### Added

- Check if account is used on the environment it was registered (live or staging)
- New command `deleteLocalAccountData` to delete all local data of an unregistered account
- New parameter `-g` for `listGroups` command to filter for specific groups

### Fixed

- Fix deleting a recipient which has no uuid

### Changed

- Show warning when sending a message and no profile name has been set.
  (A profile name may become mandatory in the future)
- After blocking a contact/group the profile key is now rotated
- Only update profile keys from authoritative group changes

## [0.10.5] - 2022-04-11

**Attention**: Now requires native libsignal-client version 0.15

### Added

- New `--ban`, `--unban` flags for `updateGroup` command to ban users from joining by group link

### Fixed

- Fix plain text output of blocked group ids
- Fix error output in case of rate limiting
- Fix error when creating a group with no members
- Fix adding recent Signal-Desktop versions as linked devices

## [0.10.4.2] - 2022-03-17

### Fixed

- Crash in json output when receiving message from untrusted identity
- Fix multi account commands for newly created accounts

## [0.10.4.1] - 2022-03-02

### Fixed

- Linking to current apps (which currently don't include a PNI identity yet)
- Show better error message when --target-timestamp is missing for sendReceipt

## [0.10.4] - 2022-02-20

### Added

- Implement support for change number as linked device
- Add `--message-from-stdin` flag for send command. The current behavior of
  reading from stdin if the `-m` flag is not given, will be removed in a future
  version.

### Changed

- Align receive timeout behavior for dbus client with cli and JSON-RPC.
  Timeout is reset by every incoming message
- Renamed `error` field in json receive response to `exception`

### Fixed

- Prevent a stale jsonrpc connection from interfering with message receiving

## [0.10.3] - 2022-02-01

### Added

- MessageSendLog to cache sent message for 24h.
  For resending messages in case the recipient fails to decrypt the message.
- New global `--log-file` parameter to write logs to a separate file.
  (`--verbose` can be used to increase the log level)

### Improved

- Better subscription handling for JSON-RPC `subscribeReceive` command

### Fixed

- Output receipt data for unsealed sender receipts again
- Fix sending message resend requests to devices that happen to have the same deviceId

## [0.10.2] - 2022-01-22

### Fixed

- Archive old sessions/sender keys when a recipient's identity key has changed
- Fix profile fetch with an invalid LANG variable

## [0.10.1] - 2022-01-16

### Added

- Send group messages with sender keys (more efficient for larger groups)
- New command `listStickerPacks` to display all known sticker packs
- New flag `--sticker` for `send` command to send stickers

### Changed

- Improve exit code for message sending.
  Exit with 0 status code if the message was sent successfully to at least
  one recipient, otherwise exit with status code 2 or 4 (for untrusted).
- Download profiles in parallel for improved performance
- `--verbose` flag can be specified multiple times for additional log output
- Enable more security options for systemd service file
- Rename sandbox to staging environment, to match the upstream name.

### Fixed

- The first incoming message after registration can now always be decrypted successfully
- Ignore decryption failures from blocked contacts and don't send a resend request.

## [0.10.0] - 2021-12-11

**Attention**: Now requires Java 17 and libsignal-client version 0.11

### Added

- The daemon command now provides a JSON-RPC based socket interface (`--socket` and `--tcp`)
- New daemon command flag `--receive-mode` to configure when messages are received
- New daemon command flag `--no-receive-stdout` to prevent outputting messages on stdout
- New command `listAccounts` that lists all registered local accounts
- New command `removeContact`
- Extend `send` command to allow sending mentions (`--mention`) and
  quotes (`--quote-timestamp`, `--quote-author`, `--quote-message`, `--quote-mention`)
- New dbus methods sendGroupTying, unregister, deleteAccount
- New dbus events MessageReceivedV2, ReceiptReceivedV2, SyncMessageReceivedV2 that provide an extras parameter with
  additional message info as a key/value map
- New dbus method sendViewedReceipt (Thanks @John Freed)
- New dbus object Configuration to read and update configuration values (Thanks @John Freed)
- Payment info in json receive output (Thanks @technillogue)
- `-c` alias for `--config` (Thanks @technillogue)

### Changed

- libzkgroup dependency is no longer required
- Renamed `-u` and `--username` flags to `-a` and `--account` to prevent confusion with upcoming Signal usernames. The
  old flags are also still supported for now.
- Respect phone number sharing mode and unlisted state set by primary device
- Adapt register command to reactivate account if possible.
- dbus-java now uses Java 16 native unix sockets, which should provide better cross-platform compatibility
- If sending to a recipient fails (e.g. unregistered) signal-cli now exits with a success exit code and prints
  additional information about the failure.

### Fixed

- Registering an existing unregistered account now works reliably in daemon mode
- Fixed an issue with loading some old config files without UUID
- More reliable send behavior if some recipients are unregistered

## [0.9.2] - 2021-10-24

### Fixed

- dbus `listNumbers` method works again

### Changed

- Improved provisioning error handling if the last steps fail
- Adapt behavior of receive command as dbus client to match normal mode
- Update captcha url for proof required handling

## [0.9.1] - 2021-10-16

**Attention**: Now requires native libzkgroup version 0.8

### Added

- New command `updateConfiguration` which allows setting configurations for linked devices
- Improved dbus daemon for group handling, groups are now exported as separate dbus objects
- Linked devices can be managed via dbus
- New dbus methods sendTyping and sendReadReceipt (Thanks @JtheSaw)
- New dbus methods submitRateLimitChallenge, isRegistered, listDevices, setExpirationTimer, sendContacts,
  sendSyncRequest, uploadStickerPack, setPin and removePin (Thanks @John Freed)
- New dbus method getSelfNumber

### Fixed

- Do not send message resend request to own device
- Allow message from pending member to accept group invitations
- Fix issue which could cause signal-cli to repeatedly send the same delivery receipts
- Reconnect websocket after connection loss

### Changed

- Use new provisioning URL `sgnl://linkdevice` instead of `tsdevice:/`
- The gradle command to build a graalvm native image is now `./gradlew nativeCompile`

## [0.9.0] - 2021-09-12

**Attention**: Now requires native libsignal-client version 0.9

### Breaking changes

- Removed deprecated `--json` parameter, use global parameter `--output=json` instead
- Json output format of `listGroups` command changed:
  Members are now arrays of `{"number":"...","uuid":"..."}` objects instead of arrays of strings.
- Removed deprecated fallback data paths, only `$XDG_DATA_HOME/signal-cli` is used now
  For those still using the old paths (`$HOME/.config/signal`, `$HOME/.config/textsecure`) you need to move those to the
  new location.

### Added

- New global parameter `--trust-new-identities=always` to allow trusting any new identity key without verification
- New parameter `--device-name` for `updateAccount` command to change the device name (also works for the primary
  device)
- New SignalControl DBus interface, to register/verify/link new accounts
- New `jsonRpc` command that provides a JSON-RPC based API on stdout/stdin
- Support for announcement groups
- New parameter `--set-permission-send-messages` for `updateGroup` to create an announcement group
- New `sendReceipt` command to send read and viewed receipts
- Support for receiving sender key messages, mobile apps can now send messages more efficiently with server-side fan-out
  to groups with signal-cli members.
- Support for reading data from remote Signal storage. Now v2 groups will be shown after linking a new device.
- New `submitRateLimitChallenge` command that can be used to lift some rate-limits by solving a captcha

### Fixed

- Store identity key correctly when sending a message after a recipient has changed keys

## [0.8.5] - 2021-08-07

### Added

- Source name is included in JSON receive output (Thanks @technillogue)

### Fixed

- Allow updateContact command to only set expiration timer without requiring a name parameter

## [0.8.4.1] - 2021-06-20

### Fixed

- Incorrect error handling in register command

## [0.8.4] - 2021-06-13

**Attention**: Now requires native libsignal-client version 0.8.1

### Added

- New parameters for `updateGroup` command for group v2 features:
  `--description`, `--remove-member`, `--admin`, `--remove-admin`, `--reset-link`, `--link`,
  `--set-permission-add-member`, `--set-permission-edit-details`, `--expiration`
- New `--admin` parameter for `quitGroup` to set an admin before leaving the group
- New `--delete` parameter for `quitGroup`, to delete the local group data
- New 'sendTyping' command to send typing indicators

### Fixed

- Fixed issue that prevented registration with invalid locales
- Prevent last admin of a group from leaving the group
- All commands now show a short description with `--help`
- Now a hint is shown if messages aren't received regularly
- Group edit conflicts are now resolved automatically

## [0.8.3] - 2021-05-13

### Fixed

- Upgrading from account files with older profiles
- Building native image with graalvm

## [0.8.2] - 2021-05-11

### Added

- A manual page for the DBus interface (Thanks @bublath, @exquo)
- Remote message delete command (Thanks @adaptivegarage)
- sendSyncRequest command to request complete contact/group list from primary device
- New `--delete-account` argument for unregister (Dangerous)
- New `--family-name` argument for updateProfile

### Fixed

- Sending reaction to group (Thanks @adaptivegarage)
- Displaying of address for messages from untrusted identities
- Handling of recipient number or uuid changes (e.g. after account deletions)
- Only respond to sync requests from primary device
- Display of quit group messages

### Changed

- Unlimited strength crypto is now enabled automatically for JREs that require it (Thanks @i-infra)
- Only one identity key is stored per recipient and updated from profile (to match app behavior)
- updateContact, block and unblock are now disabled for linked devices
- After registering an empty profile is created so new groups can be joined immediately
- If message decryption fails due to a broken session, the session is automatically renewed
- Rework account storage for better reliability
- Improved device linking flow
    - Allow relinking existing account
    - Encrypt/Decrypt device names

## [0.8.1] - 2021-03-02

### Added

- New dbus commands: updateProfile, listNumbers, getContactNumber, quitGroup, isContactBlocked, isGroupBlocked,
  isMember, joinGroup (Thanks @bublath)
- Additional output for json format: shared contacts (Thanks @Atomic-Bean)
- Improved plain text output to be more consistent and synced messages are now indented

### Fixed

- Issue with broken sessions with linked devices

### Changed

- Behavior of `trust` command improved, when trusting a new identity key all other known keys for
  the same number are removed.

## [0.8.0] - 2021-02-14

**Attention**: For all signal protocol functionality an additional native library is now
required: [libsignal-client](https://github.com/signalapp/libsignal-client/).
See https://github.com/AsamK/signal-cli/wiki/Provide-native-lib-for-libsignal for more information.

### Added

- Experimental support for building a GraalVM native image
- Support for setting profile about text and emoji

### Fixed

- Incorrect error message when removing a non-existent profile avatar

## [0.7.4] - 2021-01-19

### Changed

- Notify linked devices after profile has been updated

### Fixed

- After registering a new account, receiving messages didn't work
  You may have to register and verify again to fix the issue.
- Creating v1 groups works again

## [0.7.3] - 2021-01-17

### Added

- `getUserStatus` command to check if a user is registered on Signal (Thanks @Atomic-Bean)
- Global `--verbose` flag to increase log level
- Global `--output=json` flag, currently supported by `receive`, `daemon`, `getUserStatus`, `listGroups`
- `--note-to-self` flag for `send` command to send a note to linked devices
- More info for received messages in json output: stickers, viewOnce, typing, remoteDelete

### Changed

- signal-cli can now be used without the username `-u` flag
  For daemon command all local users will be exposed as dbus objects.
  If only one local user exists, all other commands will use that user,
  otherwise a user has to be specified.
- Messages sent to self number will be sent as normal Signal messages again, to
  send a sync message, use the new `--note-to-self` flag
- Ignore messages with group context sent by non group member
- Profile key is sent along with all direct messages
- In json output unnecessary fields that are null are now omitted

### Fixed

- Disable registration lock before removing the PIN
- Fix PIN hash version to match the official clients.
  If you had previously set a PIN you need to set it again to be able to unlock the registration lock later.
- Issue with saving account file after linking

## [0.7.2] - 2020-12-31

### Added

- Implement new registration lock PIN with `setPin` and `removePin` (with KBS)
- Include quotes, mentions and reactions in json output (Thanks @Atomic-Bean)

### Fixed

- Retrieve avatars for v2 groups
- Download attachment thumbnail for quoted attachments

## [0.7.1] - 2020-12-21

### Added

- Accept group invitation with `updateGroup -g GROUP_ID`
- Decline group invitation with `quitGroup -g GROUP_ID`
- Join group via invitation link `joinGroup --uri https://signal.group/#...`

### Fixed

- Include group ids for v2 groups in json output

## [0.7.0] - 2020-12-15

### Added

Support for groups of new type/v2

- Sending and receiving
- Updating name, avatar and adding members with `updateGroup`
- Quit group and decline invitation with `quitGroup`
- In the `listGroups` output v2 groups can be recognized by the longer groupId

**Attention**: For the new group support to work the native libzkgroup library is required.
See https://github.com/AsamK/signal-cli/wiki/Provide-native-lib-for-libsignal for more information.

### Fixed

- Rare NullPointerException when receiving messages

## [0.6.12] - 2020-11-22

### Added

- Show additional message content (view once, remote delete, mention, …) for received messages
- `--captcha` parameter for `register` command, required for some IP ranges

### Changed

- Profile keys are now stored separately from contact list
- Receipts from normal and unidentified messages now have the same format in json output

### Fixed

- Issue where some messages were sent with an old counter index

## [0.6.11] - 2020-10-14

- Fix issue with receiving message reactions

## [0.6.10] - 2020-09-11

- Fix issue when retrieving profiles
- Workaround issue with libzkgroup on platforms other than linux x86_64

## [0.6.9] - 2020-09-10

- Minor bug fixes and improvements
- dbus functionality now works on FreeBSD
- signal-cli now requires Java 11

**Warning: this version only works on Linux x86_64, will be fixed in 0.6.10**

## [0.6.8] - 2020-05-22

- Switch to hypfvieh dbus-java, which doesn't require a native library anymore (drops requirement of
  libmatthew-unix-java)
- Bugfixes for messages with uuids
- Add `--expiration` parameter to `updateContact` command to set expiration timer

## [0.6.7] - 2020-04-03

- Send command now returns the timestamp of the sent message
- DBus daemon: Publish received sync message to SyncMessageReceived signal
- Fix issue with resolving e164/uuid addresses for sessions
- Fix pack key length for sticker upload

## [0.6.6] - 2020-03-29

- Added listContacts command
- Added block/unblock commands to block contacts and groups
- Added uploadStickerPack command to upload sticker packs (see man page for more details)
- Full support for sending and receiving unidentified sender messages
- Support for message reactions with emojis
- Internal: support recipients with uuids

## [0.6.5] - 2019-11-11

Supports receiving messages sent with unidentified sender

## [0.6.4] - 2019-11-02

- Fix rounding error for attachment ids in json output
- Add additional info to json output
- Add commands to update profile name and avatar
- Add command to update contact names

## [0.6.3] - 2019-09-05

Bug fixes and small improvements

## [0.6.2] - 2018-12-16

- Fixes sending of group messages

## [0.6.1] - 2018-12-09

- Added getGroupIds dbus command
- Use "NativePRNG" pseudo random number generator, if available
- Switch default data path:
  `$XDG_DATA_HOME/signal-cli` (`$HOME/.local/share/signal-cli`)
  Existing data paths will continue to work (used as fallback)

## [0.6.0] - 2018-05-03

- Simple json output
- dbus signal for receiving messages
- Registration lock PIN
- Output quoted message

## [0.5.6] - 2017-06-16

* new listGroups command
* Support for attachments with file names
* Support for complete contacts sync
* Support for contact verification sync
* DBus interface:
* Get/Set group info
* Get/Set contact info

## [0.5.5] - 2017-02-18

- fix receiving messages on linked devices
- add unregister command

## [0.5.4] - 2017-02-17

- Fix linking of new devices

## [0.5.3] - 2017-01-29

* New commandline parameter for receive: --ignore-attachments
* Updated dependencies

## [0.5.2] - 2016-12-16

- Add support for group info requests
- Improve closing of file streams

## [0.5.1] - 2016-11-18

- Support new safety numbers (https://whispersystems.org/blog/safety-number-updates/)
- Add a man page
- Support sending disappearing messages, if the recipient has activated it

## [0.5.0] - 2016-08-29

- Check if a number is registered on Signal, before adding it to a group
- Prevent sending to groups that the user has quit
- Commands to trust new identity keys (see README)
- Messages from untrusted identities are stored on disk and decrypted when the user trusts the identity
- Timestamps shown in ISO 8601 format

## [0.4.1] - 2016-07-18

- Fix issue with creating groups
- Lock config file to prevent parallel access by multiple instances of signal-cli
- Improve return codes, always return non-zero code, when sending failed

## [0.4.0] - 2016-06-19

- Linking to Signal-Desktop and Signal-Android is now possible (Provisioning)
- Added a contact store, mainly for syncing contacts with linked devices (editing not yet possible via cli)
- Avatars for groups and contacts are now stored (new folder "avatars" in the config path)

## [0.3.1] - 2016-04-03

- Fix running with Oracle JRE 8
- Fix registering
- Fix unicode warning when compiling with non utf8 locale

## [0.3.0] - 2016-04-02

- Renamed textsecure-cli to signal-cli, following the rename of libtextsecure-java to libsignal-service-java
- The experimental dbus interface was also renamed to org.asamk.Signal
- Upload new prekeys to the server, when there are less than 20 left, prekeys are needed to create new sessions

## [0.2.1] - 2016-02-10

- Improve dbus service
- New command line argument --config to specify config directory

## [0.2.0] - 2015-12-30

Added an experimental dbus interface, for sending and receiving messages (The interface is unstable and may change with
future releases).

This release works with Java 7 and 8.

## [0.1.0] - 2015-11-28

Add support for creating/updating groups and sending to them

## [0.0.5] - 2015-11-21

- Add receive timeout commandline parameter
- Show message group info

## [0.0.4] - 2015-09-22

## [0.0.3] - 2015-08-07

## [0.0.2] - 2015-07-08

First release

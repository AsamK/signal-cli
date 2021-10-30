# Changelog

## [Unreleased]
**Attention**: Now requires Java 17

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
- New dbus methods submitRateLimitChallenge, isRegistered, listDevices, setExpirationTimer, sendContacts, sendSyncRequest, uploadStickerPack, setPin and removePin (Thanks @John Freed)
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
  For those still using the old paths (`$HOME/.config/signal`, `$HOME/.config/textsecure`) you need to move those to the new location.

### Added
- New global parameter `--trust-new-identities=always` to allow trusting any new identity key without verification
- New parameter `--device-name` for `updateAccount` command to change the device name (also works for the main device)
- New SignalControl DBus interface, to register/verify/link new accounts
- New `jsonRpc` command that provides a JSON-RPC based API on stdout/stdin
- Support for announcement groups
- New parameter `--set-permission-send-messages` for `updateGroup` to create an announcement group
- New `sendReceipt` command to send read and viewed receipts
- Support for receiving sender key messages, mobile apps can now send messages more efficiently with server-side fan-out to groups with signal-cli members.
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
  `--description`, `--remove-member`, `--admin`, `--remove-admin`, `--reset-link`, `--link`, `--set-permission-add-member`, `--set-permission-edit-details`, `--expiration`
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
- sendSyncRequest command to request complete contact/group list from master device
- New `--delete-account` argument for unregister (Dangerous)
- New `--family-name` argument for updateProfile

### Fixed
- Sending reaction to group (Thanks @adaptivegarage)
- Displaying of address for messages from untrusted identities
- Handling of recipient number or uuid changes (e.g. after account deletions)
- Only respond to sync requests from master device
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
- New dbus commands: updateProfile, listNumbers, getContactNumber, quitGroup, isContactBlocked, isGroupBlocked, isMember, joinGroup (Thanks @bublath)
- Additional output for json format: shared contacts (Thanks @Atomic-Bean)
- Improved plain text output to be more consistent and synced messages are now indented

### Fixed
- Issue with broken sessions with linked devices

### Changed
- Behavior of `trust` command improved, when trusting a new identity key all other known keys for
  the same number are removed.

## [0.8.0] - 2021-02-14
**Attention**: For all signal protocol functionality an additional native library is now required: [libsignal-client](https://github.com/signalapp/libsignal-client/).
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
- Switch to hypfvieh dbus-java, which doesn't require a native library anymore (drops requirement of libmatthew-unix-java)
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
* New commandline paramter for receive: --ignore-attachments
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
Added an experimental dbus interface, for sending and receiving messages (The interface is unstable and may change with future releases).

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

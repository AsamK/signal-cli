# Changelog

## [Unreleased]
**Attention**: Now requires native libsignal-client version 0.5.1

### Added
- Added new parameters to `updateGroup` for group v2 features:
  `--remove-member`, `--admin`, `--remove-admin`, `--reset-link`, `--link`, `--set-permission-add-member`, `--set-permission-edit-details`, `--expiration`

### Fixed
- Prevent last admin of a group from leaving the group

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

## Older

Look at the [release tags](https://github.com/AsamK/signal-cli/releases) for information about older releases.

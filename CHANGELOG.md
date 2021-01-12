# Changelog

## [Unreleased]

### Fixed
- Disable registration lock before removing the PIN
- Fix PIN hash version to match the official clients.
  If you had previously set a PIN you need to set it again to be able to unlock the registration lock later.

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

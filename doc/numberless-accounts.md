# Linking an account without a phone number

signal-cli can link as a secondary device to an account registered without a phone
number, such as those created in Signal Android 8.28. Creating or recovering a
primary numberless account using an Account Key is not supported.

Run `signal-cli link -n signal-cli`, then scan the QR code from the mobile app's
Linked devices screen. The linking URI advertises `capabilities=nopni`. If linking
completes, the CLI prints the account's ACI instead of a phone number.

Use that ACI with `-a` for subsequent commands:

```sh
signal-cli -a YOUR_ACCOUNT_ACI receive
signal-cli -a YOUR_ACCOUNT_ACI listGroups
signal-cli -a YOUR_ACCOUNT_ACI send --note-to-self -m 'Numberless account test'
```

When only one local account exists, `-a` can be omitted. Numbered accounts continue
to accept their phone number or ACI. A numberless account cannot infer a country
code for a recipient's phone number; use a complete international number beginning
with `+`, an ACI, or a Signal username.

In JSON-RPC, use the ACI in the `account` parameter. `listAccounts` and `finishLink`
return an `aci` field alongside `number`; `number` is null for a numberless account.
Receive events use the ACI in their `account` field when there is no phone number.
REST wrappers must understand those nullable numbers and ACI selectors before they
can be assumed compatible.

Numberless accounts are supported through the CLI and JSON-RPC interfaces. The
D-Bus interface still assumes phone-number-based account identifiers and does not
support numberless accounts.

## Validation

Run `./gradlew test installDist` with JDK 25. Regression tests cover the linking
request (including ACI authentication and omission of PNI keys), numbered-account
linking, missing group credential salts, persisted account reload, account discovery,
group membership lookup, recipient-number formatting, and JSON account identifiers.

Live acceptance requires a real mobile account and is separate from these tests:

1. Link a newly created secondary device using the numberless mobile account.
2. Restart signal-cli and verify that `listAccounts` still reports the ACI.
3. Send and receive a Note to Self message and a DM with a second account.
4. Receive a group message, list the group, and send a reply to that test group.
5. Run the JSON-RPC daemon, restart it, and repeat receiving and sending by ACI.

Only one signal-cli process may open an account's state at a time. Stop the daemon
before running one-off commands against the same configuration directory.

# signal-cli

signal-cli is a commandline interface for [libsignal-service-java](https://github.com/WhisperSystems/libsignal-service-java). It supports registering, verifying, sending and receiving messages. To be able to receive messages signal-cli uses a [patched libsignal-service-java](https://github.com/AsamK/libsignal-service-java), because libsignal-service-java [does not yet support registering for the websocket support](https://github.com/WhisperSystems/libsignal-service-java/pull/5) nor [provisioning as a slave device](https://github.com/WhisperSystems/libsignal-service-java/pull/21). For registering you need a phone number where you can receive SMS or incoming calls.
It is primarily intended to be used on servers to notify admins of important events. For this use-case, it has a dbus interface, that can be used to send messages from any programming language that has dbus bindings.

## Installation

You can [build signal-cli](#building) yourself, or use the [provided binary files](https://github.com/AsamK/signal-cli/releases/latest), which should work on Linux, macOS and Windows. For Arch Linux there is also a [package in AUR](https://aur.archlinux.org/packages/signal-cli/). You need to have at least JRE 7 installed, to run signal-cli.

### Install system-wide on Linux
See [latest version](https://github.com/AsamK/signal-cli/releases).
```sh
export VERSION=<latest version, format "x.y.z">
wget https://github.com/AsamK/signal-cli/releases/download/v"${VERSION}"/signal-cli-"${VERSION}".tar.gz
sudo tar xf signal-cli-"${VERSION}".tar.gz -C /opt
sudo ln -sf /opt/signal-cli-"${VERSION}"/bin/signal-cli /usr/local/bin/
```

## Usage

usage: signal-cli [-h] [-v] [--config CONFIG] [-u USERNAME | --dbus | --dbus-system] {link,addDevice,listDevices,removeDevice,register,verify,send,quitGroup,updateGroup,listIdentities,trust,receive,daemon} ...

* Register a number (with SMS verification)

        signal-cli -u USERNAME register

* Register a number (with voice verification)

        signal-cli -u USERNAME register -v

* Verify the number using the code received via SMS or voice

        signal-cli -u USERNAME verify CODE

* Send a message to one or more recipients

        signal-cli -u USERNAME send -m "This is a message" [RECIPIENT [RECIPIENT ...]] [-a [ATTACHMENT [ATTACHMENT ...]]]

* Pipe the message content from another process.

        uname -a | signal-cli -u USERNAME send [RECIPIENT [RECIPIENT ...]]
        
* Receive messages

        signal-cli -u USERNAME receive

* Groups

 * Create a group

          signal-cli -u USERNAME updateGroup -n "Group name" -m [MEMBER [MEMBER ...]]

 * Update a group

          signal-cli -u USERNAME updateGroup -g GROUP_ID -n "New group name" -a "AVATAR_IMAGE_FILE"

 * Add member to a group

          signal-cli -u USERNAME updateGroup -g GROUP_ID -m "NEW_MEMBER"

 * Leave a group

          signal-cli -u USERNAME quitGroup -g GROUP_ID

 * Send a message to a group

          signal-cli -u USERNAME send -m "This is a message" -g GROUP_ID

* Linking other devices (Provisioning)

 * Connect to another device

          signal-cli link -n "optional device name"
        
        This shows a "tsdevice:/…" link, if you want to connect to another signal-cli instance, you can just use this link. If you want to link to and Android device, create a QR code with the link (e.g. with [qrencode](https://fukuchi.org/works/qrencode/)) and scan that in the Signal Android app.

 * Add another device

          signal-cli -u USERNAME addDevice --uri "tsdevice:/…"
          
        The "tsdevice:/…" link is the one shown by the new signal-cli instance or contained in the QR code shown in Signal-Desktop or similar apps.
        Only the master device (that was registered directly, not linked) can add new devices.

 * Manage linked devices

          signal-cli -u USERNAME listDevices

          signal-cli -u USERNAME removeDevice -d DEVICE_ID

* Manage trusted keys

 * View all known keys

          signal-cli -u USERNAME listIdentities

 * View known keys of one number

          signal-cli -u USERNAME listIdentities -n NUMBER

 * Trust new key, after having verified it

          signal-cli -u USERNAME trust -v FINGER_PRINT NUMBER

 * Trust new key, without having verified it. Only use this if you don't care about security

          signal-cli -u USERNAME trust -a NUMBER

* Set configuration directory

          signal-cli --config=/home/other_user/.config/signal

        This is particularily useful in the case, when you would like to run the signal-cli tool as a different user as the one, that was used to register the account. You should make sure, that the caller has full read/write access to the given directory.
        
## DBus service

signal-cli can run in daemon mode and provides an experimental dbus interface.
For dbus support you need jni/unix-java.so installed on your system (Debian: libunixsocket-java ArchLinux: libmatthew-unix-java (AUR)).

* Run in daemon mode (dbus session bus)

          signal-cli -u USERNAME daemon

* Send a message via dbus

          signal-cli --dbus send -m "Message" [RECIPIENT [RECIPIENT ...]] [-a [ATTACHMENT [ATTACHMENT ...]]]

### System bus

To run on the system bus you need to take some additional steps.
It’s advisable to run signal-cli as a separate unix user, the following steps assume you created a user named *signal-cli*.
These steps, executed as root, should work on all distributions using systemd.

```bash
cp data/org.asamk.Signal.conf /etc/dbus-1/system.d/
cp data/org.asamk.Signal.service /usr/share/dbus-1/system-services/
cp data/signal.service /etc/systemd/system/
sed -i -e "s|%dir%|<INSERT_INSTALL_PATH>|" -e "s|%number%|<INSERT_YOUR_NUMBER>|" /etc/systemd/system/signal.service
systemctl daemon-reload
systemctl enable signal.service
systemctl reload dbus.service
```

Then just execute the send command from above, the service will be autostarted by dbus the first time it is requested.

## Storage

The password and cryptographic keys are created when registering and stored in the current users home directory:

        $HOME/.config/signal/data/

For legacy users, the old config directory is used as a fallback:

        $HOME/.config/textsecure/data/

## Building

This project uses [Gradle](http://gradle.org) for building and maintaining
dependencies. If you have a recent gradle version installed, you can replace `./gradlew` with `gradle` in the following steps.

1. Checkout the source somewhere on your filesystem with

        git clone https://github.com/AsamK/signal-cli.git

2. Execute Gradle:

        ./gradlew build

3. Create shell wrapper in *build/install/signal-cli/bin*:

        ./gradlew installDist

4. Create tar file in *build/distributions*:

        ./gradlew distTar

## Troubleshooting
If you use a version of the Oracle JRE and get an InvalidKeyException you need to enable unlimited strength crypto. See https://stackoverflow.com/questions/6481627/java-security-illegal-key-size-or-default-parameters for instructions.

## License

This project uses libsignal-service-java from Open Whisper Systems:

https://github.com/WhisperSystems/libsignal-service-java

Licensed under the GPLv3: http://www.gnu.org/licenses/gpl-3.0.html

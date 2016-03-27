# signal-cli

signal-cli is a commandline interface for [libtextsecure-java](https://github.com/WhisperSystems/libtextsecure-java). It supports registering, verifying, sending and receiving messages. To be able to receiving messages signal-cli uses a [patched libtextsecure-java](https://github.com/AsamK/libtextsecure-java), because libtextsecure-java [does not yet support registering for the websocket support](https://github.com/WhisperSystems/libtextsecure-java/pull/5). For registering you need a phone number where you can receive SMS or incoming calls.
It is primarily intended to be used on servers to notify admins of important events. For this use-case, it has a dbus interface, that can be used to send messages from any programming language that has dbus bindings.

## Usage

usage: signal-cli [-h] [-u USERNAME] [-v] {register,verify,send,quitGroup,updateGroup,receive} ...

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

* Groups

 * Create a group

          signal-cli -u USERNAME updateGroup -n "Group name" -m [MEMBER [MEMBER ...]]

 * Update a group

          signal-cli -u USERNAME updateGroup -g GROUP_ID -n "New group name"

 * Send a message to a group

          signal-cli -u USERNAME send -m "This is a message" -g GROUP_ID

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

## Building

This project uses [Gradle](http://gradle.org) for building and maintaining
dependencies.

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

This project uses libtextsecure-java from Open Whisper Systems:

https://github.com/WhisperSystems/libtextsecure-java

Licensed under the GPLv3: http://www.gnu.org/licenses/gpl-3.0.html

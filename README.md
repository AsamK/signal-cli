# signal-cli

signal-cli is a commandline interface for [libsignal-service-java](https://github.com/WhisperSystems/libsignal-service-java). It supports registering, verifying, sending and receiving messages.
To be able to link to an existing Signal-Android/signal-cli instance, signal-cli uses a [patched libsignal-service-java](https://github.com/AsamK/libsignal-service-java), because libsignal-service-java does not yet support [provisioning as a slave device](https://github.com/WhisperSystems/libsignal-service-java/pull/21).
For registering you need a phone number where you can receive SMS or incoming calls.
signal-cli is primarily intended to be used on servers to notify admins of important events. For this use-case, it has a dbus interface, that can be used to send messages from any programming language that has dbus bindings.

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

Important: The USERNAME (your phone number) must include the country calling code, i.e. the number must start with a "+" sign. (See [Wikipedia](https://en.wikipedia.org/wiki/List_of_country_calling_codes) for a list of all country codes.

* Register a number (with SMS verification)

        signal-cli -u USERNAME register

* Verify the number using the code received via SMS or voice

        signal-cli -u USERNAME verify CODE

* Send a message

        signal-cli -u USERNAME send -m "This is a message" RECIPIENT

* Pipe the message content from another process.

        uname -a | signal-cli -u USERNAME send RECIPIENT
        
* Receive messages

        signal-cli -u USERNAME receive

For more information read the [man page](https://github.com/AsamK/signal-cli/blob/master/man/signal-cli.1.adoc) and the [wiki](https://github.com/AsamK/signal-cli/wiki).

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

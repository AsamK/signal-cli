# signal-cli

signal-cli is a commandline interface
for [libsignal-service-java](https://github.com/WhisperSystems/libsignal-service-java). It supports registering,
verifying, sending and receiving messages. To be able to link to an existing Signal-Android/signal-cli instance,
signal-cli uses a [patched libsignal-service-java](https://github.com/AsamK/libsignal-service-java), because
libsignal-service-java does not yet
support [provisioning as a linked device](https://github.com/WhisperSystems/libsignal-service-java/pull/21). For
registering you need a phone number where you can receive SMS or incoming calls.

signal-cli is primarily intended to be used on servers to notify admins of important events. For this use-case, it has a daemon mode with D-BUS
interface ([man page](https://github.com/AsamK/signal-cli/blob/master/man/signal-cli-dbus.5.adoc)) and JSON-RPC interface ([documentation](https://github.com/AsamK/signal-cli/wiki/JSON-RPC-service)). For the JSON-RPC interface there's also a simple [example client](https://github.com/AsamK/signal-cli/tree/master/client), written in Rust.

## Installation

You can [build signal-cli](#building) yourself or use
the [provided binary files](https://github.com/AsamK/signal-cli/releases/latest), which should work on Linux, macOS and
Windows. There's also a [docker image and some Linux packages](https://github.com/AsamK/signal-cli/wiki/Binary-distributions) provided by the community.

System requirements:

- at least Java Runtime Environment (JRE) 17
- native library: libsignal-client

  The native libs are bundled for x86_64 Linux (with recent enough glibc), Windows and MacOS. For other
  systems/architectures
  see: [Provide native lib for libsignal](https://github.com/AsamK/signal-cli/wiki/Provide-native-lib-for-libsignal)

### Install system-wide on Linux

See [latest version](https://github.com/AsamK/signal-cli/releases).

```sh
export VERSION=<latest version, format "x.y.z">
wget https://github.com/AsamK/signal-cli/releases/download/v"${VERSION}"/signal-cli-"${VERSION}"-Linux.tar.gz
sudo tar xf signal-cli-"${VERSION}"-Linux.tar.gz -C /opt
sudo ln -sf /opt/signal-cli-"${VERSION}"/bin/signal-cli /usr/local/bin/
```

You can find further instructions on the Wiki:

- [Quickstart](https://github.com/AsamK/signal-cli/wiki/Quickstart)
- [DBus Service](https://github.com/AsamK/signal-cli/wiki/DBus-service)

## Usage

For a complete usage overview please read
the [man page](https://github.com/AsamK/signal-cli/blob/master/man/signal-cli.1.adoc) and
the [wiki](https://github.com/AsamK/signal-cli/wiki).

Important: The ACCOUNT is your phone number in international format and must include the country calling code. Hence it
should start with a "+" sign. (See [Wikipedia](https://en.wikipedia.org/wiki/List_of_country_calling_codes) for a list
of all country codes.)

* Register a number (with SMS verification)

        signal-cli -a ACCOUNT register

  You can register Signal using a landline number. In this case you can skip SMS verification process and jump directly
  to the voice call verification by adding the `--voice` switch at the end of above register command.

  Registering may require solving a CAPTCHA
  challenge: [Registration with captcha](https://github.com/AsamK/signal-cli/wiki/Registration-with-captcha)

* Verify the number using the code received via SMS or voice, optionally add `--pin PIN_CODE` if you've added a pin code
  to your account

        signal-cli -a ACCOUNT verify CODE

* Send a message

        signal-cli -a ACCOUNT send -m "This is a message" RECIPIENT

* Pipe the message content from another process.

        uname -a | signal-cli -a ACCOUNT send --message-from-stdin RECIPIENT

* Receive messages

        signal-cli -a ACCOUNT receive

**Hint**: The Signal protocol expects that incoming messages are regularly received (using `daemon` or `receive`
command). This is required for the encryption to work efficiently and for getting updates to groups, expiration timer
and other features.

## Storage

The password and cryptographic keys are created when registering and stored in the current users home directory:

        $XDG_DATA_HOME/signal-cli/data/
        $HOME/.local/share/signal-cli/data/

## Building

This project uses [Gradle](http://gradle.org) for building and maintaining dependencies. If you have a recent gradle
version installed, you can replace `./gradlew` with `gradle` in the following steps.

1. Checkout the source somewhere on your filesystem with

        git clone https://github.com/AsamK/signal-cli.git

2. Execute Gradle:

        ./gradlew build

   2a. Create shell wrapper in *build/install/signal-cli/bin*:

        ./gradlew installDist

   2b. Create tar file in *build/distributions*:

        ./gradlew distTar

   2c. Create a fat tar file in *build/libs/signal-cli-fat*:

        ./gradlew fatJar

   2d. Compile and run signal-cli:

        ./gradlew run --args="--help"

### Building a native binary with GraalVM (EXPERIMENTAL)

It is possible to build a native binary with [GraalVM](https://www.graalvm.org). This is still experimental and will not
work in all situations.

1. [Install GraalVM and setup the enviroment](https://www.graalvm.org/docs/getting-started/#install-graalvm)
2. [Install prerequisites](https://www.graalvm.org/reference-manual/native-image/#prerequisites)
3. Execute Gradle:

        ./gradlew nativeCompile

   The binary is available at *build/native/nativeCompile/signal-cli*

## FAQ and Troubleshooting

For frequently asked questions and issues have a look at the [wiki](https://github.com/AsamK/signal-cli/wiki/FAQ)

## License

This project uses libsignal-service-java from Open Whisper Systems:

https://github.com/WhisperSystems/libsignal-service-java

Licensed under the GPLv3: http://www.gnu.org/licenses/gpl-3.0.html

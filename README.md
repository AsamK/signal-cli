# textsecure-cli

textsecure-cli is a commandline interface for [libtextsecure-java](https://github.com/WhisperSystems/libtextsecure-java). It supports registering, verifying, sending and receiving messages. However receiving messages currently doesn't work, because libtextsecure-java [does not yet support registering for the websocket support](https://github.com/WhisperSystems/libtextsecure-java/pull/5). For registering you need a phone number where you can receive SMS.
It is primarily intented to be used on servers to notify admins of important events.

## Usage

usage: textsecure-cli [-h] -u USERNAME {register,verify,send,receive} ...

* Register a number

        textsecure-cli -u USERNAME register

* Verify the number using the code received via SMS

        textsecure-cli -u USERNAME verify CODE

* Send a message to one or more recipients

        textsecure-cli -u USERNAME send -m "This is a message" [RECIPIENT [RECIPIENT ...]]

* Pipe the message content from another process.

        uname -a | textsecure-cli -u USERNAME send [RECIPIENT [RECIPIENT ...]]

## Storage

The password and cryptographic keys are created when registering and stored in the current users home directory.

        $HOME/.config/textsecure/data/

## Building

This project uses [Gradle](http://gradle.org) for building and maintaining
dependencies.

1. Checkout the source somewhere on your filesystem wit

        git clone https://github.com/AsamK/textsecure-cli.git

2. Execute Gradle:

        ./gradlew build

3. Create shell wrapper in *build/install/textsecure-cli/bin*:

        ./gradlew installApp

4. Create tar file in *build/distributions*:

        ./gradlew distTar

## License

This project uses libtextsecure-java from Open Whisper Systems:

https://github.com/WhisperSystems/libtextsecure-java

Licensed under the GPLv3: http://www.gnu.org/licenses/gpl-3.0.html

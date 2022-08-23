#!/bin/sh
if [ $# -ne 2 ]; then
  echo "Usage: $0 NUMBER_1 NUMBER_2"
  exit 1
fi

set -e

NUMBER_1="$1"
NUMBER_2="$2"
TEST_PIN_1=456test_pin_foo123
NATIVE=0
JSON_RPC=0

PATH_TEST_CONFIG="$PWD/test-config"
PATH_MAIN="$PATH_TEST_CONFIG/main"
PATH_LINK="$PATH_TEST_CONFIG/link"

if [ "$NATIVE" -eq 1 ]; then
	SIGNAL_CLI="$PWD/build/native/nativeCompile/signal-cli"
elif [ "$JSON_RPC" -eq 1 ]; then
	(cd client && cargo build)
	"$PWD/build/install/signal-cli/bin/signal-cli" --verbose --verbose --trust-new-identities=always --config="$PATH_MAIN" --service-environment="staging" --log-file="$PATH_MAIN/log" daemon --socket --receive-mode=manual&
	"$PWD/build/install/signal-cli/bin/signal-cli" --verbose --verbose --trust-new-identities=always --config="$PATH_LINK" --service-environment="staging" --log-file="$PATH_LINK/log" daemon --tcp --receive-mode=manual&
	sleep 5
	SIGNAL_CLI="$PWD/client/target/debug/signal-cli-client"
else
	./gradlew installDist
	SIGNAL_CLI="$PWD/build/install/signal-cli/bin/signal-cli"
fi

run() {
  # To update graalvm config, set GRAALVM_HOME, e.g:
  # export GRAALVM_HOME=/usr/lib/jvm/java-17-graalvm
  if [ ! -z "$GRAALVM_HOME" ]; then
    export JAVA_HOME=$GRAALVM_HOME
    export SIGNAL_CLI_OPTS="-agentlib:native-image-agent=config-merge-dir=graalvm-config-dir-${SIGNAL_CLI_AGENT_ID}/"
  fi

  set -x
  if [ "$JSON_RPC" -eq 1 ]; then
    "$SIGNAL_CLI" $@
  else
    "$SIGNAL_CLI" --service-environment="staging" --verbose --verbose $@
  fi
  set +x
}

run_main() {
  export SIGNAL_CLI_AGENT_ID=main
  if [ "$JSON_RPC" -eq 1 ]; then
    run --json-rpc-socket="$XDG_RUNTIME_DIR/signal-cli/socket" $@
  else
    run --config="$PATH_MAIN" --log-file="$PATH_MAIN/log" $@
  fi
  unset SIGNAL_CLI_AGENT_ID
}

run_linked() {
  export SIGNAL_CLI_AGENT_ID=linked
  if [ "$JSON_RPC" -eq 1 ]; then
    run --json-rpc-tcp="127.0.0.1:7583" $@
  else
    run --config="$PATH_LINK" --log-file="$PATH_LINK/log" $@
  fi
  unset SIGNAL_CLI_AGENT_ID
}

register() {
  NUMBER=$1
  PIN=$2
  echo -n "Enter a captcha token (https://signalcaptchas.org/staging/registration/generate.html): "
  read CAPTCHA
  run_main -a "$NUMBER" register --captcha "$CAPTCHA"
  echo -n "Enter validation code for ${NUMBER}: "
  read CODE
  if [ -z "$PIN" ]; then
    run_main -a "$NUMBER" verify "$CODE"
  else
    run_main -a "$NUMBER" verify "$CODE" --pin "$PIN"
  fi
}

link() {
  NUMBER=$1
  LINK_CODE_FILE="$PATH_TEST_CONFIG/link_code"
  rm -f "$LINK_CODE_FILE"
  mkfifo "$LINK_CODE_FILE"
  run_linked link -n "test-device" >"$LINK_CODE_FILE" &
  read LINK_CODE <"$LINK_CODE_FILE"
  run_main -a "$NUMBER" addDevice --uri "$LINK_CODE"
  wait
  run_linked -a "$NUMBER" send --note-to-self -m hi
  run_main -a "$NUMBER" receive
  run_linked -a "$NUMBER" receive
  run_main -a "$NUMBER" receive
}

run_main --version
run_main --help

## Register
register "$NUMBER_1" "$TEST_PIN_1"
register "$NUMBER_2"

sleep 5

run_main listAccounts
run_main --output=json listAccounts

if [ "$JSON_RPC" -eq 0 ]; then
## DBus
#run_main -a "$NUMBER_1" --dbus send "$NUMBER_2" -m daemon_not_running || true
#run_main daemon &
#DAEMON_PID=$!
#sleep 10
#run_main -a "$NUMBER_1" --dbus send "$NUMBER_2" -m hii
#run_main -a "$NUMBER_2" --dbus receive
#kill "$DAEMON_PID"


# JSON-RPC
FIFO_FILE="${PATH_MAIN}/dbus-fifo"

rm -f "$FIFO_FILE"
mkfifo "$FIFO_FILE"

run_main -a "$NUMBER_1" send "$NUMBER_2" -m hi
run_main -a "$NUMBER_2" jsonRpc < "$FIFO_FILE" &

exec 3<> "$FIFO_FILE"
  echo '{"jsonrpc":"2.0","id":"id","method":"updateContact","params":{"recipient":"'"$NUMBER_1"'","name":"NUMBER_1","expiration":10}}' >&3
  echo '{"jsonrpc":"2.0","id":5,"method":"block","params":{"recipient":"'"$NUMBER_1"'"}}' >&3
  echo '{"jsonrpc":"2.0","id":null,"method":"unblock","params":{"recipient":"'"$NUMBER_1"'"}}' >&3
  echo '{"jsonrpc":"2.0","id":"id","method":"listContacts"}' >&3
  echo '{"jsonrpc":"2.0","id":"id","method":"listGroups"}' >&3
  echo '{"jsonrpc":"2.0","id":"id","method":"listDevices"}' >&3
  echo '{"jsonrpc":"2.0","id":"id","method":"listIdentities"}' >&3
  echo '{"jsonrpc":"2.0","id":"id","method":"sendSyncRequest"}' >&3
  echo '{"jsonrpc":"2.0","id":"id","method":"sendContacts"}' >&3
  echo '{"jsonrpc":"2.0","id":"id","method":"version"}' >&3
  echo '{"jsonrpc":"2.0","id":"id","method":"updateAccount"}' >&3
  echo '{"jsonrpc":"2.0","id":7,"method":"sendReceipt","params":{"recipient":"'"$NUMBER_1"'","targetTimestamp":1629919505575}}' >&3
  echo '{"jsonrpc":"2.0","id":7,"method":"sendTyping","params":{"recipient":"'"$NUMBER_1"'"}}' >&3
  echo '{"jsonrpc":"2.0","id":7,"method":"send","params":{"recipient":"'"$NUMBER_1"'","message":"some text"}}' >&3
  echo '{"jsonrpc":"2.0","id":7,"method":"send","params":{"recipients":["'"$NUMBER_1"'","'"$NUMBER_2"'"],"message":"some other text"}}' >&3
  echo '{"jsonrpc":"2.0","id":7,"method":"updateProfile","params":{"givenName":"n1","familyName":"n2","about":"ABA","aboutEmoji":"EMO","avatar":"LICENSE"}}' >&3
  echo '{"jsonrpc":"2.0","id":7,"method":"getUserStatus","params":{"recipient":"'"$NUMBER_1"'"}}' >&3

  # Error expected:
  echo '{"jsonrpc":"2.0","id":7,"method":"sendReceipt","params":{"recipient":5}}' >&3
exec 3>&-

wait
fi

run_main -a "$NUMBER_1" setPin "$TEST_PIN_1"
run_main -a "$NUMBER_2" removePin

## Contacts
run_main -a "$NUMBER_2" updateContact "$NUMBER_1" -n NUMBER_1 -e 10
run_main -a "$NUMBER_2" block "$NUMBER_1"
run_main -a "$NUMBER_2" unblock "$NUMBER_1"
run_main -a "$NUMBER_2" listContacts

run_main -a "$NUMBER_1" send "$NUMBER_2" -m hi
run_main -a "$NUMBER_2" receive
run_main -a "$NUMBER_2" send "$NUMBER_1" -m hi
run_main -a "$NUMBER_1" receive
run_main -a "$NUMBER_2" receive
## Groups
GROUP_ID=$(run_main -a "$NUMBER_1" --output=json updateGroup -n GRUPPE -a LICENSE -m "$NUMBER_1" | jq -r '.groupId')
run_main -a "$NUMBER_1" send "$NUMBER_2" -m first
run_main -a "$NUMBER_1" updateGroup -g "$GROUP_ID" -n GRUPPE_UMB -m "$NUMBER_2" --admin "$NUMBER_2" --description DESCRIPTION --link=enabled-with-approval --set-permission-add-member=only-admins --set-permission-edit-details=only-admins --set-permission-send-messages=only-admins -e 42
run_main -a "$NUMBER_1" updateGroup -g "$GROUP_ID" --remove-admin "$NUMBER_2" --reset-link --set-permission-send-messages=every-member
run_main -a "$NUMBER_1" updateGroup -g "$GROUP_ID" -r "$NUMBER_2"
run_main -a "$NUMBER_1" updateGroup -g "$GROUP_ID" -m "$NUMBER_2"
run_main -a "$NUMBER_1" listGroups -d
run_main -a "$NUMBER_1" --output=json listGroups -d
run_main -a "$NUMBER_2" receive
run_main -a "$NUMBER_2" quitGroup -g "$GROUP_ID"
run_main -a "$NUMBER_2" listGroups -d
run_main -a "$NUMBER_2" --output=json listGroups -d
run_main -a "$NUMBER_1" receive
run_main -a "$NUMBER_1" updateGroup -g "$GROUP_ID" -m "$NUMBER_2"
run_main -a "$NUMBER_1" block -g "$GROUP_ID"
run_main -a "$NUMBER_1" unblock -g "$GROUP_ID"

## Configuration
run_main -a "$NUMBER_1" updateConfiguration --read-receipts=true

## Identities
run_main -a "$NUMBER_1" listIdentities
run_main -a "$NUMBER_2" listIdentities
run_main -a "$NUMBER_2" trust "$NUMBER_1" -a

## Basic send/receive
for OUTPUT in "plain-text" "json"; do
  run_main -a "$NUMBER_1" --output="$OUTPUT" getUserStatus "$NUMBER_1" "$NUMBER_2" "+111111111"
  run_main -a "$NUMBER_1" --output="$OUTPUT" send "$NUMBER_2" -m hi
  run_main -a "$NUMBER_2" --output="$OUTPUT" send "$NUMBER_1" -m hi
  run_main -a "$NUMBER_1" --output="$OUTPUT" send -g "$GROUP_ID" -m hi -a LICENSE --mention "1:1:$NUMBER_2"
  TIMESTAMP=$(uname -a | run_main -a "$NUMBER_1" --output=json send --message-from-stdin "$NUMBER_2" | jq '.timestamp')
  run_main -a "$NUMBER_2" --output="$OUTPUT" sendReaction "$NUMBER_1" -e 🍀 -a "$NUMBER_1" -t "$TIMESTAMP"
  run_main -a "$NUMBER_1" --output="$OUTPUT" remoteDelete "$NUMBER_2" -t "$TIMESTAMP"
  run_main -a "$NUMBER_2" --output="$OUTPUT" receive
  run_main -a "$NUMBER_1" --output="$OUTPUT" receive
  run_main -a "$NUMBER_1" --output="$OUTPUT" send -e "$NUMBER_2"
  run_main -a "$NUMBER_2" --output="$OUTPUT" receive
done

## Profile
run_main -a "$NUMBER_1" updateProfile --given-name=GIVEN --family-name=FAMILY --about=ABOUT --about-emoji=EMOJI --avatar=LICENSE

## Provisioning
link "$NUMBER_1"
link "$NUMBER_2"
run_main -a "$NUMBER_1" listDevices
run_linked -a "$NUMBER_1" sendSyncRequest
run_main -a "$NUMBER_1" sendContacts

for OUTPUT in "plain-text" "json"; do
  run_main -a "$NUMBER_1" --output="$OUTPUT" send "$NUMBER_2" -m hi
  run_main -a "$NUMBER_2" --output="$OUTPUT" send "$NUMBER_1" -m hi
  run_main -a "$NUMBER_2" --output="$OUTPUT" receive
  run_main -a "$NUMBER_1" --output="$OUTPUT" receive
  run_linked -a "$NUMBER_1" --output="$OUTPUT" receive
done

run_main -a "$NUMBER_1" removeDevice -d 2

## Unregister
run_main -a "$NUMBER_1" unregister
run_main -a "$NUMBER_2" unregister --delete-account

if [ ! -z "$GRAALVM_HOME" ]; then
  "$GRAALVM_HOME"/lib/svm/bin/native-image-configure generate --input-dir=graalvm-config-dir/ --input-dir=graalvm-config-dir-linked/ --input-dir=graalvm-config-dir-main/ --output-dir=graalvm-config-dir//
  rm -r graalvm-config-dir-main graalvm-config-dir-linked
fi

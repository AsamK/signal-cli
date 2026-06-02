#!/bin/bash

set -eu

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../"
cd "$ROOT_DIR"
rm -rf "$ROOT_DIR/dist"
mkdir -p "$ROOT_DIR/dist"

if command -v podman >/dev/null; then
	ENGINE=podman
	USER=
else
	ENGINE=docker
	USER="--user $(id -u):$(id -g)"
fi

VERSION=$(sed -n 's/\s*version\s*=\s*"\(.*\)".*/\1/p' build.gradle.kts | tail -n1)
echo "$VERSION" >dist/VERSION

# Build jar
$ENGINE build -t signal-cli:build ${OVERRIDE_JAVA_VERSION:+--build-arg ZULU_TAG=$OVERRIDE_JAVA_VERSION} -f reproducible-builds/build.Containerfile .
git clean -Xfd -e '!/dist/' -e '!/dist/**' -e '!/github/' -e '!/github/**'
# shellcheck disable=SC2086
$ENGINE run --pull=never --rm -v "$(pwd)":/signal-cli:Z -e VERSION="$VERSION" $USER signal-cli:build
mv build/distributions/signal-cli-*.tar.gz dist/

if [ -n "${OVERRIDE_JAVA_VERSION:-}" ]; then
	echo -e "\e[33mBuild was performed with overridden Java version $OVERRIDE_JAVA_VERSION, native-image and client will not be built.\e[0m"
	exit 0
fi

# Build native-image
$ENGINE build -t signal-cli:native -f reproducible-builds/native.Containerfile .
git clean -Xfd -e '!/dist/' -e '!/dist/**' -e '!/github/' -e '!/github/**'
# shellcheck disable=SC2086
$ENGINE run --pull=never --rm -v "$(pwd)":/signal-cli:Z -e VERSION="$VERSION" $USER signal-cli:native
mv build/signal-cli-*-Linux-native.tar.gz dist/

# Build rust client
$ENGINE build -t signal-cli:client -f reproducible-builds/client.Containerfile .
git clean -Xfd -e '!/dist/' -e '!/dist/**' -e '!/github/' -e '!/github/**'
# shellcheck disable=SC2086
$ENGINE run --pull=never --rm -v "$(pwd)":/signal-cli:Z -e VERSION="$VERSION" $USER signal-cli:client
mv build/signal-cli-*-Linux-client.tar.gz dist/

ls -lsh dist/

echo -e "\e[32mBuild successful!\e[0m"

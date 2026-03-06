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

$ENGINE build -t signal-cli:build -f reproducible-builds/build.Containerfile .
$ENGINE build -t signal-cli:native -f reproducible-builds/native.Containerfile .
$ENGINE build -t signal-cli:client -f reproducible-builds/client.Containerfile .

# Build jar
git clean -Xfd -e '!/dist/' -e '!/dist/**' -e '!/github/' -e '!/github/**'
# shellcheck disable=SC2086
$ENGINE run --pull=never --rm -v "$(pwd)":/signal-cli:Z -e VERSION="$VERSION" $USER signal-cli:build
mv build/distributions/signal-cli-*.tar.gz dist/

# Build native-image
git clean -Xfd -e '!/dist/' -e '!/dist/**' -e '!/github/' -e '!/github/**'
# shellcheck disable=SC2086
$ENGINE run --pull=never --rm -v "$(pwd)":/signal-cli:Z -e VERSION="$VERSION" $USER signal-cli:native
mv build/signal-cli-*-Linux-native.tar.gz dist/

# Build rust client
git clean -Xfd -e '!/dist/' -e '!/dist/**' -e '!/github/' -e '!/github/**'
# shellcheck disable=SC2086
$ENGINE run --pull=never --rm -v "$(pwd)":/signal-cli:Z -e VERSION="$VERSION" $USER signal-cli:client
mv build/signal-cli-*-Linux-client.tar.gz dist/

ls -lsh dist/

echo -e "\e[32mBuild successful!\e[0m"

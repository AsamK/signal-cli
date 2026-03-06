#!/bin/bash

set -eu

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../"
cd "$ROOT_DIR"
rm -rf "$ROOT_DIR/github"
mkdir -p "$ROOT_DIR/github"

VERSION=$(sed -n 's/\s*version\s*=\s*"\(.*\)".*/\1/p' build.gradle.kts | tail -n1)

echo "Download latest release from GitHub..."

curl -L --fail "https://github.com/AsamK/signal-cli/releases/download/v${VERSION}/signal-cli-${VERSION}.tar.gz" -o "github/signal-cli-${VERSION}.tar.gz"
curl -L --fail "https://github.com/AsamK/signal-cli/releases/download/v${VERSION}/signal-cli-${VERSION}-Linux-native.tar.gz" -o "github/signal-cli-${VERSION}-Linux-native.tar.gz"
curl -L --fail "https://github.com/AsamK/signal-cli/releases/download/v${VERSION}/signal-cli-${VERSION}-Linux-client.tar.gz" -o "github/signal-cli-${VERSION}-Linux-client.tar.gz"

./reproducible-builds/build.sh

rm -f {github,dist}/VERSION

echo "commit: $(git rev-parse HEAD)"

echo "sha256 hashes of GitHub release:"
sha256sum github/*
echo "sha256 hashes of locally built files:"
sha256sum dist/*

reproducible=true
for file in $(cd github && find . -type f); do
	if diff "github/$file" "dist/$file" >/dev/null 2>&1; then
		echo -e "\e[32m[+] '$(basename "$file")' matches!\e[0m"
	else
		echo -e "\e[31m[-] '$(basename "$file")' doesn't match!\e[0m"
		reproducible=false
	fi
done

if [ "$reproducible" = false ]; then
	exit 1
fi

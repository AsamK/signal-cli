#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../"
cd "$ROOT_DIR"

if command -v podman >/dev/null; then
	ENGINE=podman
elif command -v docker >/dev/null; then
	ENGINE=docker
else
	echo "error: neither podman nor docker is available" >&2
	exit 1
fi

resolve_digest() {
	local image_ref="$1"
	"$ENGINE" pull "$image_ref" >/dev/null
	"$ENGINE" image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$image_ref" \
		| grep -m1 -E '@sha256:[0-9a-f]{64}$' \
		| sed -E 's|.*(@sha256:[0-9a-f]{64})$|\1|'
}

update_arg_tag() {
	local file="$1"
	local arg_name="$2"
	local image_prefix="$3"
	local current
	current="$(sed -n "s/^ARG ${arg_name}=\"\([^\"]*\)\"$/\\1/p" "$file")"
	if [[ -z "$current" ]]; then
		echo "error: could not find ARG ${arg_name} in $file" >&2
		exit 1
	fi
	local tag
	tag="${current%@*}"
	local digest
	digest="$(resolve_digest "${image_prefix}${tag}")"
	sed -i -E "s|^ARG ${arg_name}=\"[^\"]+\"$|ARG ${arg_name}=\"${tag}${digest}\"|" "$file"
	echo "updated $file -> ${tag}${digest}"
}

update_source_date_epoch() {
	local file="$1"
	local current_timestamp
	current_timestamp="$(date +%s)"
	sed -i -E "s|^ARG SOURCE_DATE_EPOCH=\"[^\"]+\"$|ARG SOURCE_DATE_EPOCH=\"${current_timestamp}\"|" "$file"
	echo "updated $file SOURCE_DATE_EPOCH -> ${current_timestamp}"
}

update_arg_tag reproducible-builds/build.Containerfile ZULU_TAG docker.io/azul/zulu-openjdk:
update_arg_tag reproducible-builds/native.Containerfile GRAALVM_TAG container-registry.oracle.com/graalvm/native-image:
update_arg_tag reproducible-builds/client.Containerfile RUST_TAG docker.io/rust:

update_source_date_epoch reproducible-builds/build.Containerfile
update_source_date_epoch reproducible-builds/native.Containerfile
update_source_date_epoch reproducible-builds/client.Containerfile

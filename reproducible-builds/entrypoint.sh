#!/bin/bash

set -eu

echo "Build '$1' variant $VERSION ..."

function reset_file_dates() {
	find . -exec touch -m -d "@$SOURCE_DATE_EPOCH" {} \;
}

reset_file_dates

if [ "$1" == "build" ]; then

	./gradlew build jsonSchemas \
		--no-daemon \
		--max-workers=1 \
		-Dkotlin.compiler.execution.strategy=in-process \
		--no-build-cache \
		-Dorg.gradle.caching=false \
		-Porg.gradle.java.installations.auto-download=false \
		-Porg.gradle.java.installations.auto-detect=false
	
	schemas_tar="build/signal-cli-${VERSION}-json-schemas.tar"
	reset_file_dates
	tar --sort=name --mtime="@$SOURCE_DATE_EPOCH" --owner=0 --group=0 --numeric-owner -cf "$schemas_tar" -C build/generated/META-INF/schemas .
	gzip -n -9 "$schemas_tar"

	cd man
	make install
	cd ..
	tar_archive="build/distributions/signal-cli-${VERSION}.tar"
	tar --transform="flags=r;s|man|signal-cli-${VERSION}/man|" -rf "$tar_archive" man/man{1,5}

	# Remake the tarball to ensure reproducible file order and timestamps
	mkdir -p build/extracted
	tar -xf "$tar_archive" -C build/extracted/
	reset_file_dates
	rm -f "$tar_archive"
	tar --sort=name --mtime="@$SOURCE_DATE_EPOCH" --transform='s|^\./||' --owner=0 --group=0 --numeric-owner -cf "$tar_archive" -C build/extracted .

	gzip -n -9 "$tar_archive"

elif [ "$1" == "native" ]; then

	./gradlew nativeCompile \
		--no-daemon \
		--max-workers=1 \
		-Dkotlin.compiler.execution.strategy=in-process \
		--no-build-cache \
		-Dorg.gradle.caching=false \
		-Dgraalvm.native-image.build-time=2026-01-01T00:00:00Z \
		-Porg.gradle.java.installations.auto-download=false \
		-Porg.gradle.java.installations.auto-detect=false

	strip --strip-all \
		--remove-section=.note.gnu.build-id \
		--remove-section=.comment \
		--remove-section=.gnu_debuglink \
		--remove-section=.annobin.notes \
		--remove-section=.gnu.build.attributes \
		--remove-section=.note.ABI-tag \
		build/native/nativeCompile/signal-cli

	chmod +x build/native/nativeCompile/signal-cli
	reset_file_dates
	tar --sort=name --mtime="@$SOURCE_DATE_EPOCH" --owner=0 --group=0 --numeric-owner -cf "build/signal-cli-${VERSION}-Linux-native.tar" -C build/native/nativeCompile signal-cli
	gzip -n -9 "build/signal-cli-${VERSION}-Linux-native.tar"

elif [ "$1" == "client" ]; then

	cd client
	cargo build --release --locked
	cd ..
	chmod +x client/target/release/signal-cli-client
	mkdir -p build
	reset_file_dates
	tar --sort=name --mtime="@$SOURCE_DATE_EPOCH" --owner=0 --group=0 --numeric-owner -cf "build/signal-cli-${VERSION}-Linux-client.tar" -C client/target/release signal-cli-client
	gzip -n -9 "build/signal-cli-${VERSION}-Linux-client.tar"

else
	echo "Unknown build variant '$1'"
	exit 1
fi

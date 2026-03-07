#!/bin/bash

set -eu

echo "Build '$1' variant $VERSION ..."

function reset_file_dates() {
	find . -exec touch -m -d "@$SOURCE_DATE_EPOCH" {} \;
}

reset_file_dates

if [ "$1" == "build" ]; then

	./gradlew build \
		--no-daemon \
		--max-workers=1 \
		-Dkotlin.compiler.execution.strategy=in-process \
		--no-build-cache \
		-Dorg.gradle.caching=false \
		-Porg.gradle.java.installations.auto-download=false \
		-Porg.gradle.java.installations.auto-detect=false
	cd man
	make install
	cd ..
	reset_file_dates
	tar tf build/distributions/signal-cli-*.tar | head -n1 | sed 's|signal-cli-\([^/]*\)/.*|\1|'
	tar --mtime="@$SOURCE_DATE_EPOCH" --transform="flags=r;s|man|signal-cli-${VERSION}/man|" -rf "build/distributions/signal-cli-${VERSION}.tar" man/man{1,5}
	gzip -n -9 build/distributions/signal-cli-*.tar

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
	tar --mtime="@$SOURCE_DATE_EPOCH" -czf "build/signal-cli-${VERSION}-Linux-native.tar.gz" -C build/native/nativeCompile signal-cli

elif [ "$1" == "client" ]; then

	cd client
	cargo build --release --locked
	cd ..
	chmod +x client/target/release/signal-cli-client
	mkdir -p build
	tar --mtime="@$SOURCE_DATE_EPOCH" -czf "build/signal-cli-${VERSION}-Linux-client.tar.gz" -C client/target/release signal-cli-client

else
	echo "Unknown build variant '$1'"
	exit 1
fi

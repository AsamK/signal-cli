ARG ZULU_TAG="25-latest@sha256:daf3a676e6a355acdbcabf9c06217f4d11a1abfa6503cdc36ffa05e1226e3375"

FROM docker.io/azul/zulu-openjdk:$ZULU_TAG
ARG SOURCE_DATE_EPOCH="1794060352"
ENV SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH
ENV LANG=C.UTF-8
ENV LC_CTYPE=en_US.UTF-8
RUN SNAPSHOT="$(date -u -d "@$SOURCE_DATE_EPOCH" +%Y%m%dT%H%M%SZ)" \
    && sed -i 's/^deb /deb [snapshot=yes] /' /etc/apt/sources.list && apt update --snapshot "$SNAPSHOT" && apt install -y make asciidoc-base --snapshot "$SNAPSHOT" --no-install-recommends --no-install-suggests
COPY --chmod=0700 reproducible-builds/entrypoint.sh /usr/local/bin/entrypoint.sh
WORKDIR /signal-cli
ENTRYPOINT [ "/usr/local/bin/entrypoint.sh", "build" ]

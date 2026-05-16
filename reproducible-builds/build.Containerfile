ARG ZULU_TAG="25-latest@sha256:8eca9375451a392bff01efe946f2e9263c50aa71a9d68423c068cc1061a41b7e"

FROM docker.io/azul/zulu-openjdk:$ZULU_TAG
ARG SOURCE_DATE_EPOCH="1776889382"
ENV SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH
ENV LANG=C.UTF-8
ENV LC_CTYPE=en_US.UTF-8
RUN SNAPSHOT="$(date -u -d "@$SOURCE_DATE_EPOCH" +%Y%m%dT%H%M%SZ)" \
    && apt update && apt install -y make asciidoc-base --snapshot "$SNAPSHOT" --no-install-recommends --no-install-suggests
COPY --chmod=0700 reproducible-builds/entrypoint.sh /usr/local/bin/entrypoint.sh
WORKDIR /signal-cli
ENTRYPOINT [ "/usr/local/bin/entrypoint.sh", "build" ]

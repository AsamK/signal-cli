ARG RUST_TAG="1-slim@sha256:715efd1ccdc4a63bd6a6e2f54387fff73f904b70e610d41b4d9d74ff38e13ad3"

FROM docker.io/rust:$RUST_TAG
ARG SOURCE_DATE_EPOCH="1776889382"
ENV SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH
ENV LANG=C.UTF-8
ENV LC_CTYPE=en_US.UTF-8
COPY --chmod=0700 reproducible-builds/entrypoint.sh /usr/local/bin/entrypoint.sh
WORKDIR /signal-cli
ENTRYPOINT [ "/usr/local/bin/entrypoint.sh", "client" ]

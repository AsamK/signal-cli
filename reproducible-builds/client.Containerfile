FROM docker.io/rust:1.93.1-slim-trixie@sha256:7f2c9f2f0dad8f4afa6faf5efa971e7e566398a36e54fb7684061407ea067058
ENV SOURCE_DATE_EPOCH=1767225600
ENV LANG=C.UTF-8
ENV LC_CTYPE=en_US.UTF-8
COPY --chmod=0700 reproducible-builds/entrypoint.sh /usr/local/bin/entrypoint.sh
WORKDIR /signal-cli
ENTRYPOINT [ "/usr/local/bin/entrypoint.sh", "client" ]

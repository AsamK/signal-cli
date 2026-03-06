FROM docker.io/azul/zulu-openjdk:25.0.2-jdk@sha256:0349494e05c22fe439e65be99771581b2bc428d89f07363b539389a11103fa5f
ENV SOURCE_DATE_EPOCH=1767225600
ENV LANG=C.UTF-8
ENV LC_CTYPE=en_US.UTF-8
ARG SNAPSHOT=20260101T000000Z
RUN echo "deb http://snapshot.ubuntu.com/ubuntu/${SNAPSHOT}/ jammy main" > /etc/apt/sources.list \
    && echo "deb http://snapshot.ubuntu.com/ubuntu/${SNAPSHOT}/ jammy universe" >> /etc/apt/sources.list
RUN apt update && apt install -y make asciidoc-base
COPY --chmod=0700 reproducible-builds/entrypoint.sh /usr/local/bin/entrypoint.sh
WORKDIR /signal-cli
ENTRYPOINT [ "/usr/local/bin/entrypoint.sh", "build" ]

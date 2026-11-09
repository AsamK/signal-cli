ARG GRAALVM_TAG="25@sha256:694902dbc7eaf03d9da7531b5267dda7dd1e085b01c8df180966ef92b82fe00e"

FROM container-registry.oracle.com/graalvm/native-image:$GRAALVM_TAG
ARG SOURCE_DATE_EPOCH="1794060352"
ENV SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH
ENV LANG=C.UTF-8
ENV LC_CTYPE=en_US.UTF-8
COPY --chmod=0700 reproducible-builds/entrypoint.sh /usr/local/bin/entrypoint.sh
WORKDIR /signal-cli
ENTRYPOINT [ "/usr/local/bin/entrypoint.sh", "native" ]

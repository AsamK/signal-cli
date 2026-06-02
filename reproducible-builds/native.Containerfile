ARG GRAALVM_TAG="25@sha256:38f835ccb37d4a106c37376a98e8713999077a8c8173d9876505f77da438332c"

FROM container-registry.oracle.com/graalvm/native-image:$GRAALVM_TAG
ARG SOURCE_DATE_EPOCH="1776889382"
ENV SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH
ENV LANG=C.UTF-8
ENV LC_CTYPE=en_US.UTF-8
COPY --chmod=0700 reproducible-builds/entrypoint.sh /usr/local/bin/entrypoint.sh
WORKDIR /signal-cli
ENTRYPOINT [ "/usr/local/bin/entrypoint.sh", "native" ]

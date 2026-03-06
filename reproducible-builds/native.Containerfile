FROM container-registry.oracle.com/graalvm/native-image:25.0.2@sha256:e8c5ec4f256bf958f327aea060e1424aa87f63114aeb4a4318a0ac169bbdb9a1
ENV SOURCE_DATE_EPOCH=1767225600
ENV LANG=C.UTF-8
ENV LC_CTYPE=en_US.UTF-8
COPY --chmod=0700 reproducible-builds/entrypoint.sh /usr/local/bin/entrypoint.sh
WORKDIR /signal-cli
ENTRYPOINT [ "/usr/local/bin/entrypoint.sh", "native" ]

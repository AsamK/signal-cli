FROM eclipse-temurin:17

ADD build/distributions/signal-*.tar /tmp
RUN mv /tmp/signal-cli-* /opt/signal-cli

ENTRYPOINT ["/opt/signal-cli/bin/signal-cli"]

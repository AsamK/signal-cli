FROM docker.io/eclipse-temurin:17-jre

RUN useradd signal-cli --system --create-home --home-dir /var/lib/signal-cli
ADD build/install/signal-cli /opt/signal-cli

USER signal-cli
ENTRYPOINT ["/opt/signal-cli/bin/signal-cli", "--config=/var/lib/signal-cli"]

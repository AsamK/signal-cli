FROM docker.io/eclipse-temurin:17-jre

LABEL org.opencontainers.image.source=https://github.com/AsamK/signal-cli
LABEL org.opencontainers.image.description="signal-cli provides an unofficial commandline, dbus and JSON-RPC interface for the Signal messenger."
LABEL org.opencontainers.image.licenses=GPL-3.0-only

RUN useradd signal-cli --system --create-home --home-dir /var/lib/signal-cli
ADD build/install/signal-cli /opt/signal-cli

USER signal-cli
ENTRYPOINT ["/opt/signal-cli/bin/signal-cli", "--config=/var/lib/signal-cli"]

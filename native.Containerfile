FROM docker.io/debian:testing-slim

RUN useradd signal-cli --system --create-home --home-dir /var/lib/signal-cli
ADD build/native/nativeCompile/signal-cli /usr/bin/signal-cli

USER signal-cli
ENTRYPOINT ["/usr/bin/signal-cli", "--config=/var/lib/signal-cli"]

FROM openjdk:8u151-alpine
RUN apk update \
&& apk upgrade \
&& apk add --no-cache bash libc6-compat
COPY build/install/signal-cli /opt/signal-cli
ENV PATH="/opt/signal-cli/bin:${PATH}"
RUN adduser -D -g '' user
USER user
RUN mkdir -pv ~/.config/signal/data
CMD ["signal-cli", "--singleuser", "socket", "-a", "0.0.0.0"]

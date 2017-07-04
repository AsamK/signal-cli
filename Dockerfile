# Dockerfile for automated build of signal-cli
#
# Refer to the signal-cli github pages for detailed Usage.
#
FROM gradle:3.5-jdk7-alpine

LABEL maintainer "Kayvan Sylvan <kayvansylvan@gmail.com>"

COPY . /tmp/src
WORKDIR /tmp/src

USER root

RUN ./gradlew build && ./gradlew installDist && ./gradlew distTar \
  && tar xf build/distributions/signal-cli-*.tar -C /opt \
  && ln -sf /opt/signal-cli-*/bin/signal-cli /usr/local/bin/ \
  && rm -rf /tmp/src

USER gradle
WORKDIR /home/gradle

ENTRYPOINT ["/usr/local/bin/signal-cli"]

FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        dbus-x11 \
        fontconfig \
        fonts-dejavu \
        git \
        libasound2t64 \
        libgbm1 \
        libgl1 \
        libgtk-3-0 \
        libgtk2.0-0 \
        libnss3 \
        libsecret-1-0 \
        libxcomposite1 \
        libxdamage1 \
        libxfixes3 \
        libxi6 \
        libxkbcommon-x11-0 \
        libxrandr2 \
        libxrender1 \
        libxss1 \
        libxtst6 \
        openjdk-21-jdk \
        rsync \
        xauth \
        xvfb \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --gid 10001 tester && useradd --uid 10001 --gid 10001 --create-home --shell /bin/bash tester

COPY docker/run-gradle-isolated.sh /usr/local/bin/run-gradle-isolated
RUN chmod +x /usr/local/bin/run-gradle-isolated

USER tester
WORKDIR /work
ENTRYPOINT ["/usr/local/bin/run-gradle-isolated"]

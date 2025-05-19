FROM docker.io/eclipse-temurin:17.0.14_7-jdk-alpine-3.21@sha256:b16e661d76d3af0d226d0585063dbcafe7fb8a4ef31cfcaaec71d39c41269420 AS builder

ARG ANDROID_SDK_DIST=commandlinetools-linux-11076708_latest.zip
ARG ANDROID_SDK_SHA256=2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258

ENV ANDROID_HOME=/opt/android-sdk-linux

RUN apk add --no-cache curl git gcompat

RUN mkdir -p "${ANDROID_HOME}"

RUN curl -o sdk.zip "https://dl.google.com/android/repository/${ANDROID_SDK_DIST}"
RUN echo "${ANDROID_SDK_SHA256}" sdk.zip | sha256sum -c -
RUN unzip -q -d "${ANDROID_HOME}/cmdline-tools/" sdk.zip && \
    mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" && \
    rm sdk.zip

ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin"

RUN mkdir /root/.android && touch /root/.android/repositories.cfg
RUN yes | sdkmanager --licenses

RUN sdkmanager "platform-tools"

ARG NDK_VERSION=28.0.13004108
ARG BUILD_TOOLS_VERSION=35.0.0
ARG COMPILE_SDK_VERSION=android-35

RUN sdkmanager "ndk;${NDK_VERSION}"
RUN sdkmanager "platforms;${COMPILE_SDK_VERSION}"
RUN sdkmanager "build-tools;${BUILD_TOOLS_VERSION}"

COPY gradlew /molly/
COPY gradle /molly/gradle/
RUN /molly/gradlew --version

ENV GRADLE_RO_DEP_CACHE=/.gradle-ro-cache

COPY . /molly/
WORKDIR /molly
RUN git clean -df

ENTRYPOINT ["./gradlew", "-PCI=true"]

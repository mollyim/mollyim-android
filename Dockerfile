FROM docker.io/eclipse-temurin:17.0.7_7-sdk-jammy@sha256:ab4bbe391a42adc8e590d0c54b3ca7903cbc3b62a3e3b23ac8dce94ebfef6b9e AS builder

ARG ANDROID_SDK_DIST=commandlinetools-linux-10406996_latest.zip
ARG ANDROID_SDK_SHA256=8919e8752979db73d8321e9babe2caedcc393750817c1a5f56c128ec442fb540

ENV ANDROID_HOME=/opt/android-sdk-linux

RUN apt-get update && apt-get install -y unzip git

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

ARG ANDROID_API_LEVEL=34
ARG ANDROID_BUILD_TOOLS_VERSION=34.0.0

RUN sdkmanager "platforms;android-${ANDROID_API_LEVEL}"
RUN sdkmanager "build-tools;${ANDROID_BUILD_TOOLS_VERSION}"

COPY gradlew /molly/
COPY gradle /molly/gradle/
RUN /molly/gradlew --version

ENV GRADLE_RO_DEP_CACHE=/.gradle-ro-cache

COPY . /molly/
WORKDIR /molly
RUN git clean -df

ENTRYPOINT ["./gradlew", "-PCI=true"]

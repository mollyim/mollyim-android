FROM openjdk:8u242

RUN apt-get update && apt-get install -y make file

ENV ANDROID_API_LEVELS android-28
ENV ANDROID_TOOLS_VERSION 25.2.5
ENV ANDROID_SDK_SHA256 577516819c8b5fae680f049d39014ff1ba4af870b687cab10595783e6f22d33e
ENV ANDROID_BUILD_TOOLS_VERSION 28.0.3
ENV ANDROID_NDK_VERSION r21
ENV ANDROID_NDK_SHA256 b65ea2d5c5b68fb603626adcbcea6e4d12c68eb8a73e373bbb9d23c252fc647b

ENV ANDROID_HOME /opt/android-sdk-linux
ENV ANDROID_NDK_HOME ${ANDROID_HOME}/android-ndk-${ANDROID_NDK_VERSION}
ENV PATH ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools:${ANDROID_NDK_HOME}

RUN curl -o sdk.zip https://dl.google.com/android/repository/tools_r${ANDROID_TOOLS_VERSION}-linux.zip
RUN printf -- '%s  %s\n' "${ANDROID_SDK_SHA256}" "sdk.zip" | sha256sum -c -
RUN unzip -q -d ${ANDROID_HOME} sdk.zip && rm sdk.zip

RUN curl -o ndk.zip https://dl.google.com/android/repository/android-ndk-${ANDROID_NDK_VERSION}-linux-x86_64.zip
RUN printf -- '%s  %s\n' "${ANDROID_NDK_SHA256}" "ndk.zip" | sha256sum -c -
RUN unzip -q -d ${ANDROID_HOME} ndk.zip && rm ndk.zip

RUN echo y | android update sdk --no-ui --all --filter platform-tools,build-tools-${ANDROID_BUILD_TOOLS_VERSION},${ANDROID_API_LEVELS}
RUN echo y | android update sdk --no-ui --all --filter extra-google-m2repository,extra-android-m2repository,extra-android-support

COPY . /molly
WORKDIR /molly

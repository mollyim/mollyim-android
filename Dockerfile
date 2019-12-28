FROM openjdk:8u232

RUN apt-get update && apt-get install -y make file

ENV ANDROID_API_LEVELS android-28
ENV ANDROID_TOOLS_VERSION 25.2.5
ENV ANDROID_BUILD_TOOLS_VERSION 28.0.3
ENV ANDROID_NDK_VERSION r20b

ENV ANDROID_HOME /opt/android-sdk-linux
ENV ANDROID_NDK_HOME ${ANDROID_HOME}/android-ndk-${ANDROID_NDK_VERSION}
ENV PATH ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools:${ANDROID_NDK_HOME}

RUN curl -o sdk.zip https://dl.google.com/android/repository/tools_r${ANDROID_TOOLS_VERSION}-linux.zip
RUN unzip -q -d ${ANDROID_HOME} sdk.zip && rm sdk.zip

RUN curl -o ndk.zip https://dl.google.com/android/repository/android-ndk-${ANDROID_NDK_VERSION}-linux-x86_64.zip
RUN unzip -q -d ${ANDROID_HOME} ndk.zip && rm ndk.zip

RUN echo y | android update sdk --no-ui --all --filter platform-tools,build-tools-${ANDROID_BUILD_TOOLS_VERSION},${ANDROID_API_LEVELS}
RUN echo y | android update sdk --no-ui --all --filter extra-google-m2repository,extra-android-m2repository,extra-android-support

COPY . /src
WORKDIR /src

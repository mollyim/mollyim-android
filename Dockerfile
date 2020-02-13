FROM openjdk:8u242

RUN apt-get update && apt-get install -y make file

ENV ANDROID_API_LEVELS android-28
ENV ANDROID_BUILD_TOOLS_VERSION 28.0.3
ENV ANDROID_SDK_DIST sdk-tools-linux-4333796.zip
ENV ANDROID_SDK_SHA256 92ffee5a1d98d856634e8b71132e8a95d96c83a63fde1099be3d86df3106def9
ENV ANDROID_NDK_DIST android-ndk-r21-linux-x86_64.zip
ENV ANDROID_NDK_SHA256 b65ea2d5c5b68fb603626adcbcea6e4d12c68eb8a73e373bbb9d23c252fc647b

ENV ANDROID_HOME /opt/android-sdk-linux
ENV ANDROID_NDK_HOME ${ANDROID_HOME}/android-ndk

RUN curl -o sdk.zip https://dl.google.com/android/repository/${ANDROID_SDK_DIST}
RUN echo ${ANDROID_SDK_SHA256} sdk.zip | sha256sum -c -
RUN unzip -q -d ${ANDROID_HOME} sdk.zip && rm sdk.zip

RUN curl -o ndk.zip https://dl.google.com/android/repository/${ANDROID_NDK_DIST}
RUN echo ${ANDROID_NDK_SHA256} ndk.zip | sha256sum -c -
RUN unzip -q -d ${ANDROID_HOME} ndk.zip && rm ndk.zip

ENV PATH ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/tools/bin:${ANDROID_HOME}/platform-tools:${ANDROID_NDK_HOME}

RUN mkdir /root/.android && touch /root/.android/repositories.cfg
RUN yes | sdkmanager  --licenses
RUN sdkmanager \
    "platform-tools" \
    "build-tools;${ANDROID_BUILD_TOOLS_VERSION}" \
    "platforms;${ANDROID_API_LEVELS}" \
    "extras;google;m2repository" \
    "extras;android;m2repository"

COPY . /molly
WORKDIR /molly

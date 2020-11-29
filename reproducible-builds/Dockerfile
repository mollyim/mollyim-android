ARG JAVA_UPDATE_RELEASE=8u272
ARG DEBIAN_RELEASE=buster

FROM openjdk:${JAVA_UPDATE_RELEASE}-${DEBIAN_RELEASE}

ARG ANDROID_SDK_DIST=commandlinetools-linux-6858069_latest.zip
ARG ANDROID_SDK_SHA256=87f6dcf41d4e642e37ba03cb2e387a542aa0bd73cb689a9e7152aad40a6e7a08

RUN apt-get update && apt-get install -y make file

ENV ANDROID_HOME=/opt/android-sdk-linux

RUN curl -o sdk.zip "https://dl.google.com/android/repository/${ANDROID_SDK_DIST}"
RUN echo "${ANDROID_SDK_SHA256}" sdk.zip | sha256sum -c -
RUN unzip -q -d "${ANDROID_HOME}" sdk.zip && rm sdk.zip

ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/bin"

RUN mkdir /root/.android && touch /root/.android/repositories.cfg
RUN yes | sdkmanager --licenses

RUN sdkmanager \
    "platform-tools" \
    "extras;google;m2repository" \
    "extras;android;m2repository"

COPY . /molly
WORKDIR /molly

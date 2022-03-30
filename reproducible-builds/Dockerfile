FROM openjdk:11.0.13-bulleye@sha256:f6ded9543abec9fd65e26cc4b3b683fe6f593c04b17ddfd327fcdffe0ca9fda3

# ANDROID_COMMAND_LINE_TOOLS_FILENAME
ARG ANDROID_SDK_DIST=commandlinetools-linux-7583922_latest.zip
ARG ANDROID_SDK_SHA256=124f2d5115eee365df6cf3228ffbca6fc3911d16f8025bebd5b1c6e2fcfa7faf

ENV ANDROID_HOME=/opt/android-sdk-linux

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

# ANDROID_API_LEVELS
# ANDROID_BUILD_TOOLS_VERSION
RUN sdkmanager "platforms;android-31"
RUN sdkmanager "build-tools;31.0.0"

COPY gradlew /molly/
COPY gradle /molly/gradle/
RUN /molly/gradlew --version

COPY . /molly/
WORKDIR /molly
RUN git clean -df

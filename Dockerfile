FROM travisci/ci-amethyst:packer-1513010293-3f2fb39

# SDK-tools version is pinned here
ENV ANDROID_API_LEVELS android-28
ENV ANDROID_TOOLS_VERSION 25.2.5
ENV ANDROID_BUILD_TOOLS_VERSION 28.0.3

ENV ANDROID_HOME /opt/android-sdk-linux
ENV PATH ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools

RUN curl -o sdk.zip https://dl.google.com/android/repository/tools_r${ANDROID_TOOLS_VERSION}-linux.zip
RUN unzip -q -d ${ANDROID_HOME} sdk.zip && rm sdk.zip

RUN echo y | android update sdk --no-ui --all --filter platform-tools,build-tools-${ANDROID_BUILD_TOOLS_VERSION},${ANDROID_API_LEVELS}
RUN echo y | android update sdk --no-ui --all --filter extra-google-m2repository,extra-android-m2repository,extra-android-support

COPY . /sig4a
WORKDIR /sig4a

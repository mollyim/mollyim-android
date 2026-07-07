FROM docker.io/eclipse-temurin:21.0.11_10-jdk-alpine-3.23@sha256:4ec2402e5ebb803add08b063b9e5e52e7c11961caaae1f490479d925753f0d92 AS base

# Stage 1: base
# Minimal environment with only system tools. This stage is stable and reusable across merges.

RUN apk add --no-cache curl gcompat git make

# Set a default Git identity
RUN git config --global user.name "builder" \
 && git config --global user.email "builder@docker.local"

FROM base AS workspace

# Stage 2: workspace
# Adds toolchains, project source, and the build entrypoint.

LABEL im.molly.artifacts.m2.path="/root/.m2/repository/"

# Install Android SDK
ARG ANDROID_SDK_DIST="commandlinetools-linux-13114758_latest.zip"
ARG ANDROID_SDK_SHA256="7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee"
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"

RUN set -eux; \
    curl -fS "https://dl.google.com/android/repository/$ANDROID_SDK_DIST" -o sdk.zip; \
    printf '%s  %s\n' "$ANDROID_SDK_SHA256" "sdk.zip" | sha256sum -c; \
    mkdir -p "$ANDROID_HOME"; \
    unzip -q sdk.zip -d "$ANDROID_HOME/cmdline-tools/"; \
    rm sdk.zip; \
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"; \
    yes | sdkmanager --licenses; \
    sdkmanager "platform-tools"

ARG NDK_VERSION="28.0.13004108"
RUN sdkmanager "ndk;$NDK_VERSION"

ENV ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$NDK_VERSION"

ARG BUILD_TOOLS_VERSION="36.0.0"
RUN sdkmanager "build-tools;${BUILD_TOOLS_VERSION}"

ARG COMPILE_SDK="android-36.1"
RUN sdkmanager "platforms;${COMPILE_SDK}"

# Cache gradle wrapper
COPY gradlew /molly/
COPY gradle/wrapper /molly/gradle/wrapper
RUN /molly/gradlew --version

# Set optional read-only Gradle cache (mount at runtime)
ENV GRADLE_RO_DEP_CACHE=/.gradle-ro-cache

# Override project's gradle.properties
COPY gradle.properties.docker /root/.gradle/gradle.properties

# Copy project and purge generated files
COPY . /molly/
WORKDIR /molly
RUN git clean -ffdx

# Use Make as the builder entrypoint
ENTRYPOINT ["make"]
CMD ["help"]

FROM workspace AS deps-override

# Stage 2A: deps-override
# Load pre-built dependencies from a local Maven repository.
# Build with: docker build --build-context maven-local=<path/to/m2> --target deps-override .
COPY --from=maven-local /repository/ /root/.m2/repository/

FROM workspace AS builder

# Stage 3: builder
# Identical to workspace. Dependencies resolve from remote repositories only.

FROM docker.io/eclipse-temurin:21.0.11_10-jdk-alpine-3.23@sha256:4ec2402e5ebb803add08b063b9e5e52e7c11961caaae1f490479d925753f0d92 AS base

# Stage 1: base
# Minimal environment with only system tools. This stage is stable and reusable across merges.

RUN apk add --no-cache bash curl fd gcompat git make ripgrep

# Set a default Git identity
RUN git config --global user.name "builder" \
 && git config --global user.email "builder@docker.local"

FROM base AS toolchain

# Stage 2: toolchain
# Language SDKs, compilers, and build tools.

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

# Pre-fetch Gradle wrapper
COPY gradlew /molly/
COPY gradle/wrapper /molly/gradle/wrapper
RUN /molly/gradlew --version

# Set optional read-only Gradle cache (mount at runtime)
ENV GRADLE_RO_DEP_CACHE=/.gradle-ro-cache

# Override project's gradle.properties
COPY gradle.properties.docker /root/.gradle/gradle.properties

FROM toolchain AS source

# Stage 3: source
# Full project source tree.

WORKDIR /molly
COPY . .

FROM source AS deps-overlay

# Stage 4: deps-overlay
# Hook for pre-built dependencies, supplied via named build contexts.

ARG LOA_DEP_LIBSIGNAL=scratch
COPY --from=${LOA_DEP_LIBSIGNAL} / /molly/prebuilts/
ARG LOA_DEP_RINGRTC=scratch
COPY --from=${LOA_DEP_RINGRTC} / /molly/prebuilts/

FROM deps-overlay AS assemble

# Stage 5: assemble
# Build stage for the project.
# Can be overridden with a prebuilt image via:
#  --build-context assemble=docker-image://<image>

RUN make assemble

FROM scratch AS artifacts

# Stage 6: artifacts
# Build outputs only, laid out exactly as consumers expect.

COPY --from=assemble /molly/app/build/outputs/apk /apk
COPY --from=assemble /molly/app/build/outputs/bundle /bundle

FROM deps-overlay AS default

# Stage 7: default
# Runnable build environment without prebuilt outputs.
# Left as the final stage so it becomes the default target.

ENTRYPOINT ["make"]
CMD ["help"]

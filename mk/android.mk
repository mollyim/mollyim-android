# Copyright 2026 Molly Instant Messenger
# SPDX-License-Identifier: AGPL-3.0-only

## Extra arguments forwarded to Gradle
GRADLE_ARGS ?=

GRADLEW := ./gradlew -PCI=true

do_assemble = $(strip $(GRADLEW) :app:assembleRelease :app:bundleRelease $(GRADLE_ARGS))
do_test     = $(strip $(GRADLEW) build $(GRADLE_ARGS))
do_publish  = $(warning Not implemented yet)
do_clean    = $(strip $(GRADLEW) clean $(GRADLE_ARGS))

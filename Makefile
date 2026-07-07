# Copyright 2026 Molly Instant Messenger
# SPDX-License-Identifier: AGPL-3.0-only
#
# Docker builder entry point.
#
# This Makefile is the entrypoint for the Docker builder image. It provides
# the same stable build targets on every platform. Platform-specific commands
# live in mk/<platform>.mk and are selected by TARGET_PLATFORM.

TARGET_PLATFORM ?= android

ifeq ($(TARGET_PLATFORM),android)
include mk/android.mk
else
$(error Unsupported TARGET_PLATFORM='$(TARGET_PLATFORM)')
endif

.DEFAULT_GOAL := help
.PHONY: help assemble test publish clean

# Variables are documented by a preceding comment in the form "## text".
help:
	@echo "Docker builder for platform: $(TARGET_PLATFORM)"
	@echo
	@echo "Targets:"
	@echo "  help       Show this help"
	@echo "  assemble   Compile and install artifacts for export"
	@echo "  test       Run tests"
	@echo "  publish    Publish artifacts"
	@echo "  clean      Remove build artifacts"
	@echo
	@echo "Variables:"
	@awk ' \
		/^## / { desc = substr($$0, 4); next } \
		desc { printf "  %-18s %s\n", $$1, desc; desc = "" } \
	' $(MAKEFILE_LIST)

assemble:
	$(do_assemble)

test:
	$(do_test)

publish:
	$(do_publish)

clean:
	$(do_clean)

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE     := native-utils
LOCAL_C_INCLUDES := $(LOCAL_PATH)/utils
LOCAL_CFLAGS     += -Wall

LOCAL_SRC_FILES := utils/org_thoughtcrime_securesms_util_FileUtils.cpp

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE     := argon2
LOCAL_C_INCLUDES := $(LOCAL_PATH)/argon2/include
LOCAL_CFLAGS     += -fvisibility=hidden

LOCAL_SRC_FILES  := \
    argon2/src/argon2.c \
    argon2/src/core.c \
    argon2/src/blake2/blake2b.c \
    argon2/src/thread.c \
    argon2/src/encoding.c \
    argon2/src/ref.c \
    argon2/argon2jni.c

include $(BUILD_SHARED_LIBRARY)

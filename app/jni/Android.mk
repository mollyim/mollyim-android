LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE     := native-utils
LOCAL_C_INCLUDES := $(LOCAL_PATH)/utils
LOCAL_CFLAGS     += -Wall
ifeq ($(APP_OPTIM),release)
LOCAL_LDLIBS     += -Wl,--build-id=none
endif

LOCAL_SRC_FILES := utils/org_thoughtcrime_securesms_util_FileUtils.cpp
LOCAL_SRC_FILES += utils/org_thoughtcrime_securesms_service_WipeMemoryService.c

include $(BUILD_SHARED_LIBRARY)

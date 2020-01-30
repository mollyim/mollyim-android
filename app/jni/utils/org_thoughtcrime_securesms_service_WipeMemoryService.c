#include <jni.h>
#include <bits/sysconf.h>

#include "stdlib.h"

#define PAGE_SIZE sysconf(_SC_PAGE_SIZE)

JNIEXPORT jlong JNICALL Java_org_thoughtcrime_securesms_service_WipeMemoryService_allocPages
    (JNIEnv *env, jclass clazz, jint count)
{
  if (count == 0) return (long) NULL;

  void *p = malloc(count * PAGE_SIZE);

  return (long) p;
}

JNIEXPORT void JNICALL Java_org_thoughtcrime_securesms_service_WipeMemoryService_freePages
    (JNIEnv *env, jclass clazz, jlong p)
{
  free((void *) p);
}

JNIEXPORT void JNICALL Java_org_thoughtcrime_securesms_service_WipeMemoryService_wipePage
    (JNIEnv *env, jclass clazz, jlong p, jint index)
{
  int *x = (void *) p + (index * PAGE_SIZE);

  int size = PAGE_SIZE;

  do {
    *x++ = rand();
    size -= sizeof(*x);
  } while (size);
}

JNIEXPORT jint JNICALL Java_org_thoughtcrime_securesms_service_WipeMemoryService_getPageSize
    (JNIEnv *env, jclass clazz)
{
  return PAGE_SIZE;
}

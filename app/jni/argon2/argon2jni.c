#include "argon2.h"

#include <jni.h>
#include <strings.h>

JNIEXPORT jbyteArray JNICALL Java_org_thoughtcrime_securesms_crypto_Argon2_IDHashRaw(
        JNIEnv *env, jclass jc, jint t_cost, jint m_cost, jint threads,
        jbyteArray jpwd, jbyteArray jsalt, jint outlen)
{
    if (t_cost < 0 || m_cost < 0 || threads < 0 || outlen < 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "argon2 values should not be less than 0");
        return NULL;
    }

    jbyte *pwd      = (*env)->GetByteArrayElements(env, jpwd, NULL);
    jsize pwdlen    = (*env)->GetArrayLength(env, jpwd);

    jbyte *salt     = (*env)->GetByteArrayElements(env, jsalt, NULL);
    jsize saltlen   = (*env)->GetArrayLength(env, jsalt);

    argon2_context ctx;
    uint8_t out[outlen];

    bzero(&ctx, sizeof(ctx));

    ctx.out       = out;
    ctx.outlen    = outlen;
    ctx.pwd       = (uint8_t *) pwd;
    ctx.pwdlen    = pwdlen;
    ctx.salt      = (uint8_t *) salt;
    ctx.saltlen   = saltlen;
    ctx.t_cost    = t_cost;
    ctx.m_cost    = m_cost;
    ctx.lanes     = threads;
    ctx.threads   = threads;
    ctx.flags     = ARGON2_FLAG_CLEAR_PASSWORD | ARGON2_FLAG_CLEAR_SECRET;
    ctx.version   = ARGON2_VERSION_13;

    int ret = argon2id_ctx(&ctx);

    (*env)->ReleaseByteArrayElements(env, jpwd, pwd, 0);
    (*env)->ReleaseByteArrayElements(env, jsalt, salt, JNI_ABORT);

    jbyteArray jout = NULL;

    if (ret == ARGON2_OK) {
        jout = (*env)->NewByteArray(env, outlen);
        if (jout != NULL) {
            (*env)->SetByteArrayRegion(env, jout, 0, outlen, (jbyte *) out);
        }
    } else {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/RuntimeException"),
                         argon2_error_message(ret));
        jout = NULL;
    }

    bzero(out, outlen);

    return jout;
}

/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class EnergyCheckUtils */

#ifndef _Included_EnergyCheckUtils
#define _Included_EnergyCheckUtils
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     EnergyCheckUtils
 * Method:    ProfileInit
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_EnergyCheckUtils_ProfileInit
  (JNIEnv *, jclass);

/*
 * Class:     EnergyCheckUtils
 * Method:    GetSocketNum
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_EnergyCheckUtils_GetSocketNum
  (JNIEnv *, jclass);

/*
 * Class:     EnergyCheckUtils
 * Method:    EnergyStatCheck
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_EnergyCheckUtils_EnergyStatCheck
  (JNIEnv *, jclass);

JNIEXPORT jdouble JNICALL Java_EnergyCheckUtils_GetPkgEnergy
  (JNIEnv *, jclass, jint);

JNIEXPORT jdouble JNICALL Java_EnergyCheckUtils_GetCoreVoltage
 (JNIEnv *, jclass, jint);

// If disabled, powerlimit is set to -1
// limit1: TDP (don't change) limit2: short-term (7.8ms)
// 0: powerlimit1, 1: timewindow1, 2: powerlimit2, 3: timewindow2
JNIEXPORT jdoubleArray JNICALL Java_EnergyCheckUtils_GetPkgLimit
  (JNIEnv *, jclass, jint);

JNIEXPORT void JNICALL Java_EnergyCheckUtils_SetPkgLimit
  (JNIEnv *, jclass, jint, jdouble, jdouble);

JNIEXPORT jdouble JNICALL Java_EnergyCheckUtils_GetDramEnergy
  (JNIEnv *, jclass, jint);

JNIEXPORT jlong JNICALL Java_EnergyCheckUtils_GetAPERF
  (JNIEnv *, jclass, jint);

JNIEXPORT jlong JNICALL Java_EnergyCheckUtils_GetMPERF
  (JNIEnv *, jclass, jint);

/*
 * Class:     EnergyCheckUtils
 * Method:    ProfileDealloc
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_EnergyCheckUtils_ProfileDealloc
  (JNIEnv *, jclass);

JNIEXPORT jint JNICALL Java_EnergyCheckUtils_getCoreNum(JNIEnv * env, jclass jcls);

JNIEXPORT jint JNICALL Java_EnergyCheckUtils_getThreadPerCore(JNIEnv * env, jclass jcls);

#ifdef __cplusplus
}
#endif
#endif


#include <jni.h>


#ifndef _Included_qingwei_kong_serialportlibrary_SerialPort
#define _Included_qingwei_kong_serialportlibrary_SerialPort
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jobject JNICALL Java_com_wrs_project_module_serialport_SerialPort_open
  (JNIEnv *, jclass, jstring, jint, jint, jint, jint, jint, jint);


JNIEXPORT void JNICALL Java_com_wrs_project_module_serialport_SerialPort_close
  (JNIEnv *, jobject);

JNIEXPORT jobject JNICALL Java_com_wrs_project_module_serialport_SerialPort_forceOpenNative
  (JNIEnv *, jclass, jstring, jint, jint, jint, jint, jint);

JNIEXPORT jboolean JNICALL Java_com_wrs_project_module_serialport_SerialPortManager_configureExistingFD
  (JNIEnv *, jclass, jobject, jint, jint, jint, jint, jint);

JNIEXPORT jboolean JNICALL Java_com_wrs_project_module_serialport_SystemPrivilegeHelper_addToGroupNative
  (JNIEnv *, jclass, jint);

JNIEXPORT jboolean JNICALL Java_com_wrs_project_module_serialport_SELinuxBypass_setContextAndOpenNative
  (JNIEnv *, jclass, jstring, jstring);

JNIEXPORT jobject JNICALL Java_com_wrs_project_module_serialport_SELinuxBypass_privilegedOpenNative
  (JNIEnv *, jclass, jstring);

JNIEXPORT jobject JNICALL Java_com_wrs_project_module_serialport_SystemSignatureHelper_systemSignatureOpenNative
  (JNIEnv *, jclass, jstring, jint, jint, jint, jint, jint);

#ifdef __cplusplus
}
#endif
#endif

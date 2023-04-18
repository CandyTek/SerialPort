
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

#ifdef __cplusplus
}
#endif
#endif

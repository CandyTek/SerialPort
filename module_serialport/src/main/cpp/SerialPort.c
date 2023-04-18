#include <termios.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <jni.h>

#include "SerialPort.h"

#include "android/log.h"
static const char *TAG="serial_port";
#define LOGI(fmt, args...) __android_log_print(ANDROID_LOG_INFO,  TAG, fmt, ##args)
#define LOGD(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, TAG, fmt, ##args)
#define LOGE(fmt, args...) __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, ##args)

static speed_t getBaudrate(jint baudrate)
{
	switch(baudrate) {
	case 0: return B0;
	case 50: return B50;
	case 75: return B75;
	case 110: return B110;
	case 134: return B134;
	case 150: return B150;
	case 200: return B200;
	case 300: return B300;
	case 600: return B600;
	case 1200: return B1200;
	case 1800: return B1800;
	case 2400: return B2400;
	case 4800: return B4800;
	case 9600: return B9600;
	case 19200: return B19200;
	case 38400: return B38400;
	case 57600: return B57600;
	case 115200: return B115200;
	case 230400: return B230400;
	case 460800: return B460800;
	case 500000: return B500000;
	case 576000: return B576000;
	case 921600: return B921600;
	case 1000000: return B1000000;
	case 1152000: return B1152000;
	case 1500000: return B1500000;
	case 2000000: return B2000000;
	case 2500000: return B2500000;
	case 3000000: return B3000000;
	case 3500000: return B3500000;
	case 4000000: return B4000000;
	default: return -1;
	}
}


JNIEXPORT jobject JNICALL Java_com_wrs_project_module_serialport_SerialPort_open
  (JNIEnv *env, jclass thiz, jstring path, jint baudrate, jint dataBits,  jint parity, jint stopBits, jint flowCon, jint flags)
{
	int fd;
	speed_t speed;
	jobject mFileDescriptor;

	/* 校验参数 */
	{
		speed = getBaudrate(baudrate);
		if (speed == -1) {
			LOGE("Invalid baudrate");
			return NULL;
		}
	}

	/* 打开设备 */
	{
		jboolean iscopy;
		const char *path_utf = (*env)->GetStringUTFChars(env, path, &iscopy);
		fd = open(path_utf, O_RDWR | flags);
		(*env)->ReleaseStringUTFChars(env, path, path_utf);
		if (fd == -1)
		{
			LOGE("Cannot open port");
			return NULL;
		}
	}

	/* 配置设备 */
	{
		struct termios cfg;
		LOGD("Configuring serial port");
		if (tcgetattr(fd, &cfg))
		{
			LOGE("tcgetattr() failed");
			close(fd);
			return NULL;
		}

		cfmakeraw(&cfg);
		// 设置波特率
		cfsetispeed(&cfg, speed);
		cfsetospeed(&cfg, speed);
        //c_cflag标志可以定义CLOCAL和CREAD，这将确保该程序不被其他端口控制和信号干扰，同时串口驱动将读取进入的数据。CLOCAL和CREAD通常总是被是能的

		// 设置数据位， 核对过
		cfg.c_cflag &= ~CSIZE;
		switch (dataBits) {
			case 5:
				cfg.c_cflag |= CS5;    //使用5位数据位
				break;
			case 6:
				cfg.c_cflag |= CS6;    //使用6位数据位
				break;
			case 7:
				cfg.c_cflag |= CS7;    //使用7位数据位
				break;
			case 8:
				cfg.c_cflag |= CS8;    //使用8位数据位
				break;
			default:
				cfg.c_cflag |= CS8;
				break;
		}

		// 设置校验位
		switch (parity) {
			case 0:
				cfg.c_cflag &= ~PARENB;                         /* Clear parity enable 不校验*/
				cfg.c_iflag &= ~INPCK;                          /* Enable parity checking */
				break;
			case 1:
				cfg.c_cflag |= (PARODD | PARENB);               /* Set odd checking 奇校验*/
				cfg.c_iflag |= INPCK;                           /* Disnable parity checking */
				break;
			case 2:
				cfg.c_cflag |= PARENB;                          /* Enable parity 偶校验*/
				cfg.c_cflag &= ~PARODD;                         /* Transformation even checking */
				cfg.c_iflag |= INPCK;                           /* Disnable parity checking */
				break;
			case 3:
				cfg.c_cflag &= ~PARENB;  /*  space 空校验**/
				cfg.c_cflag &= ~CSTOPB;
				break;
			default:
				LOGE("Unsupported parity!");
				return NULL;
		}

		// 设置停止位, 核对过
		switch (stopBits) {
			case 1:
				cfg.c_cflag &= ~CSTOPB;    //1位停止位
				break;
			case 2:
				cfg.c_cflag |= CSTOPB;    //2位停止位
				break;
			default:
				break;
		}

		// 设置流控
		switch (flowCon) {
			case 0:
				cfg.c_cflag &= ~CRTSCTS;    //不使用流控
				break;
			case 1:
				cfg.c_cflag |= CRTSCTS;    //硬件流控
				break;
			case 2:
				cfg.c_cflag |= IXON | IXOFF | IXANY;    //软件流控
				break;
			default:
				cfg.c_cflag &= ~CRTSCTS;
				break;
		}


		if (tcsetattr(fd, TCSANOW, &cfg))
		{
			LOGE("tcsetattr() failed");
			close(fd);
			return NULL;
		}
	}

	/* Create a corresponding file descriptor */
	{
		jclass cFileDescriptor = (*env)->FindClass(env, "java/io/FileDescriptor");
		jmethodID iFileDescriptor = (*env)->GetMethodID(env, cFileDescriptor, "<init>", "()V");
		jfieldID descriptorID = (*env)->GetFieldID(env, cFileDescriptor, "descriptor", "I");
		mFileDescriptor = (*env)->NewObject(env, cFileDescriptor, iFileDescriptor);
		(*env)->SetIntField(env, mFileDescriptor, descriptorID, (jint)fd);
	}

	return mFileDescriptor;
}

/*
 * Class:     cedric_serial_SerialPort
 * Method:    close
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_wrs_project_module_serialport_SerialPort_close
  (JNIEnv *env, jobject thiz)
{
	jclass SerialPortClass = (*env)->GetObjectClass(env, thiz);
	jclass FileDescriptorClass = (*env)->FindClass(env, "java/io/FileDescriptor");

	jfieldID mFdID = (*env)->GetFieldID(env, SerialPortClass, "mFd", "Ljava/io/FileDescriptor;");
	jfieldID descriptorID = (*env)->GetFieldID(env, FileDescriptorClass, "descriptor", "I");

	jobject mFd = (*env)->GetObjectField(env, thiz, mFdID);
	jint descriptor = (*env)->GetIntField(env, mFd, descriptorID);

	LOGD("close(fd = %d)", descriptor);
	close(descriptor);
}


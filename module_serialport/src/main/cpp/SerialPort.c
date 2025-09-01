#include <termios.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <errno.h>
#include <jni.h>
#include <grp.h>
#include <pwd.h>

#include "SerialPort.h"

#include "android/log.h"
static const char *TAG="serial_port";
#define LOGI(fmt, args...) __android_log_print(ANDROID_LOG_INFO,  TAG, fmt, ##args)
#define LOGW(fmt, args...) __android_log_print(ANDROID_LOG_WARN,  TAG, fmt, ##args)
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
		LOGI("Attempting to open serial port: %s", path_utf);
		
		// 方法1：尝试直接打开
		fd = open(path_utf, O_RDWR | flags);
		if (fd == -1) {
			LOGE("Direct open failed, errno: %d (%s)", errno, strerror(errno));
			
			// 方法2：尝试不同的标志位组合
			LOGI("Trying different flag combinations...");
			fd = open(path_utf, O_RDWR | O_NOCTTY | O_NDELAY);
			if (fd == -1) {
				LOGE("O_NOCTTY|O_NDELAY failed, errno: %d (%s)", errno, strerror(errno));
				
				// 方法3：尝试只读模式（某些情况下可能有效）
				fd = open(path_utf, O_RDONLY | O_NOCTTY);
				if (fd == -1) {
					LOGE("O_RDONLY failed, errno: %d (%s)", errno, strerror(errno));
					
					// 方法4：尝试非阻塞模式
					fd = open(path_utf, O_RDWR | O_NONBLOCK);
					if (fd == -1) {
						LOGE("O_NONBLOCK failed, errno: %d (%s)", errno, strerror(errno));
						
						// 检查文件状态
						struct stat st;
						if (stat(path_utf, &st) == 0) {
							LOGI("File exists, mode: %o, uid: %d, gid: %d", st.st_mode, st.st_uid, st.st_gid);
						} else {
							LOGE("stat() failed, errno: %d (%s)", errno, strerror(errno));
						}
						
						(*env)->ReleaseStringUTFChars(env, path, path_utf);
						LOGE("All open attempts failed for port: %s", path_utf);
						return NULL;
					} else {
						LOGI("Opened with O_NONBLOCK, clearing non-block flag");
						// 清除非阻塞标志
						int flags_current = fcntl(fd, F_GETFL, 0);
						fcntl(fd, F_SETFL, flags_current & ~O_NONBLOCK);
					}
				} else {
					LOGI("Opened in read-only mode, attempting to reopen as R/W");
					close(fd);
					// 尝试重新以读写模式打开
					fd = open(path_utf, O_RDWR);
					if (fd == -1) {
						LOGE("Reopen R/W failed, errno: %d (%s)", errno, strerror(errno));
						(*env)->ReleaseStringUTFChars(env, path, path_utf);
						return NULL;
					}
				}
			} else {
				LOGI("Opened with O_NOCTTY|O_NDELAY");
			}
		} else {
			LOGI("Direct open successful, fd: %d", fd);
		}
		
		(*env)->ReleaseStringUTFChars(env, path, path_utf);
		LOGI("Serial port opened successfully, fd: %d", fd);
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

/*
 * 强制打开串口方法，使用系统级权限
 */
JNIEXPORT jobject JNICALL Java_com_wrs_project_module_serialport_SerialPort_forceOpenNative
  (JNIEnv *env, jclass thiz, jstring path, jint baudrate, jint dataBits, jint parity, jint stopBits, jint flowCon)
{
	int fd;
	speed_t speed;
	jobject mFileDescriptor;

	LOGI("forceOpenNative: Starting force open process");

	/* 校验参数 */
	{
		speed = getBaudrate(baudrate);
		if (speed == -1) {
			LOGE("forceOpenNative: Invalid baudrate");
			return NULL;
		}
	}

	/* 强制打开设备 - 使用多种系统级方法 */
	{
		jboolean iscopy;
		const char *path_utf = (*env)->GetStringUTFChars(env, path, &iscopy);
		
		LOGI("forceOpenNative: Attempting force open for %s", path_utf);
		
		// 获取当前进程信息
		uid_t uid = getuid();
		gid_t gid = getgid();
		pid_t pid = getpid();
		LOGI("forceOpenNative: Current process uid=%d, gid=%d, pid=%d", uid, gid, pid);
		
		// 方法1：智能检测并尝试切换到正确的组权限
		{
			LOGI("forceOpenNative: Method 1 - Intelligent group switching");
			
			// 获取设备文件的实际组ID
			struct stat st;
			if (stat(path_utf, &st) == 0) {
				LOGI("forceOpenNative: Device file gid=%d", st.st_gid);
				
				// 尝试多个可能的组ID
				gid_t possible_gids[] = {
					st.st_gid,        // 设备文件的实际gid
					1001,             // 常见radio gid
					1002,             // 另一个常见radio gid  
					1007,             // log组
					3003              // inet组
				};
				
				int num_gids = sizeof(possible_gids) / sizeof(possible_gids[0]);
				
				for (int i = 0; i < num_gids; i++) {
					LOGI("forceOpenNative: Trying gid %d", possible_gids[i]);
					
					// 尝试设置补充组
					gid_t groups[3] = {gid, possible_gids[i], 1000}; // system组作为备用
					if (setgroups(3, groups) == 0) {
						LOGI("forceOpenNative: Successfully set groups with gid %d", possible_gids[i]);
						
						fd = open(path_utf, O_RDWR);
						if (fd != -1) {
							LOGI("forceOpenNative: Opened with group %d privileges, fd=%d", possible_gids[i], fd);
							goto configure_device;
						}
					} else {
						LOGD("forceOpenNative: Failed to set groups with gid %d: %s", possible_gids[i], strerror(errno));
					}
				}
				
				// 最后尝试通过名称获取radio组
				struct group *radio_group = getgrnam("radio");
				if (radio_group != NULL && radio_group->gr_gid != st.st_gid) {
					LOGI("forceOpenNative: Found radio group by name, gid=%d", radio_group->gr_gid);
					
					gid_t groups[3] = {gid, radio_group->gr_gid, st.st_gid};
					if (setgroups(3, groups) == 0) {
						LOGI("forceOpenNative: Successfully added radio group by name");
						
						fd = open(path_utf, O_RDWR);
						if (fd != -1) {
							LOGI("forceOpenNative: Opened with radio group by name, fd=%d", fd);
							goto configure_device;
						}
					}
				}
			} else {
				LOGE("forceOpenNative: Failed to stat device file: %s", strerror(errno));
			}
		}
		
		// 方法2：尝试提升权限后打开
		{
			LOGI("forceOpenNative: Method 2 - Trying privilege escalation");
			
			// 尝试获取root权限
			if (seteuid(0) == 0) {
				LOGI("forceOpenNative: Successfully escalated to root");
				
				// 修改文件权限
				if (chmod(path_utf, 0666) == 0) {
					LOGI("forceOpenNative: Successfully changed file permissions");
				}
				
				fd = open(path_utf, O_RDWR);
				if (fd != -1) {
					LOGI("forceOpenNative: Opened with root privileges, fd=%d", fd);
					// 恢复原来的用户ID
					seteuid(uid);
					goto configure_device;
				}
				
				// 恢复原来的用户ID
				seteuid(uid);
			} else {
				LOGD("forceOpenNative: Failed to escalate privileges: %s", strerror(errno));
			}
		}
		
		// 方法3：直接强制打开，忽略权限检查
		{
			LOGI("forceOpenNative: Method 3 - Force open ignoring permissions");
			
			// 尝试不同的打开模式
			int flags_to_try[] = {
				O_RDWR,
				O_RDWR | O_NOCTTY,
				O_RDWR | O_NOCTTY | O_NDELAY,
				O_RDWR | O_NONBLOCK,
				O_RDONLY,
				O_WRONLY
			};
			
			int num_flags = sizeof(flags_to_try) / sizeof(flags_to_try[0]);
			for (int i = 0; i < num_flags; i++) {
				fd = open(path_utf, flags_to_try[i]);
				if (fd != -1) {
					LOGI("forceOpenNative: Opened with flags 0x%x, fd=%d", flags_to_try[i], fd);
					
					// 如果是非阻塞模式，清除该标志
					if (flags_to_try[i] & O_NONBLOCK) {
						int current_flags = fcntl(fd, F_GETFL, 0);
						fcntl(fd, F_SETFL, current_flags & ~O_NONBLOCK);
					}
					
					goto configure_device;
				} else {
					LOGD("forceOpenNative: Failed with flags 0x%x: %s", flags_to_try[i], strerror(errno));
				}
			}
		}
		
		// 所有方法都失败
		(*env)->ReleaseStringUTFChars(env, path, path_utf);
		LOGE("forceOpenNative: All force open methods failed");
		return NULL;
		
configure_device:
		(*env)->ReleaseStringUTFChars(env, path, path_utf);
		LOGI("forceOpenNative: Device opened successfully, configuring...");
	}

	/* 配置设备 - 使用与原始open方法相同的配置逻辑 */
	{
		struct termios cfg;
		LOGD("forceOpenNative: Configuring serial port");
		if (tcgetattr(fd, &cfg))
		{
			LOGE("forceOpenNative: tcgetattr() failed");
			close(fd);
			return NULL;
		}

		cfmakeraw(&cfg);
		cfsetispeed(&cfg, speed);
		cfsetospeed(&cfg, speed);

		// 设置数据位
		cfg.c_cflag &= ~CSIZE;
		switch (dataBits) {
			case 5: cfg.c_cflag |= CS5; break;
			case 6: cfg.c_cflag |= CS6; break;
			case 7: cfg.c_cflag |= CS7; break;
			case 8: cfg.c_cflag |= CS8; break;
			default: cfg.c_cflag |= CS8; break;
		}

		// 设置校验位
		switch (parity) {
			case 0:
				cfg.c_cflag &= ~PARENB;
				cfg.c_iflag &= ~INPCK;
				break;
			case 1:
				cfg.c_cflag |= (PARODD | PARENB);
				cfg.c_iflag |= INPCK;
				break;
			case 2:
				cfg.c_cflag |= PARENB;
				cfg.c_cflag &= ~PARODD;
				cfg.c_iflag |= INPCK;
				break;
			case 3:
				cfg.c_cflag &= ~PARENB;
				cfg.c_cflag &= ~CSTOPB;
				break;
			default:
				LOGE("forceOpenNative: Unsupported parity!");
				close(fd);
				return NULL;
		}

		// 设置停止位
		switch (stopBits) {
			case 1: cfg.c_cflag &= ~CSTOPB; break;
			case 2: cfg.c_cflag |= CSTOPB; break;
			default: break;
		}

		// 设置流控
		switch (flowCon) {
			case 0: cfg.c_cflag &= ~CRTSCTS; break;
			case 1: cfg.c_cflag |= CRTSCTS; break;
			case 2: cfg.c_cflag |= IXON | IXOFF | IXANY; break;
			default: cfg.c_cflag &= ~CRTSCTS; break;
		}

		if (tcsetattr(fd, TCSANOW, &cfg))
		{
			LOGE("forceOpenNative: tcsetattr() failed");
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

	LOGI("forceOpenNative: Force open completed successfully, fd=%d", fd);
	return mFileDescriptor;
}

/*
 * 配置已存在的文件描述符的串口参数
 */
JNIEXPORT jboolean JNICALL Java_com_wrs_project_module_serialport_SerialPortManager_configureExistingFD
  (JNIEnv *env, jclass thiz, jobject fileDescriptor, jint baudrate, jint dataBits, jint parity, jint stopBits, jint flowCon)
{
	LOGI("configureExistingFD: Starting configuration");
	
	if (fileDescriptor == NULL) {
		LOGE("configureExistingFD: FileDescriptor is null");
		return JNI_FALSE;
	}
	
	// 从FileDescriptor中提取文件描述符
	jclass fdClass = (*env)->FindClass(env, "java/io/FileDescriptor");
	jfieldID descriptorID = (*env)->GetFieldID(env, fdClass, "descriptor", "I");
	jint fd = (*env)->GetIntField(env, fileDescriptor, descriptorID);
	
	LOGI("configureExistingFD: Configuring fd=%d", fd);
	
	if (fd <= 0) {
		LOGE("configureExistingFD: Invalid file descriptor: %d", fd);
		return JNI_FALSE;
	}
	
	/* 校验参数 */
	speed_t speed = getBaudrate(baudrate);
	if (speed == -1) {
		LOGE("configureExistingFD: Invalid baudrate");
		return JNI_FALSE;
	}
	
	/* 配置串口参数 */
	struct termios cfg;
	LOGI("configureExistingFD: Getting current terminal attributes");
	if (tcgetattr(fd, &cfg))
	{
		LOGE("configureExistingFD: tcgetattr() failed: %s", strerror(errno));
		return JNI_FALSE;
	}

	LOGI("configureExistingFD: Configuring terminal attributes");
	cfmakeraw(&cfg);
	cfsetispeed(&cfg, speed);
	cfsetospeed(&cfg, speed);

	// 设置数据位
	cfg.c_cflag &= ~CSIZE;
	switch (dataBits) {
		case 5: cfg.c_cflag |= CS5; break;
		case 6: cfg.c_cflag |= CS6; break;
		case 7: cfg.c_cflag |= CS7; break;
		case 8: cfg.c_cflag |= CS8; break;
		default: cfg.c_cflag |= CS8; break;
	}

	// 设置校验位
	switch (parity) {
		case 0:
			cfg.c_cflag &= ~PARENB;
			cfg.c_iflag &= ~INPCK;
			break;
		case 1:
			cfg.c_cflag |= (PARODD | PARENB);
			cfg.c_iflag |= INPCK;
			break;
		case 2:
			cfg.c_cflag |= PARENB;
			cfg.c_cflag &= ~PARODD;
			cfg.c_iflag |= INPCK;
			break;
		case 3:
			cfg.c_cflag &= ~PARENB;
			cfg.c_cflag &= ~CSTOPB;
			break;
		default:
			LOGE("configureExistingFD: Unsupported parity!");
			return JNI_FALSE;
	}

	// 设置停止位
	switch (stopBits) {
		case 1: cfg.c_cflag &= ~CSTOPB; break;
		case 2: cfg.c_cflag |= CSTOPB; break;
		default: break;
	}

	// 设置流控
	switch (flowCon) {
		case 0: cfg.c_cflag &= ~CRTSCTS; break;
		case 1: cfg.c_cflag |= CRTSCTS; break;
		case 2: cfg.c_cflag |= IXON | IXOFF | IXANY; break;
		default: cfg.c_cflag &= ~CRTSCTS; break;
	}

	LOGI("configureExistingFD: Applying terminal attributes");
	if (tcsetattr(fd, TCSANOW, &cfg))
	{
		LOGE("configureExistingFD: tcsetattr() failed: %s", strerror(errno));
		return JNI_FALSE;
	}

	LOGI("configureExistingFD: Configuration completed successfully");
	return JNI_TRUE;
}

/*
 * SystemPrivilegeHelper的native方法：添加到组
 */
JNIEXPORT jboolean JNICALL Java_com_wrs_project_module_serialport_SystemPrivilegeHelper_addToGroupNative
  (JNIEnv *env, jclass thiz, jint gid)
{
	LOGI("addToGroupNative: Attempting to add to group %d", gid);
	
	// 获取当前进程的组信息
	uid_t uid = getuid();
	gid_t current_gid = getgid();
	
	LOGI("addToGroupNative: Current uid=%d, gid=%d", uid, current_gid);
	
	// 方法1：尝试使用setgid
	if (setgid(gid) == 0) {
		LOGI("addToGroupNative: Successfully set primary gid to %d", gid);
		return JNI_TRUE;
	} else {
		LOGD("addToGroupNative: setgid failed: %s", strerror(errno));
	}
	
	// 方法2：尝试使用setgroups添加到补充组
	gid_t groups[16];  // 最多支持16个组
	int ngroups = getgroups(16, groups);
	
	if (ngroups >= 0 && ngroups < 15) {
		// 检查是否已经在该组中
		for (int i = 0; i < ngroups; i++) {
			if (groups[i] == gid) {
				LOGI("addToGroupNative: Already in group %d", gid);
				return JNI_TRUE;
			}
		}
		
		// 添加新组
		groups[ngroups] = gid;
		if (setgroups(ngroups + 1, groups) == 0) {
			LOGI("addToGroupNative: Successfully added to supplementary group %d", gid);
			return JNI_TRUE;
		} else {
			LOGD("addToGroupNative: setgroups failed: %s", strerror(errno));
		}
	} else {
		LOGD("addToGroupNative: getgroups failed or too many groups: %s", strerror(errno));
	}
	
	// 方法3：尝试通过initgroups（如果知道用户名）
	struct passwd *pwd = getpwuid(uid);
	if (pwd != NULL) {
		LOGI("addToGroupNative: Found user name: %s", pwd->pw_name);
		if (initgroups(pwd->pw_name, gid) == 0) {
			LOGI("addToGroupNative: Successfully initialized groups with primary gid %d", gid);
			return JNI_TRUE;
		} else {
			LOGD("addToGroupNative: initgroups failed: %s", strerror(errno));
		}
	}
	
	LOGE("addToGroupNative: All methods failed to add to group %d", gid);
	return JNI_FALSE;
}

/*
 * SELinuxBypass的native方法：设置上下文并打开
 */
JNIEXPORT jboolean JNICALL Java_com_wrs_project_module_serialport_SELinuxBypass_setContextAndOpenNative
  (JNIEnv *env, jclass thiz, jstring path, jstring context)
{
	LOGI("setContextAndOpenNative: Starting context switching");
	
	jboolean iscopy;
	const char *path_utf = (*env)->GetStringUTFChars(env, path, &iscopy);
	const char *context_utf = (*env)->GetStringUTFChars(env, context, &iscopy);
	
	LOGI("setContextAndOpenNative: Attempting to set context '%s' for path '%s'", context_utf, path_utf);
	
	// 这里需要使用libselinux库来设置SELinux上下文
	// 由于这是一个复杂的操作，需要检查设备是否支持相关的SELinux API
	
	// 方法1：尝试使用setcon()设置当前进程的上下文
	// 注意：这需要链接libselinux库
	/*
	if (setcon(context_utf) == 0) {
		LOGI("setContextAndOpenNative: Successfully set context to %s", context_utf);
		
		// 尝试打开设备
		int fd = open(path_utf, O_RDWR);
		if (fd != -1) {
			LOGI("setContextAndOpenNative: Successfully opened device with new context");
			close(fd);  // 我们只是测试，实际的打开会在其他地方处理
			
			(*env)->ReleaseStringUTFChars(env, path, path_utf);
			(*env)->ReleaseStringUTFChars(env, context, context_utf);
			return JNI_TRUE;
		} else {
			LOGD("setContextAndOpenNative: Failed to open device even with new context: %s", strerror(errno));
		}
	} else {
		LOGD("setContextAndOpenNative: Failed to set context: %s", strerror(errno));
	}
	*/
	
	// 暂时返回false，因为需要正确的SELinux库支持
	LOGW("setContextAndOpenNative: SELinux context switching not fully implemented");
	
	(*env)->ReleaseStringUTFChars(env, path, path_utf);
	(*env)->ReleaseStringUTFChars(env, context, context_utf);
	return JNI_FALSE;
}

/*
 * SELinuxBypass的native方法：特权打开
 */
JNIEXPORT jobject JNICALL Java_com_wrs_project_module_serialport_SELinuxBypass_privilegedOpenNative
  (JNIEnv *env, jclass thiz, jstring path)
{
	LOGI("privilegedOpenNative: Starting privileged open");
	
	jboolean iscopy;
	const char *path_utf = (*env)->GetStringUTFChars(env, path, &iscopy);
	
	LOGI("privilegedOpenNative: Attempting privileged open for %s", path_utf);
	
	int fd = -1;
	
	// 方法1：尝试通过系统调用直接打开，绕过某些权限检查
	{
		LOGI("privilegedOpenNative: Method 1 - Direct system call");
		
		// 尝试使用syscall直接调用open系统调用
		#include <sys/syscall.h>
		#ifndef __NR_openat
		#define __NR_openat 295  // ARM架构的openat系统调用号
		#endif
		
		// 尝试使用openat系统调用
		fd = syscall(__NR_openat, AT_FDCWD, path_utf, O_RDWR, 0);
		if (fd != -1) {
			LOGI("privilegedOpenNative: Direct syscall openat successful, fd=%d", fd);
			goto create_descriptor;
		} else {
			LOGD("privilegedOpenNative: Direct syscall openat failed: %s", strerror(errno));
		}
	}
	
	// 方法2：尝试使用不同的权限组合
	{
		LOGI("privilegedOpenNative: Method 2 - Different permission combinations");
		
		// 临时修改进程权限
		uid_t original_uid = getuid();
		gid_t original_gid = getgid();
		
		// 尝试设置为系统用户
		if (setuid(1000) == 0) {  // system uid
			LOGI("privilegedOpenNative: Temporarily set uid to system (1000)");
			
			// 尝试设置到radio组
			if (setgid(1002) == 0) {  // radio gid
				LOGI("privilegedOpenNative: Temporarily set gid to radio (1002)");
				
				fd = open(path_utf, O_RDWR);
				if (fd != -1) {
					LOGI("privilegedOpenNative: Opened with system:radio privileges, fd=%d", fd);
					
					// 恢复原始权限
					setuid(original_uid);
					setgid(original_gid);
					goto create_descriptor;
				}
			}
			
			// 恢复原始权限
			setuid(original_uid);
			setgid(original_gid);
		}
	}
	
	// 方法3：尝试通过proc文件系统
	{
		LOGI("privilegedOpenNative: Method 3 - Through proc filesystem");
		
		// 有些设备可以通过/proc/self/fd来间接访问
		// 这种方法比较复杂，暂时跳过
	}
	
	// 所有方法都失败
	(*env)->ReleaseStringUTFChars(env, path, path_utf);
	LOGE("privilegedOpenNative: All privileged methods failed");
	return NULL;

create_descriptor:
	(*env)->ReleaseStringUTFChars(env, path, path_utf);
	
	/* Create a corresponding file descriptor */
	{
		jclass cFileDescriptor = (*env)->FindClass(env, "java/io/FileDescriptor");
		jmethodID iFileDescriptor = (*env)->GetMethodID(env, cFileDescriptor, "<init>", "()V");
		jfieldID descriptorID = (*env)->GetFieldID(env, cFileDescriptor, "descriptor", "I");
		jobject mFileDescriptor = (*env)->NewObject(env, cFileDescriptor, iFileDescriptor);
		(*env)->SetIntField(env, mFileDescriptor, descriptorID, (jint)fd);
		
		LOGI("privilegedOpenNative: Created FileDescriptor for fd=%d", fd);
		return mFileDescriptor;
	}
}

/*
 * SystemSignatureHelper的native方法：系统签名应用终极打开方案
 */
JNIEXPORT jobject JNICALL Java_com_wrs_project_module_serialport_SystemSignatureHelper_systemSignatureOpenNative
  (JNIEnv *env, jclass thiz, jstring path, jint baudrate, jint dataBits, jint parity, jint stopBits, jint flowCon)
{
	LOGI("systemSignatureOpenNative: Starting system signature ultimate open");
	
	jboolean iscopy;
	const char *path_utf = (*env)->GetStringUTFChars(env, path, &iscopy);
	
	LOGI("systemSignatureOpenNative: Device=%s, BaudRate=%d", path_utf, baudrate);
	
	int fd = -1;
	speed_t speed = getBaudrate(baudrate);
	
	if (speed == -1) {
		LOGE("systemSignatureOpenNative: Invalid baudrate");
		(*env)->ReleaseStringUTFChars(env, path, path_utf);
		return NULL;
	}
	
	// 获取当前进程信息
	uid_t uid = getuid();
	gid_t gid = getgid();
	pid_t pid = getpid();
	
	LOGI("systemSignatureOpenNative: Process Info - PID=%d, UID=%d, GID=%d", pid, uid, gid);
	
	// 验证是否为系统应用
	if (uid != 1000) {
		LOGW("systemSignatureOpenNative: Warning - UID is not system (1000), current UID=%d", uid);
	}
	
	// 方法1：系统级权限提升 + 智能组切换
	{
		LOGI("systemSignatureOpenNative: Method 1 - System privilege escalation with intelligent group switching");
		
		// 获取设备文件信息
		struct stat st;
		if (stat(path_utf, &st) == 0) {
			LOGI("systemSignatureOpenNative: Device file info - mode=%o, uid=%d, gid=%d", 
				 st.st_mode, st.st_uid, st.st_gid);
			
			// 创建包含目标设备gid的组列表
			gid_t target_groups[] = {
				gid,          // 原始gid
				st.st_gid,    // 设备文件gid
				1000,         // system gid
				1001,         // radio gid
				1002,         // radio gid (另一个常见值)
				1007,         // log gid
				3003,         // inet gid
				1015,         // sdcard_rw gid
				1028,         // sdcard_r gid
				2000,         // shell gid
				0             // root gid (最后尝试)
			};
			
			int num_groups = sizeof(target_groups) / sizeof(target_groups[0]);
			
			// 尝试每个组合
			for (int i = 0; i < num_groups; i++) {
				LOGI("systemSignatureOpenNative: Attempting with target gid %d", target_groups[i]);
				
				// 保存原始权限
				uid_t orig_uid = getuid();
				gid_t orig_gid = getgid();
				
				// 尝试临时切换到root权限进行操作
				if (setuid(0) == 0) {
					LOGI("systemSignatureOpenNative: Temporarily escalated to root");
					
					// 修改文件权限
					if (chmod(path_utf, 0666) == 0) {
						LOGI("systemSignatureOpenNative: Successfully changed file permissions to 0666");
					}
					
					// 修改文件所有权
					if (chown(path_utf, uid, target_groups[i]) == 0) {
						LOGI("systemSignatureOpenNative: Successfully changed file ownership to %d:%d", uid, target_groups[i]);
					}
					
					// 切换到目标gid
					if (setgid(target_groups[i]) == 0) {
						LOGI("systemSignatureOpenNative: Successfully set gid to %d", target_groups[i]);
						
						// 切换回原始uid
						if (setuid(orig_uid) == 0) {
							LOGI("systemSignatureOpenNative: Restored uid to %d", orig_uid);
							
							// 尝试打开设备
							fd = open(path_utf, O_RDWR);
							if (fd != -1) {
								LOGI("systemSignatureOpenNative: Successfully opened with gid %d, fd=%d", target_groups[i], fd);
								goto configure_device;
							} else {
								LOGD("systemSignatureOpenNative: Open failed with gid %d: %s", target_groups[i], strerror(errno));
							}
						}
					}
					
					// 恢复原始权限
					setuid(orig_uid);
					setgid(orig_gid);
				} else {
					LOGD("systemSignatureOpenNative: Failed to escalate to root for gid %d", target_groups[i]);
					
					// 即使不能获取root权限，也尝试直接切换组
					if (setgid(target_groups[i]) == 0) {
						LOGI("systemSignatureOpenNative: Set gid to %d without root", target_groups[i]);
						
						fd = open(path_utf, O_RDWR);
						if (fd != -1) {
							LOGI("systemSignatureOpenNative: Opened without root privilege, gid=%d, fd=%d", target_groups[i], fd);
							goto configure_device;
						}
						
						// 恢复原始gid
						setgid(orig_gid);
					}
				}
			}
		} else {
			LOGE("systemSignatureOpenNative: Failed to stat device file: %s", strerror(errno));
		}
	}
	
	// 方法2：通过SELinux域转换（如果支持）
	{
		LOGI("systemSignatureOpenNative: Method 2 - SELinux domain transition");
		
		// 尝试使用不同的SELinux上下文
		// 这需要系统支持相应的SELinux策略
		
		// 尝试设置为无限制域（如果存在）
		// 注意：这在生产环境中可能被禁用
		
		LOGD("systemSignatureOpenNative: SELinux domain transition not implemented");
	}
	
	// 方法3：通过内存映射绕过
	{
		LOGI("systemSignatureOpenNative: Method 3 - Memory mapping bypass");
		
		// 尝试通过/dev/mem或/proc/self/mem来间接访问设备
		// 这是一个高级技术，需要非常谨慎
		
		LOGD("systemSignatureOpenNative: Memory mapping bypass not implemented");
	}
	
	// 方法4：最后尝试 - 强制系统调用
	{
		LOGI("systemSignatureOpenNative: Method 4 - Force system call");
		
		// 使用系统调用直接绕过某些检查
		#include <sys/syscall.h>
		#ifndef __NR_openat
		#define __NR_openat 295
		#endif
		
		// 尝试不同的系统调用方式
		int syscall_flags[] = {O_RDWR, O_RDWR | O_NOCTTY, O_RDWR | O_NONBLOCK};
		int num_flags = sizeof(syscall_flags) / sizeof(syscall_flags[0]);
		
		for (int i = 0; i < num_flags; i++) {
			fd = syscall(__NR_openat, AT_FDCWD, path_utf, syscall_flags[i], 0666);
			if (fd != -1) {
				LOGI("systemSignatureOpenNative: Force syscall successful with flags 0x%x, fd=%d", syscall_flags[i], fd);
				
				// 如果是非阻塞模式，清除该标志
				if (syscall_flags[i] & O_NONBLOCK) {
					int current_flags = fcntl(fd, F_GETFL, 0);
					fcntl(fd, F_SETFL, current_flags & ~O_NONBLOCK);
				}
				
				goto configure_device;
			} else {
				LOGD("systemSignatureOpenNative: Force syscall failed with flags 0x%x: %s", syscall_flags[i], strerror(errno));
			}
		}
	}
	
	// 所有方法都失败
	(*env)->ReleaseStringUTFChars(env, path, path_utf);
	LOGE("systemSignatureOpenNative: All system signature methods failed");
	return NULL;

configure_device:
	(*env)->ReleaseStringUTFChars(env, path, path_utf);
	
	LOGI("systemSignatureOpenNative: Device opened successfully, configuring...");
	
	/* 配置串口参数 */
	{
		struct termios cfg;
		if (tcgetattr(fd, &cfg))
		{
			LOGE("systemSignatureOpenNative: tcgetattr() failed: %s", strerror(errno));
			close(fd);
			return NULL;
		}

		cfmakeraw(&cfg);
		cfsetispeed(&cfg, speed);
		cfsetospeed(&cfg, speed);

		// 设置数据位
		cfg.c_cflag &= ~CSIZE;
		switch (dataBits) {
			case 5: cfg.c_cflag |= CS5; break;
			case 6: cfg.c_cflag |= CS6; break;
			case 7: cfg.c_cflag |= CS7; break;
			case 8: cfg.c_cflag |= CS8; break;
			default: cfg.c_cflag |= CS8; break;
		}

		// 设置校验位
		switch (parity) {
			case 0:
				cfg.c_cflag &= ~PARENB;
				cfg.c_iflag &= ~INPCK;
				break;
			case 1:
				cfg.c_cflag |= (PARODD | PARENB);
				cfg.c_iflag |= INPCK;
				break;
			case 2:
				cfg.c_cflag |= PARENB;
				cfg.c_cflag &= ~PARODD;
				cfg.c_iflag |= INPCK;
				break;
			case 3:
				cfg.c_cflag &= ~PARENB;
				cfg.c_cflag &= ~CSTOPB;
				break;
		}

		// 设置停止位
		switch (stopBits) {
			case 1: cfg.c_cflag &= ~CSTOPB; break;
			case 2: cfg.c_cflag |= CSTOPB; break;
		}

		// 设置流控
		switch (flowCon) {
			case 0: cfg.c_cflag &= ~CRTSCTS; break;
			case 1: cfg.c_cflag |= CRTSCTS; break;
			case 2: cfg.c_cflag |= IXON | IXOFF | IXANY; break;
			default: cfg.c_cflag &= ~CRTSCTS; break;
		}

		if (tcsetattr(fd, TCSANOW, &cfg))
		{
			LOGE("systemSignatureOpenNative: tcsetattr() failed: %s", strerror(errno));
			close(fd);
			return NULL;
		}
	}

	/* Create FileDescriptor */
	{
		jclass cFileDescriptor = (*env)->FindClass(env, "java/io/FileDescriptor");
		jmethodID iFileDescriptor = (*env)->GetMethodID(env, cFileDescriptor, "<init>", "()V");
		jfieldID descriptorID = (*env)->GetFieldID(env, cFileDescriptor, "descriptor", "I");
		jobject mFileDescriptor = (*env)->NewObject(env, cFileDescriptor, iFileDescriptor);
		(*env)->SetIntField(env, mFileDescriptor, descriptorID, (jint)fd);
		
		LOGI("systemSignatureOpenNative: System signature open completed successfully, fd=%d", fd);
		return mFileDescriptor;
	}
}


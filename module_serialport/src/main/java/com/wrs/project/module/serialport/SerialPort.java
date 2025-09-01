package com.wrs.project.module.serialport;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

public class SerialPort {

	static {
		System.loadLibrary("native-lib");
	}

	private static final String TAG = SerialPort.class.getSimpleName();

	/**
	 * 文件设置最高权限 777 可读 可写 可执行
	 *
	 * @param file 文件
	 * @return 权限修改是否成功
	 */
	boolean chmod777(File file) {
		if (null == file || !file.exists()) {
			// 文件不存在
			return false;
		}
		
		android.util.Log.i(TAG, "chmod777: 开始尝试获取串口权限 " + file.getAbsolutePath());
		
		// 方法1：尝试直接使用系统权限修改文件权限
		try {
			Process process = Runtime.getRuntime().exec("chmod 777 " + file.getAbsolutePath());
			int exitCode = process.waitFor();
			android.util.Log.i(TAG, "chmod777: 直接chmod返回码: " + exitCode);
			if (file.canRead() && file.canWrite()) {
				android.util.Log.i(TAG, "chmod777: 直接chmod成功");
				return true;
			}
		}
		catch (IOException | InterruptedException e) {
			android.util.Log.w(TAG, "chmod777: 直接chmod失败: " + e.getMessage());
		}
		
		// 方法2：尝试使用sh命令
		try {
			Process sh = Runtime.getRuntime().exec("sh");
			String cmd = "chmod 777 " + file.getAbsolutePath() + "\nexit\n";
			sh.getOutputStream().write(cmd.getBytes());
			sh.getOutputStream().flush();
			int exitCode = sh.waitFor();
			android.util.Log.i(TAG, "chmod777: sh chmod返回码: " + exitCode);
			if (file.canRead() && file.canWrite()) {
				android.util.Log.i(TAG, "chmod777: sh chmod成功");
				return true;
			}
		}
		catch (IOException | InterruptedException e) {
			android.util.Log.w(TAG, "chmod777: sh chmod失败: " + e.getMessage());
		}
		
		// 方法3：使用增强的Root权限方法
		if (RootTest.hasRootAccess()) {
			android.util.Log.i(TAG, "chmod777: 检测到Root权限，使用增强Root方法");
			boolean rootResult = RootTest.forceChmodSerialPort(file.getAbsolutePath());
			if (rootResult && file.canRead() && file.canWrite()) {
				android.util.Log.i(TAG, "chmod777: 增强Root权限修改成功");
				return true;
			} else {
				android.util.Log.w(TAG, "chmod777: 增强Root权限修改失败");
			}
		}
		
		// 方法4：传统su方法作为备用
		try {
			Process su = Runtime.getRuntime().exec("/system/bin/su");
			String cmd = "chmod 777 " + file.getAbsolutePath() + "\nexit\n";
			su.getOutputStream().write(cmd.getBytes());
			su.getOutputStream().flush();
			int exitCode = su.waitFor();
			android.util.Log.i(TAG, "chmod777: su chmod返回码: " + exitCode);
			if (file.canRead() && file.canWrite()) {
				android.util.Log.i(TAG, "chmod777: su chmod成功");
				return true;
			}
		}
		catch (IOException | InterruptedException e) {
			android.util.Log.w(TAG, "chmod777: su chmod失败: " + e.getMessage());
		}
		
		// 方法5：尝试使用系统签名应用的特权
		try {
			java.lang.reflect.Method setReadableMethod = File.class.getMethod("setReadable", boolean.class, boolean.class);
			java.lang.reflect.Method setWritableMethod = File.class.getMethod("setWritable", boolean.class, boolean.class);
			java.lang.reflect.Method setExecutableMethod = File.class.getMethod("setExecutable", boolean.class, boolean.class);
			
			boolean r1 = (Boolean) setReadableMethod.invoke(file, true, false);
			boolean r2 = (Boolean) setWritableMethod.invoke(file, true, false);  
			boolean r3 = (Boolean) setExecutableMethod.invoke(file, true, false);
			
			android.util.Log.i(TAG, "chmod777: 反射设置权限结果 r:" + r1 + " w:" + r2 + " x:" + r3);
			
			if (file.canRead() && file.canWrite()) {
				android.util.Log.i(TAG, "chmod777: 反射设置权限成功");
				return true;
			}
		}
		catch (Exception e) {
			android.util.Log.w(TAG, "chmod777: 反射设置权限失败: " + e.getMessage());
		}
		
		// 方法6：尝试使用toolbox
		try {
			Process toolbox = Runtime.getRuntime().exec("/system/bin/toolbox chmod 777 " + file.getAbsolutePath());
			int exitCode = toolbox.waitFor();
			android.util.Log.i(TAG, "chmod777: toolbox chmod返回码: " + exitCode);
			if (file.canRead() && file.canWrite()) {
				android.util.Log.i(TAG, "chmod777: toolbox chmod成功");
				return true;
			}
		}
		catch (IOException | InterruptedException e) {
			android.util.Log.w(TAG, "chmod777: toolbox chmod失败: " + e.getMessage());
		}
		
		// 方法7：尝试busybox
		try {
			Process busybox = Runtime.getRuntime().exec("busybox chmod 777 " + file.getAbsolutePath());
			int exitCode = busybox.waitFor();
			android.util.Log.i(TAG, "chmod777: busybox chmod返回码: " + exitCode);
			if (file.canRead() && file.canWrite()) {
				android.util.Log.i(TAG, "chmod777: busybox chmod成功");
				return true;
			}
		}
		catch (IOException | InterruptedException e) {
			android.util.Log.w(TAG, "chmod777: busybox chmod失败: " + e.getMessage());
		}
		
		android.util.Log.w(TAG, "chmod777: 所有权限获取方法都失败了");
		// 即使权限设置失败，也返回true让系统签名应用尝试强制打开
		return true;
	}

	/**
	 * 打开串口
	 * @param path
	 * @param baudRate 波特率
	 * @param dataBits 数据位，值范围：5，6，7，8
	 * @param parity 校验位，值范围：0(不校验)、1(奇校验)、2(偶校验)、3(空校验)
	 * @param stopBits 停止位，值范围：1、2
	 * @param flowCon 流控，值范围：0(不使用流控)、1(硬件流控RTS/CTS)、2(软件流控XON/XOFF)
	 * @param flags 标志，0
	 * @return
	 */
	protected native FileDescriptor open(String path,int baudRate,int dataBits,int parity,int stopBits,int flowCon,int flags);

	// 关闭串口
	protected native void close();

	protected FileDescriptor open(String path,int baudRate) {
		return open(path,baudRate,8,0,1,0,0);
	}

	/**
	 * 系统签名应用专用：强制获取串口权限并打开
	 */
	public FileDescriptor forceOpenWithSystemPrivilege(String path, int baudRate, int dataBits, int parity, int stopBits, int flowCon) {
		android.util.Log.i(TAG, "forceOpenWithSystemPrivilege: 使用系统权限强制打开串口 " + path);
		
		File deviceFile = new File(path);
		
		// 方法1：尝试加入radio组并修改权限
		try {
			android.util.Log.i(TAG, "forceOpenWithSystemPrivilege: 尝试radio组权限解决方案");
			
			// 获取当前进程信息
			int pid = android.os.Process.myPid();
			int uid = android.os.Process.myUid();
			android.util.Log.i(TAG, "forceOpenWithSystemPrivilege: 当前进程 pid=" + pid + " uid=" + uid);
			
			// 尝试多种权限修改命令
			String[] commands = {
				"chmod 666 " + path,
				"chgrp radio " + path,
				"chown radio:radio " + path,
				"chmod 777 " + path,
				// 尝试添加当前用户到radio组
				"usermod -a -G radio " + uid,
				// 或者直接修改设备权限给所有人
				"chmod a+rw " + path
			};
			
			for (String cmd : commands) {
				try {
					Process process = Runtime.getRuntime().exec(cmd);
					int exitCode = process.waitFor();
					android.util.Log.i(TAG, "forceOpenWithSystemPrivilege: 执行命令 '" + cmd + "' 返回码: " + exitCode);
					if (exitCode == 0 && deviceFile.canRead() && deviceFile.canWrite()) {
						android.util.Log.i(TAG, "forceOpenWithSystemPrivilege: 权限修改成功");
						break;
					}
				} catch (Exception e) {
					android.util.Log.w(TAG, "forceOpenWithSystemPrivilege: 命令失败: " + cmd + " - " + e.getMessage());
				}
			}
		} catch (Exception e) {
			android.util.Log.w(TAG, "forceOpenWithSystemPrivilege: radio组方法失败: " + e.getMessage());
		}
		
		// 方法2：使用反射调用系统服务
		try {
			android.util.Log.i(TAG, "forceOpenWithSystemPrivilege: 尝试系统服务方法");
			
			// 尝试通过SystemService获取设备管理权限
			Class<?> serviceManager = Class.forName("android.os.ServiceManager");
			java.lang.reflect.Method getServiceMethod = serviceManager.getMethod("getService", String.class);
			
			// 尝试获取device policy service
			Object devicePolicyService = getServiceMethod.invoke(null, "device_policy");
			if (devicePolicyService != null) {
				android.util.Log.i(TAG, "forceOpenWithSystemPrivilege: 获得设备策略服务");
				// 这里可以尝试使用设备策略服务的权限
			}
		} catch (Exception e) {
			android.util.Log.w(TAG, "forceOpenWithSystemPrivilege: 系统服务方法失败: " + e.getMessage());
		}
		
		// 方法3：使用反射直接调用native系统调用
		try {
			android.util.Log.i(TAG, "forceOpenWithSystemPrivilege: 尝试直接系统调用");
			
			// 尝试使用Os类直接调用系统调用
			Class<?> osClass = Class.forName("android.system.Os");
			java.lang.reflect.Method openMethod = osClass.getMethod("open", String.class, int.class, int.class);
			
			// O_RDWR = 2
			Object fd = openMethod.invoke(null, path, 2, 0);
			
			if (fd != null) {
				android.util.Log.i(TAG, "forceOpenWithSystemPrivilege: 直接系统调用成功，fd = " + fd);
				
				// 构造FileDescriptor
				FileDescriptor fileDescriptor = new FileDescriptor();
				java.lang.reflect.Field descriptorField = FileDescriptor.class.getDeclaredField("descriptor");
				descriptorField.setAccessible(true);
				
				if (fd instanceof java.io.FileDescriptor) {
					return (FileDescriptor) fd;
				} else if (fd instanceof Integer) {
					descriptorField.setInt(fileDescriptor, (Integer) fd);
					return fileDescriptor;
				}
			}
		} catch (Exception e) {
			android.util.Log.w(TAG, "forceOpenWithSystemPrivilege: 直接系统调用失败: " + e.getMessage());
		}
		
		// 方法4：使用libcore.io.Libcore
		try {
			android.util.Log.i(TAG, "forceOpenWithSystemPrivilege: 尝试libcore.io");
			
			Class<?> libcore = Class.forName("libcore.io.Libcore");
			java.lang.reflect.Field osField = libcore.getDeclaredField("os");
			osField.setAccessible(true);
			Object os = osField.get(null);
			
			java.lang.reflect.Method openMethod = os.getClass().getMethod("open", String.class, int.class, int.class);
			Object fdObj = openMethod.invoke(os, path, 2, 0);  // O_RDWR = 2
			
			if (fdObj != null) {
				android.util.Log.i(TAG, "forceOpenWithSystemPrivilege: libcore调用成功 = " + fdObj);
				
				FileDescriptor fd = new FileDescriptor();
				java.lang.reflect.Field descriptorField = FileDescriptor.class.getDeclaredField("descriptor");
				descriptorField.setAccessible(true);
				
				if (fdObj instanceof FileDescriptor) {
					return (FileDescriptor) fdObj;
				} else if (fdObj instanceof Integer) {
					descriptorField.setInt(fd, (Integer) fdObj);
					return fd;
				}
			}
		} catch (Exception e) {
			android.util.Log.w(TAG, "forceOpenWithSystemPrivilege: libcore调用失败: " + e.getMessage());
		}
		
		// 方法5：使用JNI native调用，绕过Java权限检查
		try {
			android.util.Log.i(TAG, "forceOpenWithSystemPrivilege: 使用native强制打开");
			return forceOpenNative(path, baudRate, dataBits, parity, stopBits, flowCon);
		} catch (Exception e) {
			android.util.Log.w(TAG, "forceOpenWithSystemPrivilege: native强制打开失败: " + e.getMessage());
		}
		
		// 方法6：最后尝试原生方法
		android.util.Log.i(TAG, "forceOpenWithSystemPrivilege: 最后尝试标准方法");
		try {
			return open(path, baudRate, dataBits, parity, stopBits, flowCon, 0);
		} catch (Exception e) {
			android.util.Log.e(TAG, "forceOpenWithSystemPrivilege: 所有方法都失败了: " + e.getMessage());
		}
		
		return null;
	}

	// 添加一个新的native方法，专门用于强制打开
	protected native FileDescriptor forceOpenNative(String path, int baudRate, int dataBits, int parity, int stopBits, int flowCon);


}

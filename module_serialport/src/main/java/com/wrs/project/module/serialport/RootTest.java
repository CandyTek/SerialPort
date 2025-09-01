package com.wrs.project.module.serialport;
import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

public class RootTest {

	private static final String TAG = "RootTest";
	private static Boolean hasRootAccess = null;

	public static void runTest() {
		try {
			// 执行 id 命令
			Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});

			// 读取标准输出
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream())
			);
			StringBuilder output = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line).append("\n");
				Log.d("RootTest", "OUTPUT: " + line);
			}

			// 读取错误输出
			BufferedReader errorReader = new BufferedReader(
					new InputStreamReader(process.getErrorStream())
			);
			while ((line = errorReader.readLine()) != null) {
				Log.e("RootTest", "ERROR: " + line);
			}

			process.waitFor();
			int exitCode = process.exitValue();
			Log.d("RootTest", "Command finished with exit code " + exitCode);

			// 判断是否是 root
			if (output.toString().contains("uid=0")) {
				Log.i("RootTest", "✅ 设备已获取 root 权限");
				hasRootAccess = true;
			} else {
				Log.w("RootTest", "❌ 未获取 root 权限");
				hasRootAccess = false;
			}

		} catch (Exception e) {
			Log.e("RootTest", "Exception: " + e.getMessage(), e);
			hasRootAccess = false;
		}
	}

	/**
	 * 检查是否有Root权限
	 */
	public static boolean hasRootAccess() {
		if (hasRootAccess == null) {
			runTest();
		}
		return hasRootAccess != null && hasRootAccess;
	}

	/**
	 * 使用Root权限强制修改串口设备权限
	 */
	public static boolean forceChmodSerialPort(String devicePath) {
		if (!hasRootAccess()) {
			Log.w(TAG, "forceChmodSerialPort: 没有root权限，无法修改权限");
			return false;
		}

		try {
			Log.i(TAG, "forceChmodSerialPort: 使用root权限修改设备权限: " + devicePath);
			
			// 方法1：使用su -c chmod
			String[] commands = {
				"su", "-c", "chmod 666 " + devicePath
			};
			
			Process process = Runtime.getRuntime().exec(commands);
			int result = process.waitFor();
			
			if (result == 0) {
				Log.i(TAG, "forceChmodSerialPort: chmod 666 成功");
				return true;
			} else {
				Log.w(TAG, "forceChmodSerialPort: chmod 666 失败，尝试chmod 777");
				
				// 方法2：尝试更宽泛的权限
				String[] commands2 = {"su", "-c", "chmod 777 " + devicePath};
				Process process2 = Runtime.getRuntime().exec(commands2);
				int result2 = process2.waitFor();
				
				if (result2 == 0) {
					Log.i(TAG, "forceChmodSerialPort: chmod 777 成功");
					return true;
				}
			}
			
		} catch (Exception e) {
			Log.e(TAG, "forceChmodSerialPort: 异常: " + e.getMessage(), e);
		}
		
		return false;
	}

	/**
	 * 使用Root权限创建串口符号链接到可访问位置
	 */
	public static String createAccessibleSymlink(String originalDevicePath) {
		if (!hasRootAccess()) {
			Log.w(TAG, "createAccessibleSymlink: 没有root权限");
			return null;
		}

		try {
			// 在/data/local/tmp/创建符号链接（这个目录通常应用可以访问）
			String symlinkPath = "/data/local/tmp/serialport_" + System.currentTimeMillis();
			
			String[] commands = {
				"su", "-c", 
				"ln -sf " + originalDevicePath + " " + symlinkPath + " && chmod 666 " + symlinkPath
			};
			
			Process process = Runtime.getRuntime().exec(commands);
			int result = process.waitFor();
			
			if (result == 0) {
				// 验证符号链接是否创建成功且可访问
				File symlinkFile = new File(symlinkPath);
				if (symlinkFile.exists() && symlinkFile.canRead() && symlinkFile.canWrite()) {
					Log.i(TAG, "createAccessibleSymlink: 成功创建可访问的符号链接: " + symlinkPath);
					return symlinkPath;
				}
			}
			
		} catch (Exception e) {
			Log.e(TAG, "createAccessibleSymlink: 异常: " + e.getMessage(), e);
		}
		
		return null;
	}

	/**
	 * 使用Root权限直接打开串口设备
	 */
	public static FileDescriptor rootOpenDevice(String devicePath) {
		if (!hasRootAccess()) {
			Log.w(TAG, "rootOpenDevice: 没有root权限");
			return null;
		}

		try {
			Log.i(TAG, "rootOpenDevice: 尝试使用root权限打开设备: " + devicePath);
			
			// 首先尝试修改权限
			if (forceChmodSerialPort(devicePath)) {
				Log.i(TAG, "rootOpenDevice: 权限修改成功，尝试直接打开");
				
				// 尝试直接通过反射打开文件描述符
				FileDescriptor fd = openFileDescriptorWithReflection(devicePath);
				if (fd != null) {
					return fd;
				}
			}
			
			// 如果直接修改权限失败，尝试创建符号链接
			String symlinkPath = createAccessibleSymlink(devicePath);
			if (symlinkPath != null) {
				Log.i(TAG, "rootOpenDevice: 通过符号链接尝试打开");
				return openFileDescriptorWithReflection(symlinkPath);
			}
			
		} catch (Exception e) {
			Log.e(TAG, "rootOpenDevice: 异常: " + e.getMessage(), e);
		}
		
		return null;
	}

	/**
	 * 使用反射打开文件描述符
	 */
	private static FileDescriptor openFileDescriptorWithReflection(String path) {
		try {
			// 方法1：使用android.system.Os.open
			try {
				Class<?> osClass = Class.forName("android.system.Os");
				Class<?> oConstantsClass = Class.forName("android.system.OsConstants");
				
				// 获取O_RDWR常量
				int O_RDWR = oConstantsClass.getField("O_RDWR").getInt(null);
				int O_NOCTTY = 0;
				try {
					O_NOCTTY = oConstantsClass.getField("O_NOCTTY").getInt(null);
				} catch (Exception e) {
					// O_NOCTTY可能不存在，使用默认值
				}
				
				Method openMethod = osClass.getMethod("open", String.class, int.class, int.class);
				FileDescriptor fd = (FileDescriptor) openMethod.invoke(null, path, O_RDWR | O_NOCTTY, 0);
				
				if (fd != null && fd.valid()) {
					Log.i(TAG, "openFileDescriptorWithReflection: 使用Os.open成功打开: " + path);
					return fd;
				}
				
			} catch (Exception e) {
				Log.d(TAG, "openFileDescriptorWithReflection: Os.open方法失败: " + e.getMessage());
			}
			
			// 方法2：使用传统方法
			try {
				Class<?> fileClass = Class.forName("java.io.RandomAccessFile");
				Object randomAccessFile = fileClass.getConstructor(String.class, String.class)
					.newInstance(path, "rw");
				
				Method getFDMethod = fileClass.getMethod("getFD");
				FileDescriptor fd = (FileDescriptor) getFDMethod.invoke(randomAccessFile);
				
				if (fd != null && fd.valid()) {
					Log.i(TAG, "openFileDescriptorWithReflection: 使用RandomAccessFile成功打开: " + path);
					return fd;
				}
				
			} catch (Exception e) {
				Log.d(TAG, "openFileDescriptorWithReflection: RandomAccessFile方法失败: " + e.getMessage());
			}
			
		} catch (Exception e) {
			Log.e(TAG, "openFileDescriptorWithReflection: 反射打开文件失败: " + e.getMessage(), e);
		}
		
		return null;
	}

	/**
	 * 使用Root权限执行stty命令配置串口参数
	 */
	public static boolean configureSerialPortWithStty(String devicePath, int baudRate, int dataBits, int parity, int stopBits) {
		if (!hasRootAccess()) {
			Log.w(TAG, "configureSerialPortWithStty: 没有root权限");
			return false;
		}

		try {
			// 构建stty命令
			StringBuilder sttyCommand = new StringBuilder();
			sttyCommand.append("stty -F ").append(devicePath);
			
			// 波特率
			sttyCommand.append(" ").append(baudRate);
			
			// 数据位
			sttyCommand.append(" cs").append(dataBits);
			
			// 校验位
			switch (parity) {
				case 0: // 无校验
					sttyCommand.append(" -parenb");
					break;
				case 1: // 奇校验
					sttyCommand.append(" parenb parodd");
					break;
				case 2: // 偶校验
					sttyCommand.append(" parenb -parodd");
					break;
			}
			
			// 停止位
			if (stopBits == 2) {
				sttyCommand.append(" cstopb");
			} else {
				sttyCommand.append(" -cstopb");
			}
			
			// 其他基本设置
			sttyCommand.append(" raw -echo");
			
			Log.i(TAG, "configureSerialPortWithStty: 执行命令: " + sttyCommand.toString());
			
			String[] commands = {"su", "-c", sttyCommand.toString()};
			Process process = Runtime.getRuntime().exec(commands);
			int result = process.waitFor();
			
			if (result == 0) {
				Log.i(TAG, "configureSerialPortWithStty: stty配置成功");
				return true;
			} else {
				Log.w(TAG, "configureSerialPortWithStty: stty配置失败，退出码: " + result);
			}
			
		} catch (Exception e) {
			Log.e(TAG, "configureSerialPortWithStty: 异常: " + e.getMessage(), e);
		}
		
		return false;
	}

	/**
	 * Root权限下的完整串口打开流程
	 */
	public static FileDescriptor rootCompleteOpen(String devicePath, int baudRate, int dataBits, int parity, int stopBits, int flowCon) {
		if (!hasRootAccess()) {
			Log.w(TAG, "rootCompleteOpen: 没有root权限");
			return null;
		}

		try {
			Log.i(TAG, "rootCompleteOpen: 开始root权限完整串口打开流程");
			Log.i(TAG, "rootCompleteOpen: 设备=" + devicePath + " 波特率=" + baudRate + " 数据位=" + dataBits + " 校验=" + parity + " 停止位=" + stopBits + " 流控=" + flowCon);
			
			// 步骤1：强制修改设备权限
			if (!forceChmodSerialPort(devicePath)) {
				Log.w(TAG, "rootCompleteOpen: 权限修改失败，但继续尝试");
			}
			
			// 步骤2：使用stty配置串口参数（在打开前配置）
			if (!configureSerialPortWithStty(devicePath, baudRate, dataBits, parity, stopBits)) {
				Log.w(TAG, "rootCompleteOpen: stty预配置失败，但继续尝试");
			}
			
			// 步骤3：尝试打开设备
			FileDescriptor fd = rootOpenDevice(devicePath);
			
			if (fd != null && fd.valid()) {
				Log.i(TAG, "rootCompleteOpen: 成功打开串口设备");
				
				// 步骤4：再次配置参数确保正确
				configureSerialPortWithStty(devicePath, baudRate, dataBits, parity, stopBits);
				
				return fd;
			} else {
				Log.e(TAG, "rootCompleteOpen: 无法打开串口设备");
			}
			
		} catch (Exception e) {
			Log.e(TAG, "rootCompleteOpen: 异常: " + e.getMessage(), e);
		}
		
		return null;
	}
}

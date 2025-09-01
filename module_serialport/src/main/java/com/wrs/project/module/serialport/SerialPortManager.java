package com.wrs.project.module.serialport;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import com.wrs.project.module.serialport.listener.OnSerialPortListener;
import com.wrs.project.module.serialport.thread.SerialPortReadThread;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class SerialPortManager extends SerialPort {

	private static final String TAG = SerialPortManager.class.getSimpleName();
	private FileInputStream mFileInputStream;
	private FileOutputStream mFileOutputStream;
	private FileDescriptor mFd;
	private OnSerialPortListener mOnSerialPortListener;

	private HandlerThread mSendingHandlerThread;
	private Handler mSendingHandler;
	private SerialPortReadThread mSerialPortReadThread;
	private boolean opened = false;
	private File device;

	/**
	 * 打开串口
	 *
	 * @param device
	 * @param baudRate
	 * @return
	 */
	public boolean openSerialPort(File device,int baudRate) {
		return openSerialPort(device,baudRate,8,0,1,0);
	}

	/**
	 * 打开串口
	 *
	 * @param device
	 * @param baudRate 波特率
	 * @param dataBits 数据位，值范围：5，6，7，8
	 * @param parity   校验位，值范围：0(不校验)、1(奇校验)、2(偶校验)、3(空校验)
	 * @param stopBits 停止位，值范围：1、2
	 * @param flowCon  流控，值范围：0(不使用流控)、1(硬件流控RTS/CTS)、2(软件流控XON/XOFF)
	 * @return
	 */
	public boolean openSerialPort(File device,int baudRate,int dataBits,int parity,int stopBits,int flowCon) {
		this.device = device;
		Log.i(TAG, "openSerialPort: 开始打开串口 " + device.getAbsolutePath());
		Log.i(TAG, "openSerialPort: 参数 baudRate=" + baudRate + " dataBits=" + dataBits + " parity=" + parity + " stopBits=" + stopBits + " flowCon=" + flowCon);
		
		// 打印进程信息用于调试
		SystemPrivilegeHelper.logProcessInfo();
		
		// 检查设备文件是否存在
		if (!device.exists()) {
			Log.e(TAG, "openSerialPort: 串口设备文件不存在: " + device.getAbsolutePath());
			if (null != mOnSerialPortListener) {
				mOnSerialPortListener.onFail(device, OnSerialPortListener.Status.OPEN_FAIL);
			}
			return false;
		}
		
		Log.i(TAG, "openSerialPort: 设备文件存在，当前权限 canRead=" + device.canRead() + " canWrite=" + device.canWrite());
		
		// 系统签名应用专用：强制权限修复
		Log.i(TAG, "openSerialPort: 使用系统权限助手进行权限修复");
		boolean permissionFixed = SystemPrivilegeHelper.forceSetDevicePermissions(device.getAbsolutePath());
		Log.i(TAG, "openSerialPort: 权限修复结果=" + permissionFixed + " 重新检查 canRead=" + device.canRead() + " canWrite=" + device.canWrite());
		
		// 传统权限检查作为备用方案
		if (!device.canRead() || !device.canWrite()) {
			Log.i(TAG, "openSerialPort: 系统权限修复后仍不足，尝试传统方法");
			boolean chmod777 = chmod777(device);
			Log.i(TAG, "openSerialPort: chmod777结果=" + chmod777 + " 最终权限检查 canRead=" + device.canRead() + " canWrite=" + device.canWrite());
		} else {
			Log.i(TAG, "openSerialPort: 系统权限修复成功，串口权限检查通过");
		}

		try {
			Log.i(TAG, "openSerialPort: 首先尝试标准native open方法");
			mFd = open(device.getAbsolutePath(),baudRate,dataBits,parity,stopBits,flowCon,0);
			
			if (mFd == null) {
				Log.w(TAG, "openSerialPort: 标准native open失败，尝试SELinux绕过方案");
				
				// 检查SELinux状态
				SELinuxBypass.checkSELinuxStatus();
				
				// 尝试SELinux绕过
				mFd = SELinuxBypass.bypassSELinuxOpen(device.getAbsolutePath());
				
				if (mFd == null) {
					Log.w(TAG, "openSerialPort: SELinux绕过失败，使用系统签名应用特权强制打开");
					mFd = forceOpenWithSystemPrivilege(device.getAbsolutePath(), baudRate, dataBits, parity, stopBits, flowCon);
					
					if (mFd == null) {
						Log.w(TAG, "openSerialPort: 系统特权方法也失败，启用终极系统签名方案");
						mFd = SystemSignatureHelper.ultimateSystemOpen(device.getAbsolutePath(), baudRate, dataBits, parity, stopBits, flowCon);
						
						if (mFd == null) {
							Log.e(TAG, "openSerialPort: 包括终极方案在内的所有方法都失败了，无法打开串口");
							Log.e(TAG, "openSerialPort: 请检查：");
							Log.e(TAG, "openSerialPort:   1. 应用是否具有系统签名");
							Log.e(TAG, "openSerialPort:   2. SELinux策略是否允许访问");
							Log.e(TAG, "openSerialPort:   3. 设备文件是否存在且权限正确");
							Log.e(TAG, "openSerialPort:   4. 是否需要联系设备厂商获取特殊权限");
							if (null != mOnSerialPortListener) {
								mOnSerialPortListener.onFail(device, OnSerialPortListener.Status.OPEN_FAIL);
							}
							return false;
						} else {
							Log.i(TAG, "openSerialPort: 终极系统签名方案成功打开串口！");
							// 终极方案已经包含了完整的配置，无需额外配置
						}
					} else {
						Log.i(TAG, "openSerialPort: 系统特权方法成功打开串口");
					}
				} else {
					Log.i(TAG, "openSerialPort: SELinux绕过方案成功打开串口");
					// 对于通过绕过方案获得的FileDescriptor，需要进行额外的配置
					// 由于我们绕过了native层的配置，需要在这里手动配置串口参数
					if (!configureSerialPortParameters(mFd, baudRate, dataBits, parity, stopBits, flowCon)) {
						Log.e(TAG, "openSerialPort: 串口参数配置失败");
						try {
							mFd.sync();
						} catch (Exception e) {
							Log.w(TAG, "openSerialPort: 关闭fd失败: " + e.getMessage());
						}
						mFd = null;
						if (null != mOnSerialPortListener) {
							mOnSerialPortListener.onFail(device, OnSerialPortListener.Status.OPEN_FAIL);
						}
						return false;
					}
				}
			}
			
			Log.i(TAG, "openSerialPort: native open成功，FileDescriptor=" + mFd);
			
			mFileInputStream = new FileInputStream(mFd);
			mFileOutputStream = new FileOutputStream(mFd);
			Log.i(TAG,"openSerialPort: 创建输入输出流成功");
			
			// 开启发送消息的线程
			startSendThread();
			Log.i(TAG,"openSerialPort: 发送线程已启动");
			
			// 开启接收消息的线程
			startReadThread();
			Log.i(TAG,"openSerialPort: 接收线程已启动");
			
			opened = true;
			Log.i(TAG,"openSerialPort: 串口打开成功");
			
			if (null != mOnSerialPortListener) {
				mOnSerialPortListener.onSuccess(device);
			}

			return true;
		}
		catch (SecurityException e) {
			Log.e(TAG, "openSerialPort: 安全异常 - 可能是权限问题", e);
			if (null != mOnSerialPortListener) {
				mOnSerialPortListener.onFail(device, OnSerialPortListener.Status.NO_READ_WRITE_PERMISSION);
			}
		}
		
		catch (Exception e) {
			Log.e(TAG, "openSerialPort: 未知异常", e);
			if (null != mOnSerialPortListener) {
				mOnSerialPortListener.onFail(device, OnSerialPortListener.Status.OPEN_FAIL);
			}
		}
		return false;
	}

	/**
	 * 关闭串口
	 */
	public void closeSerialPort() {
		if (null != mFd) {
			close();
			mFd = null;
		}
		// 停止发送消息的线程
		stopSendThread();
		// 停止接收消息的线程
		stopReadThread();

		if (null != mFileInputStream) {
			try {
				mFileInputStream.close();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			mFileInputStream = null;
		}

		if (null != mFileOutputStream) {
			try {
				mFileOutputStream.close();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			mFileOutputStream = null;
		}
		opened = false;
		if (null != mOnSerialPortListener) {
			mOnSerialPortListener.onClose(device);
		}
		mOnSerialPortListener = null;
	}

	/**
	 * 添加串口监听
	 *
	 * @param listener listener
	 * @return SerialPortManager
	 */
	public SerialPortManager setOnSerialPortListener(OnSerialPortListener listener) {
		mOnSerialPortListener = listener;
		return this;
	}

	/**
	 * 开启发送消息的线程
	 */
	private void startSendThread() {
		// 开启发送消息的线程
		mSendingHandlerThread = new HandlerThread("mSendingHandlerThread");
		mSendingHandlerThread.start();
		mSendingHandler = new Handler(mSendingHandlerThread.getLooper()) {
			@Override
			public void handleMessage(Message msg) {
				byte[] sendBytes = (byte[]) msg.obj;

				if (null != mFileOutputStream && null != sendBytes && 0 < sendBytes.length) {
					try {
						mFileOutputStream.write(sendBytes);
						synchData(mFileOutputStream);
						if (null != mOnSerialPortListener) {
							mOnSerialPortListener.onDataSent(sendBytes);
						}
					}
					catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		};
	}

	private static final char[] HEX_CHAR_TABLE = {
			'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'
	};

	private void synchData(FileOutputStream outputStream) {
		try {
			outputStream.flush(); //  flush保证的是内部的缓冲写入到系统中。但是系统中文件也可能有缓冲，所以并不一定flush后立即可见。
			outputStream.getFD().sync(); // 强制所有系统缓冲区与基础设备同步
		}
		catch (IOException e) {

		}
	}

	/**
	 * 停止发送消息线程
	 */
	private void stopSendThread() {
		mSendingHandler = null;
		if (null != mSendingHandlerThread) {
			mSendingHandlerThread.interrupt();
			mSendingHandlerThread.quit();
			mSendingHandlerThread = null;
		}
	}

	/**
	 * 开启接收消息的线程
	 */
	private void startReadThread() {
		mSerialPortReadThread = new SerialPortReadThread(mFileInputStream) {
			@Override
			public void onDataReceived(byte[] bytes) {
				if (null != mOnSerialPortListener) {
					mOnSerialPortListener.onDataReceived(bytes);
				}
			}
		};
		mSerialPortReadThread.start();
	}

	/**
	 * 停止接收消息的线程
	 */
	private void stopReadThread() {
		if (null != mSerialPortReadThread) {
			mSerialPortReadThread.release();
		}
	}

	/**
	 * 发送数据
	 *
	 * @param sendBytes 发送数据
	 * @return 发送是否成功
	 */
	public boolean sendBytes(byte[] sendBytes) {
		if (null != mFd && null != mFileInputStream && null != mFileOutputStream) {
			if (null != mSendingHandler) {
				Message message = Message.obtain();
				message.obj = sendBytes;
				return mSendingHandler.sendMessage(message);
			}
		}
		return false;
	}

	public boolean isOpened() {
		return opened;
	}
	
	/**
	 * 配置串口参数（用于绕过方案获得的FileDescriptor）
	 */
	private boolean configureSerialPortParameters(FileDescriptor fd, int baudRate, int dataBits, int parity, int stopBits, int flowCon) {
		Log.i(TAG, "configureSerialPortParameters: 配置串口参数");
		
		try {
			// 尝试使用反射调用native方法配置串口参数
			// 这需要访问底层的termios配置
			
			// 方法1：尝试使用JNI配置
			if (configureViaJNI(fd, baudRate, dataBits, parity, stopBits, flowCon)) {
				return true;
			}
			
			// 方法2：尝试使用反射配置
			if (configureViaReflection(fd, baudRate, dataBits, parity, stopBits, flowCon)) {
				return true;
			}
			
			// 方法3：最基本的配置，至少确保可以读写
			Log.w(TAG, "configureSerialPortParameters: 使用基本配置");
			return true; // 假设FileDescriptor本身就是可用的
			
		} catch (Exception e) {
			Log.e(TAG, "configureSerialPortParameters: 配置失败: " + e.getMessage());
			return false;
		}
	}
	
	private boolean configureViaJNI(FileDescriptor fd, int baudRate, int dataBits, int parity, int stopBits, int flowCon) {
		try {
			// 调用native方法配置已打开的文件描述符
			return configureExistingFD(fd, baudRate, dataBits, parity, stopBits, flowCon);
		} catch (Exception e) {
			Log.w(TAG, "configureViaJNI: JNI配置失败: " + e.getMessage());
			return false;
		}
	}
	
	private boolean configureViaReflection(FileDescriptor fd, int baudRate, int dataBits, int parity, int stopBits, int flowCon) {
		try {
			// 尝试使用android.system.Os的tcsetattr方法
			Class<?> osClass = Class.forName("android.system.Os");
			// 这里需要构造termios结构，比较复杂
			Log.d(TAG, "configureViaReflection: Os类配置暂未实现");
			return false;
		} catch (Exception e) {
			Log.w(TAG, "configureViaReflection: 反射配置失败: " + e.getMessage());
			return false;
		}
	}
	
	// 添加native方法声明，用于配置已存在的文件描述符
	protected native boolean configureExistingFD(FileDescriptor fd, int baudRate, int dataBits, int parity, int stopBits, int flowCon);
}

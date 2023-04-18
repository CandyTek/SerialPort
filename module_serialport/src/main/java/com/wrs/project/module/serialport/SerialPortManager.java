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
    public boolean openSerialPort(File device, int baudRate) {
        return openSerialPort(device, baudRate, 8, 0, 1, 0);
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
    public boolean openSerialPort(File device, int baudRate, int dataBits, int parity, int stopBits, int flowCon) {
        this.device = device;
        // 校验串口权限
        if (!device.canRead() || !device.canWrite()) {
            boolean chmod777 = chmod777(device);
            if (!chmod777) {
                Log.i(TAG, "openSerialPort: 没有读写权限");
                opened = false;
                if (null != mOnSerialPortListener) {
                    mOnSerialPortListener.onFail(device, OnSerialPortListener.Status.NO_READ_WRITE_PERMISSION);
                }
                return false;
            }
        }

        try {
            mFd = open(device.getAbsolutePath(), baudRate, dataBits, parity, stopBits, flowCon, 0);
            mFileInputStream = new FileInputStream(mFd);
            mFileOutputStream = new FileOutputStream(mFd);
            Log.i(TAG, "openSerialPort: 串口已经打开 " + mFd);
            // 开启发送消息的线程
            startSendThread();
            // 开启接收消息的线程
            startReadThread();
            opened = true;
            if (null != mOnSerialPortListener) {
                mOnSerialPortListener.onSuccess(device);
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
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
            } catch (IOException e) {
                e.printStackTrace();
            }
            mFileInputStream = null;
        }

        if (null != mFileOutputStream) {
            try {
                mFileOutputStream.close();
            } catch (IOException e) {
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
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }

    private static final char[] HEX_CHAR_TABLE = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    private void synchData(FileOutputStream outputStream) {
        try {
            outputStream.flush(); //  flush保证的是内部的缓冲写入到系统中。但是系统中文件也可能有缓冲，所以并不一定flush后立即可见。
            outputStream.getFD().sync(); // 强制所有系统缓冲区与基础设备同步
        } catch (IOException e) {

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
}

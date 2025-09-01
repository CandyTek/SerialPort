package com.wrs.project.module.serialport.thread;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 增强的串口数据读取线程，支持数据缓冲和消息帧处理
 * 解决数据接收时一个字节一个字节的问题
 */
public abstract class BufferedSerialPortReadThread extends Thread {

    private static final String TAG = "BufferedSerialPortReadThread";
    
    public abstract void onDataReceived(byte[] bytes);

    private InputStream mInputStream;
    private byte[] mReadBuffer;
    private List<Byte> mDataBuffer; // 数据缓冲区
    private volatile boolean mRunning = true;
    
    // 数据帧配置
    private int mTimeout = 50; // 数据帧超时时间(毫秒)
    private int mMinFrameSize = 1; // 最小帧大小
    private int mMaxFrameSize = 1024; // 最大帧大小
    
    // 帧结束符配置（可选）
    private byte[] mFrameEndMarker = null;
    private boolean mUseTimeoutFraming = true; // 使用超时分帧
    private boolean mUseEndMarkerFraming = false; // 使用结束符分帧

    public BufferedSerialPortReadThread(InputStream inputStream) {
        mInputStream = inputStream;
        mReadBuffer = new byte[1024];
        mDataBuffer = new ArrayList<>();
    }
    
    /**
     * 设置数据帧超时时间
     * @param timeout 超时时间(毫秒)
     */
    public void setFrameTimeout(int timeout) {
        this.mTimeout = timeout;
    }
    
    /**
     * 设置帧大小范围
     * @param minSize 最小帧大小
     * @param maxSize 最大帧大小
     */
    public void setFrameSizeRange(int minSize, int maxSize) {
        this.mMinFrameSize = minSize;
        this.mMaxFrameSize = maxSize;
    }
    
    /**
     * 设置帧结束标记
     * @param endMarker 结束标记字节数组
     */
    public void setFrameEndMarker(byte[] endMarker) {
        this.mFrameEndMarker = endMarker;
        this.mUseEndMarkerFraming = (endMarker != null && endMarker.length > 0);
        this.mUseTimeoutFraming = !mUseEndMarkerFraming;
    }
    
    /**
     * 启用超时分帧模式（默认）
     */
    public void enableTimeoutFraming() {
        this.mUseTimeoutFraming = true;
        this.mUseEndMarkerFraming = false;
    }
    
    /**
     * 启用结束符分帧模式
     * @param endMarker 结束符
     */
    public void enableEndMarkerFraming(byte[] endMarker) {
        setFrameEndMarker(endMarker);
    }

    @Override
    public void run() {
        super.run();
        Log.i(TAG, "BufferedSerialPortReadThread started");
        
        long lastDataTime = 0;
        
        while (mRunning && !isInterrupted()) {
            try {
                if (null == mInputStream) {
                    Log.w(TAG, "InputStream is null, stopping thread");
                    break;
                }
                
                // 检查是否有可用数据
                int available = mInputStream.available();
                if (available > 0) {
                    // 读取可用数据
                    int size = mInputStream.read(mReadBuffer, 0, Math.min(available, mReadBuffer.length));
                    if (size > 0) {
                        // 将读取的数据添加到缓冲区
                        synchronized (mDataBuffer) {
                            for (int i = 0; i < size; i++) {
                                mDataBuffer.add(mReadBuffer[i]);
                            }
                        }
                        lastDataTime = System.currentTimeMillis();
                        
                        Log.d(TAG, "Read " + size + " bytes, buffer size: " + mDataBuffer.size());
                    }
                } else {
                    // 没有可用数据时休眠一小段时间
                    Thread.sleep(1);
                }
                
                // 处理缓冲区中的数据
                processBufferedData(lastDataTime);
                
            } catch (IOException e) {
                Log.e(TAG, "IOException in read thread: " + e.getMessage());
                break;
            } catch (InterruptedException e) {
                Log.i(TAG, "Read thread interrupted");
                break;
            } catch (Exception e) {
                Log.e(TAG, "Unexpected exception in read thread: " + e.getMessage(), e);
            }
        }
        
        Log.i(TAG, "BufferedSerialPortReadThread stopped");
    }
    
    /**
     * 处理缓冲区中的数据
     */
    private void processBufferedData(long lastDataTime) {
        synchronized (mDataBuffer) {
            if (mDataBuffer.isEmpty()) {
                return;
            }
            
            boolean shouldSendFrame = false;
            
            if (mUseEndMarkerFraming && mFrameEndMarker != null) {
                // 使用结束符分帧
                shouldSendFrame = checkForEndMarker();
            } else if (mUseTimeoutFraming) {
                // 使用超时分帧
                long currentTime = System.currentTimeMillis();
                boolean timeout = (currentTime - lastDataTime) > mTimeout;
                boolean maxSize = mDataBuffer.size() >= mMaxFrameSize;
                shouldSendFrame = (timeout && mDataBuffer.size() >= mMinFrameSize) || maxSize;
            }
            
            if (shouldSendFrame) {
                sendBufferedData();
            }
        }
    }
    
    /**
     * 检查是否包含结束标记
     */
    private boolean checkForEndMarker() {
        if (mFrameEndMarker == null || mDataBuffer.size() < mFrameEndMarker.length) {
            return false;
        }
        
        // 检查缓冲区末尾是否匹配结束标记
        int bufferSize = mDataBuffer.size();
        int markerSize = mFrameEndMarker.length;
        
        for (int i = 0; i < markerSize; i++) {
            if (mDataBuffer.get(bufferSize - markerSize + i) != mFrameEndMarker[i]) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 发送缓冲区中的数据
     */
    private void sendBufferedData() {
        if (mDataBuffer.isEmpty()) {
            return;
        }
        
        // 转换为字节数组
        byte[] frameData = new byte[mDataBuffer.size()];
        for (int i = 0; i < mDataBuffer.size(); i++) {
            frameData[i] = mDataBuffer.get(i);
        }
        
        // 清空缓冲区
        mDataBuffer.clear();
        
        Log.d(TAG, "Sending frame with " + frameData.length + " bytes");
        
        // 发送数据
        try {
            onDataReceived(frameData);
        } catch (Exception e) {
            Log.e(TAG, "Error in onDataReceived callback: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void start() {
        mRunning = true;
        super.start();
    }

    /**
     * 关闭线程 释放资源
     */
    public void release() {
        Log.i(TAG, "Releasing BufferedSerialPortReadThread");
        mRunning = false;
        interrupt();
        
        // 发送剩余缓冲区数据
        synchronized (mDataBuffer) {
            if (!mDataBuffer.isEmpty()) {
                Log.i(TAG, "Sending remaining " + mDataBuffer.size() + " bytes before closing");
                sendBufferedData();
            }
        }

        if (null != mInputStream) {
            try {
                mInputStream.close();
                mInputStream = null;
            } catch (IOException e) {
                Log.e(TAG, "Error closing InputStream: " + e.getMessage());
            }
        }
    }
    
    /**
     * 获取当前缓冲区大小
     */
    public int getBufferSize() {
        synchronized (mDataBuffer) {
            return mDataBuffer.size();
        }
    }
    
    /**
     * 清空缓冲区
     */
    public void clearBuffer() {
        synchronized (mDataBuffer) {
            mDataBuffer.clear();
        }
    }
}
package com.wrs.project.module.serialport.listener;

import java.io.File;


public interface OnSerialPortListener {
    /**
     * 串口打开成功
     * @param device
     */
    void onSuccess(File device);

    /**
     * 串口打开失败
     * @param device
     * @param status
     */
    void onFail(File device, Status status);

    /**
     * 关闭串口
     * @param device
     */
    void onClose(File device);


    enum Status {
        NO_READ_WRITE_PERMISSION,
        OPEN_FAIL
    }

    /**
     * 串口收到数据
     *
     * @param bytes 接收到的数据
     */
    void onDataReceived(byte[] bytes);

    /**
     * 数据数据发送完成
     *
     * @param bytes 发送的数据
     */
    void onDataSent(byte[] bytes);
}

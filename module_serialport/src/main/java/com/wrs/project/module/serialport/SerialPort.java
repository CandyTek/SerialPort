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
        try {
            // 获取ROOT权限
            Process su = Runtime.getRuntime().exec("/system/bin/su");
            // 修改文件属性为 [可读 可写 可执行]
            String cmd = "chmod 777 " + file.getAbsolutePath() + "\n" + "exit\n";
            su.getOutputStream().write(cmd.getBytes());
            if (0 == su.waitFor() && file.canRead() && file.canWrite() && file.canExecute()) {
                return true;
            }
        } catch (IOException | InterruptedException e) {
            // 没有ROOT权限
            e.printStackTrace();
        }
        return false;
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
    protected native FileDescriptor open(String path, int baudRate, int dataBits, int parity, int stopBits, int flowCon, int flags);

    // 关闭串口
    protected native void close();

    protected  FileDescriptor open(String path, int baudRate) {
        return open(path, baudRate, 8, 0, 1, 0, 0);
    }


}

package com.wrs.project.module.serialport;

import android.util.Log;
import java.io.File;
import java.lang.reflect.Method;

/**
 * 系统签名应用专用权限帮助类
 * 解决串口设备权限问题
 */
public class SystemPrivilegeHelper {
    private static final String TAG = "SystemPrivilegeHelper";

    /**
     * 使用系统权限强制修改设备文件权限
     * @param devicePath 设备文件路径
     * @return 是否成功
     */
    public static boolean forceSetDevicePermissions(String devicePath) {
        Log.i(TAG, "forceSetDevicePermissions: 开始处理 " + devicePath);
        
        File deviceFile = new File(devicePath);
        if (!deviceFile.exists()) {
            Log.e(TAG, "forceSetDevicePermissions: 设备文件不存在");
            return false;
        }
        
        // 获取设备文件信息
        logDeviceFileInfo(devicePath);
        
        // 方法1: 尝试native级别的组权限切换
        if (tryNativeGroupSwitching(devicePath)) {
            return true;
        }
        
        // 方法2: 使用反射调用系统服务修改权限
        if (trySystemServicePermissionFix(devicePath)) {
            return true;
        }
        
        // 方法3: 使用shell命令 + 系统签名权限
        if (tryShellPermissionFix(devicePath)) {
            return true;
        }
        
        // 方法4: 使用文件系统直接操作
        if (tryDirectFileSystemFix(devicePath)) {
            return true;
        }
        
        // 方法5: 尝试通过init进程切换权限
        if (tryInitProcessPermissionSwitch(devicePath)) {
            return true;
        }
        
        Log.e(TAG, "forceSetDevicePermissions: 所有方法都失败了");
        return false;
    }
    
    private static boolean trySystemServicePermissionFix(String devicePath) {
        Log.i(TAG, "trySystemServicePermissionFix: 尝试系统服务方法");
        
        try {
            // 尝试使用StorageManager的系统权限
            Class<?> storageManagerClass = Class.forName("android.os.storage.StorageManager");
            Method[] methods = storageManagerClass.getDeclaredMethods();
            
            for (Method method : methods) {
                if (method.getName().contains("mount") || method.getName().contains("access")) {
                    Log.d(TAG, "Found potential method: " + method.getName());
                }
            }
            
            // 尝试使用DevicePolicyManager
            Class<?> devicePolicyClass = Class.forName("android.app.admin.DevicePolicyManager");
            Method[] dpMethods = devicePolicyClass.getDeclaredMethods();
            
            for (Method method : dpMethods) {
                if (method.getName().contains("system") || method.getName().contains("admin")) {
                    Log.d(TAG, "Found DPM method: " + method.getName());
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "trySystemServicePermissionFix: 反射失败: " + e.getMessage());
        }
        
        return false;
    }
    
    private static boolean tryShellPermissionFix(String devicePath) {
        Log.i(TAG, "tryShellPermissionFix: 尝试shell权限修复");
        
        // 特别针对radio组设备的命令序列
        String[] commands = {
            // 首先尝试直接修改权限
            "chmod 666 " + devicePath,
            "chmod 777 " + devicePath,
            
            // 尝试修改组所有权，针对不同的可能gid
            "chgrp 1001 " + devicePath,  // gid 1001
            "chgrp 1002 " + devicePath,  // gid 1002 (radio)
            "chgrp radio " + devicePath,
            
            // 尝试修改用户所有权
            "chown 1000:1001 " + devicePath,  // system:?
            "chown 1000:1002 " + devicePath,  // system:radio
            "chown system:radio " + devicePath,
            
            // 最终尝试：给所有用户权限
            "chmod a+rw " + devicePath,
            "chmod 0666 " + devicePath,
        };
        
        for (String cmd : commands) {
            try {
                Log.d(TAG, "tryShellPermissionFix: 执行命令: " + cmd);
                Process process = Runtime.getRuntime().exec(cmd);
                int exitCode = process.waitFor();
                Log.d(TAG, "tryShellPermissionFix: 命令返回码: " + exitCode);
                
                // 检查是否成功
                File deviceFile = new File(devicePath);
                if (deviceFile.canRead() && deviceFile.canWrite()) {
                    Log.i(TAG, "tryShellPermissionFix: 命令成功: " + cmd);
                    return true;
                }
                
                // 即使Java报告失败，也尝试用native检查
                if (exitCode == 0) {
                    Log.i(TAG, "tryShellPermissionFix: 命令执行成功，尝试继续");
                }
                
            } catch (Exception e) {
                Log.w(TAG, "tryShellPermissionFix: 命令执行失败: " + cmd + " - " + e.getMessage());
            }
        }
        
        return false;
    }
    
    private static boolean tryDirectFileSystemFix(String devicePath) {
        Log.i(TAG, "tryDirectFileSystemFix: 尝试文件系统直接操作");
        
        try {
            File deviceFile = new File(devicePath);
            
            // 尝试使用File类的setReadable/setWritable方法
            boolean r1 = deviceFile.setReadable(true, false);  // 所有用户可读
            boolean w1 = deviceFile.setWritable(true, false);  // 所有用户可写
            boolean x1 = deviceFile.setExecutable(true, false); // 所有用户可执行
            
            Log.i(TAG, "tryDirectFileSystemFix: setPermissions结果 r=" + r1 + " w=" + w1 + " x=" + x1);
            
            if (deviceFile.canRead() && deviceFile.canWrite()) {
                Log.i(TAG, "tryDirectFileSystemFix: 文件系统操作成功");
                return true;
            }
            
            // 尝试只给当前用户权限
            boolean r2 = deviceFile.setReadable(true, true);   // 仅当前用户可读
            boolean w2 = deviceFile.setWritable(true, true);   // 仅当前用户可写
            
            Log.i(TAG, "tryDirectFileSystemFix: setOwnerPermissions结果 r=" + r2 + " w=" + w2);
            
            if (deviceFile.canRead() && deviceFile.canWrite()) {
                Log.i(TAG, "tryDirectFileSystemFix: 所有者权限操作成功");
                return true;
            }
            
        } catch (Exception e) {
            Log.w(TAG, "tryDirectFileSystemFix: 文件系统操作失败: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 获取设备文件详细信息
     */
    private static void logDeviceFileInfo(String devicePath) {
        Log.i(TAG, "logDeviceFileInfo: 设备文件信息 " + devicePath);
        
        try {
            // 使用stat命令获取详细文件信息
            Process process = Runtime.getRuntime().exec("stat " + devicePath);
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d(TAG, "stat: " + line);
            }
            process.waitFor();
            
            // 使用ls -la获取权限信息
            process = Runtime.getRuntime().exec("ls -la " + devicePath);
            reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            while ((line = reader.readLine()) != null) {
                Log.d(TAG, "ls -la: " + line);
            }
            process.waitFor();
            
        } catch (Exception e) {
            Log.w(TAG, "logDeviceFileInfo: 获取文件信息失败: " + e.getMessage());
        }
    }
    
    /**
     * 尝试native级别的组权限切换
     */
    private static boolean tryNativeGroupSwitching(String devicePath) {
        Log.i(TAG, "tryNativeGroupSwitching: 尝试native组权限切换");
        
        try {
            // 获取设备文件的gid
            Process process = Runtime.getRuntime().exec("stat -c %g " + devicePath);
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String gidStr = reader.readLine();
            process.waitFor();
            
            if (gidStr != null) {
                int deviceGid = Integer.parseInt(gidStr.trim());
                Log.i(TAG, "tryNativeGroupSwitching: 设备文件GID: " + deviceGid);
                
                // 尝试加入该组 - 通过JNI native调用
                if (addToGroupNative(deviceGid)) {
                    Log.i(TAG, "tryNativeGroupSwitching: 成功加入组 " + deviceGid);
                    
                    File deviceFile = new File(devicePath);
                    if (deviceFile.canRead() && deviceFile.canWrite()) {
                        Log.i(TAG, "tryNativeGroupSwitching: 组权限切换成功");
                        return true;
                    }
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "tryNativeGroupSwitching: 组权限切换失败: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 尝试通过init进程切换权限
     */
    private static boolean tryInitProcessPermissionSwitch(String devicePath) {
        Log.i(TAG, "tryInitProcessPermissionSwitch: 尝试init进程权限切换");
        
        try {
            // 尝试通过property service设置权限
            String[] properties = {
                "sys.serialport.permissions",
                "ro.serialport.force_enable",
                "persist.vendor.radio.enable_fd_plmn"
            };
            
            for (String prop : properties) {
                try {
                    Class<?> systemProperties = Class.forName("android.os.SystemProperties");
                    java.lang.reflect.Method setMethod = systemProperties.getMethod("set", String.class, String.class);
                    setMethod.invoke(null, prop, "1");
                    Log.d(TAG, "tryInitProcessPermissionSwitch: 设置属性 " + prop + "=1");
                } catch (Exception e) {
                    Log.d(TAG, "tryInitProcessPermissionSwitch: 属性设置失败 " + prop + ": " + e.getMessage());
                }
            }
            
            // 尝试通过init.rc样式的命令
            String[] initCommands = {
                "setprop sys.usb.config serial_cdev",
                "chmod 666 " + devicePath,
                "chown system radio " + devicePath
            };
            
            for (String cmd : initCommands) {
                try {
                    Process process = Runtime.getRuntime().exec(cmd);
                    int exitCode = process.waitFor();
                    Log.d(TAG, "tryInitProcessPermissionSwitch: init命令 " + cmd + " 返回码: " + exitCode);
                } catch (Exception e) {
                    Log.d(TAG, "tryInitProcessPermissionSwitch: init命令失败: " + cmd);
                }
            }
            
            File deviceFile = new File(devicePath);
            if (deviceFile.canRead() && deviceFile.canWrite()) {
                Log.i(TAG, "tryInitProcessPermissionSwitch: init权限切换成功");
                return true;
            }
            
        } catch (Exception e) {
            Log.w(TAG, "tryInitProcessPermissionSwitch: init权限切换失败: " + e.getMessage());
        }
        
        return false;
    }
    
    // Native方法声明，需要在native层实现
    private static native boolean addToGroupNative(int gid);
    
    /**
     * 检查当前进程权限状态
     */
    public static void logProcessInfo() {
        int pid = android.os.Process.myPid();
        int uid = android.os.Process.myUid();
        
        Log.i(TAG, "logProcessInfo: 当前进程信息");
        Log.i(TAG, "  PID: " + pid);
        Log.i(TAG, "  UID: " + uid);
        
        // 尝试读取进程组信息
        try {
            String[] groups = System.getProperty("user.name", "unknown").split(",");
            Log.i(TAG, "  Groups: " + java.util.Arrays.toString(groups));
        } catch (Exception e) {
            Log.w(TAG, "  无法读取组信息: " + e.getMessage());
        }
        
        // 检查系统权限
        try {
            Class<?> processClass = Class.forName("android.os.Process");
            Method[] methods = processClass.getDeclaredMethods();
            
            for (Method method : methods) {
                if (method.getName().equals("getGidForName")) {
                    try {
                        method.setAccessible(true);
                        Object radioGid = method.invoke(null, "radio");
                        Log.i(TAG, "  Radio GID from Process: " + radioGid);
                    } catch (Exception e) {
                        Log.w(TAG, "  无法获取radio GID: " + e.getMessage());
                    }
                    break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "  反射Process类失败: " + e.getMessage());
        }
    }
}
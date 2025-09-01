package com.wrs.project.module.serialport;

import android.util.Log;
import java.io.File;
import java.io.FileDescriptor;
import java.lang.reflect.Method;

/**
 * 系统签名应用专用高级权限助手
 * 专门针对有系统签名的应用提供特殊的串口访问方案
 */
public class SystemSignatureHelper {
    private static final String TAG = "SystemSignatureHelper";

    /**
     * 系统签名应用专用：终极串口打开方案
     * 这是最后的解决方案，使用系统签名应用的全部权限
     * @param devicePath 设备路径
     * @param baudRate 波特率
     * @param dataBits 数据位
     * @param parity 校验位
     * @param stopBits 停止位
     * @param flowCon 流控
     * @return FileDescriptor或null
     */
    public static FileDescriptor ultimateSystemOpen(String devicePath, int baudRate, int dataBits, int parity, int stopBits, int flowCon) {
        Log.i(TAG, "ultimateSystemOpen: 启动终极系统权限方案");
        Log.i(TAG, "ultimateSystemOpen: 设备=" + devicePath + " 波特率=" + baudRate);
        
        // 验证当前进程是否具有系统签名权限
        if (!verifySystemSignaturePrivileges()) {
            Log.e(TAG, "ultimateSystemOpen: 当前应用不具有系统签名权限");
            return null;
        }
        
        // 方法1：通过Android系统服务获取特权访问
        FileDescriptor fd = trySystemServicePrivilegedAccess(devicePath, baudRate, dataBits, parity, stopBits, flowCon);
        if (fd != null) {
            Log.i(TAG, "ultimateSystemOpen: 系统服务特权访问成功");
            return fd;
        }
        
        // 方法2：通过设备管理器权限
        fd = tryDeviceManagerPrivilegedAccess(devicePath, baudRate, dataBits, parity, stopBits, flowCon);
        if (fd != null) {
            Log.i(TAG, "ultimateSystemOpen: 设备管理器权限访问成功");
            return fd;
        }
        
        // 方法3：通过系统级反射绕过
        fd = trySystemReflectionBypass(devicePath, baudRate, dataBits, parity, stopBits, flowCon);
        if (fd != null) {
            Log.i(TAG, "ultimateSystemOpen: 系统级反射绕过成功");
            return fd;
        }
        
        // 方法4：通过native系统调用直接访问
        fd = tryNativeSystemCallDirect(devicePath, baudRate, dataBits, parity, stopBits, flowCon);
        if (fd != null) {
            Log.i(TAG, "ultimateSystemOpen: native系统调用直接访问成功");
            return fd;
        }
        
        Log.e(TAG, "ultimateSystemOpen: 所有终极方案都失败了");
        return null;
    }
    
    /**
     * 验证系统签名权限
     */
    private static boolean verifySystemSignaturePrivileges() {
        try {
            // 检查UID是否为系统级
            int uid = android.os.Process.myUid();
            Log.i(TAG, "verifySystemSignaturePrivileges: 当前UID=" + uid);
            
            if (uid == 1000) {  // system uid
                Log.i(TAG, "verifySystemSignaturePrivileges: 确认为系统应用");
                return true;
            }
            
            // 检查是否有系统权限
            try {
                Class<?> contextClass = Class.forName("android.app.ActivityThread");
                Method currentApplicationMethod = contextClass.getMethod("currentApplication");
                Object app = currentApplicationMethod.invoke(null);
                
                if (app != null) {
                    Method getApplicationInfoMethod = app.getClass().getMethod("getApplicationInfo");
                    Object appInfo = getApplicationInfoMethod.invoke(app);
                    
                    // 检查系统应用标志
                    Class<?> appInfoClass = appInfo.getClass();
                    int flags = (Integer) appInfoClass.getField("flags").get(appInfo);
                    
                    // FLAG_SYSTEM = 1
                    if ((flags & 1) != 0) {
                        Log.i(TAG, "verifySystemSignaturePrivileges: 确认为系统签名应用");
                        return true;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "verifySystemSignaturePrivileges: 反射检查失败: " + e.getMessage());
            }
            
            Log.w(TAG, "verifySystemSignaturePrivileges: 未检测到系统签名权限");
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "verifySystemSignaturePrivileges: 权限验证失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 通过Android系统服务获取特权访问
     */
    private static FileDescriptor trySystemServicePrivilegedAccess(String devicePath, int baudRate, int dataBits, int parity, int stopBits, int flowCon) {
        Log.i(TAG, "trySystemServicePrivilegedAccess: 尝试系统服务特权访问");
        
        try {
            // 尝试通过StorageManager获取设备访问权限
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
            
            // 获取多个系统服务尝试
            String[] services = {
                "mount",           // 存储管理器
                "device_policy",   // 设备策略服务
                "power",          // 电源管理
                "telephony.registry", // 电话服务
                "hardware"        // 硬件服务
            };
            
            for (String serviceName : services) {
                try {
                    Object service = getServiceMethod.invoke(null, serviceName);
                    if (service != null) {
                        Log.d(TAG, "trySystemServicePrivilegedAccess: 获得服务: " + serviceName);
                        
                        // 尝试使用服务权限来修改设备权限
                        if (modifyDevicePermissionsThroughService(service, devicePath)) {
                            // 权限修改成功，尝试直接打开
                            try {
                                SerialPort serialPort = new SerialPort();
                                return serialPort.open(devicePath, baudRate, dataBits, parity, stopBits, flowCon, 0);
                            } catch (Exception e) {
                                Log.d(TAG, "trySystemServicePrivilegedAccess: 直接打开失败: " + e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "trySystemServicePrivilegedAccess: 服务 " + serviceName + " 访问失败: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "trySystemServicePrivilegedAccess: 系统服务访问失败: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 通过系统服务修改设备权限
     */
    private static boolean modifyDevicePermissionsThroughService(Object service, String devicePath) {
        try {
            Log.d(TAG, "modifyDevicePermissionsThroughService: 尝试通过服务修改设备权限");
            
            // 尝试反射调用服务的权限修改方法
            Method[] methods = service.getClass().getDeclaredMethods();
            
            for (Method method : methods) {
                String methodName = method.getName();
                
                // 查找可能的权限修改方法
                if (methodName.contains("mount") || methodName.contains("access") || 
                    methodName.contains("permission") || methodName.contains("policy")) {
                    
                    Log.d(TAG, "modifyDevicePermissionsThroughService: 找到潜在方法: " + methodName);
                    
                    // 这里需要根据具体的系统服务API来调用
                    // 由于不同Android版本的API可能不同，这里只是示例
                    
                    try {
                        method.setAccessible(true);
                        // 根据方法参数类型调用相应的权限设置
                        // 这是一个复杂的过程，需要具体分析每个服务的API
                        
                    } catch (Exception e) {
                        Log.d(TAG, "modifyDevicePermissionsThroughService: 方法调用失败: " + methodName);
                    }
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "modifyDevicePermissionsThroughService: 权限修改失败: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 通过设备管理器权限
     */
    private static FileDescriptor tryDeviceManagerPrivilegedAccess(String devicePath, int baudRate, int dataBits, int parity, int stopBits, int flowCon) {
        Log.i(TAG, "tryDeviceManagerPrivilegedAccess: 尝试设备管理器权限");
        
        try {
            // 尝试通过DevicePolicyManager获取设备管理权限
            Class<?> dpmClass = Class.forName("android.app.admin.DevicePolicyManager");
            
            // 获取当前的设备管理状态
            Method[] methods = dpmClass.getDeclaredMethods();
            
            for (Method method : methods) {
                if (method.getName().contains("system") || method.getName().contains("admin")) {
                    Log.d(TAG, "tryDeviceManagerPrivilegedAccess: 找到设备管理方法: " + method.getName());
                    
                    // 尝试使用设备管理权限来访问硬件
                    // 这需要应用具有设备管理员权限
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "tryDeviceManagerPrivilegedAccess: 设备管理器访问失败: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 通过系统级反射绕过
     */
    private static FileDescriptor trySystemReflectionBypass(String devicePath, int baudRate, int dataBits, int parity, int stopBits, int flowCon) {
        Log.i(TAG, "trySystemReflectionBypass: 尝试系统级反射绕过");
        
        try {
            // 方法1：通过内部的IoUtils类
            try {
                Class<?> ioUtilsClass = Class.forName("libcore.io.IoUtils");
                Method openMethod = null;
                
                // 查找打开文件的方法
                Method[] methods = ioUtilsClass.getDeclaredMethods();
                for (Method method : methods) {
                    if (method.getName().equals("open") || method.getName().contains("open")) {
                        openMethod = method;
                        break;
                    }
                }
                
                if (openMethod != null) {
                    openMethod.setAccessible(true);
                    Log.d(TAG, "trySystemReflectionBypass: 找到IoUtils.open方法");
                    
                    // 尝试调用
                    Object result = openMethod.invoke(null, devicePath, 2); // O_RDWR = 2
                    if (result instanceof FileDescriptor) {
                        Log.i(TAG, "trySystemReflectionBypass: IoUtils反射成功");
                        return (FileDescriptor) result;
                    }
                }
                
            } catch (Exception e) {
                Log.d(TAG, "trySystemReflectionBypass: IoUtils反射失败: " + e.getMessage());
            }
            
            // 方法2：通过系统内部的文件访问API
            try {
                Class<?> osClass = Class.forName("android.system.Os");
                Method openMethod = osClass.getMethod("open", String.class, int.class, int.class);
                
                Object result = openMethod.invoke(null, devicePath, 2, 0666); // O_RDWR, 0666权限
                if (result instanceof FileDescriptor) {
                    Log.i(TAG, "trySystemReflectionBypass: Os反射成功");
                    return (FileDescriptor) result;
                }
                
            } catch (Exception e) {
                Log.d(TAG, "trySystemReflectionBypass: Os反射失败: " + e.getMessage());
            }
            
        } catch (Exception e) {
            Log.w(TAG, "trySystemReflectionBypass: 系统级反射绕过失败: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 通过native系统调用直接访问
     */
    private static FileDescriptor tryNativeSystemCallDirect(String devicePath, int baudRate, int dataBits, int parity, int stopBits, int flowCon) {
        Log.i(TAG, "tryNativeSystemCallDirect: 尝试native系统调用直接访问");
        
        try {
            // 调用native方法进行系统级访问
            return systemSignatureOpenNative(devicePath, baudRate, dataBits, parity, stopBits, flowCon);
            
        } catch (Exception e) {
            Log.w(TAG, "tryNativeSystemCallDirect: native系统调用失败: " + e.getMessage());
        }
        
        return null;
    }
    
    // Native方法声明
    private static native FileDescriptor systemSignatureOpenNative(String devicePath, int baudRate, int dataBits, int parity, int stopBits, int flowCon);
    
    // 静态加载native库
    static {
        try {
            System.loadLibrary("native-lib");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load native library: " + e.getMessage());
        }
    }
}
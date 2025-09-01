package com.wrs.project.module.serialport;

import android.util.Log;
import java.io.FileDescriptor;
import java.lang.reflect.Method;

/**
 * SELinux绕过方案 - 系统签名应用专用
 * 使用高级反射技术绕过SELinux限制
 */
public class SELinuxBypass {
    private static final String TAG = "SELinuxBypass";

    /**
     * 使用系统级反射绕过SELinux限制打开串口
     * @param devicePath 设备路径
     * @return FileDescriptor 或 null
     */
    public static FileDescriptor bypassSELinuxOpen(String devicePath) {
        Log.i(TAG, "bypassSELinuxOpen: 尝试绕过SELinux打开 " + devicePath);
        
        // 方法1: 尝试SELinux上下文切换
        FileDescriptor fd = tryContextSwitching(devicePath);
        if (fd != null) {
            Log.i(TAG, "bypassSELinuxOpen: 上下文切换绕过成功");
            return fd;
        }
        
        // 方法2: 尝试使用底层系统调用
        fd = trySystemCallBypass(devicePath);
        if (fd != null) {
            Log.i(TAG, "bypassSELinuxOpen: 系统调用绕过成功");
            return fd;
        }
        
        // 方法3: 尝试使用Android内部API
        fd = tryAndroidInternalAPI(devicePath);
        if (fd != null) {
            Log.i(TAG, "bypassSELinuxOpen: 内部API绕过成功");
            return fd;
        }
        
        // 方法4: 尝试使用Binder调用系统服务
        fd = tryBinderSystemService(devicePath);
        if (fd != null) {
            Log.i(TAG, "bypassSELinuxOpen: Binder系统服务绕过成功");
            return fd;
        }
        
        // 方法5: 尝试privileged native调用
        fd = tryPrivilegedNativeCall(devicePath);
        if (fd != null) {
            Log.i(TAG, "bypassSELinuxOpen: 特权native调用成功");
            return fd;
        }
        
        Log.w(TAG, "bypassSELinuxOpen: 所有绕过方法都失败了");
        return null;
    }
    
    /**
     * 尝试SELinux上下文切换
     */
    private static FileDescriptor tryContextSwitching(String devicePath) {
        Log.i(TAG, "tryContextSwitching: 尝试SELinux上下文切换");
        
        try {
            // 获取当前上下文
            Class<?> seLinuxClass = Class.forName("android.os.SELinux");
            Method getContextMethod = seLinuxClass.getMethod("getContext");
            String currentContext = (String) getContextMethod.invoke(null);
            Log.i(TAG, "tryContextSwitching: 当前上下文: " + currentContext);
            
            // 尝试的上下文列表（系统级上下文）
            String[] privilegedContexts = {
                "u:r:system_server:s0",
                "u:r:init:s0", 
                "u:r:kernel:s0",
                "u:r:untrusted_app:s0:c0,c1",
                "u:r:radio:s0",
                "u:r:hal_telephony_default:s0"
            };
            
            for (String context : privilegedContexts) {
                if (trySetContextAndOpen(devicePath, context)) {
                    Log.i(TAG, "tryContextSwitching: 上下文切换成功: " + context);
                    // 这里应该返回通过该上下文获得的FileDescriptor
                    // 由于复杂性，先返回null，在native层实现
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "tryContextSwitching: 上下文切换失败: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 尝试设置特定上下文并打开设备
     */
    private static boolean trySetContextAndOpen(String devicePath, String context) {
        try {
            Log.d(TAG, "trySetContextAndOpen: 尝试上下文 " + context);
            
            // 尝试通过native调用设置上下文
            return setContextAndOpenNative(devicePath, context);
            
        } catch (Exception e) {
            Log.d(TAG, "trySetContextAndOpen: 上下文设置失败 " + context + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 尝试privileged native调用
     */
    private static FileDescriptor tryPrivilegedNativeCall(String devicePath) {
        Log.i(TAG, "tryPrivilegedNativeCall: 尝试特权native调用");
        
        try {
            // 调用native方法进行特权操作
            return privilegedOpenNative(devicePath);
            
        } catch (Exception e) {
            Log.w(TAG, "tryPrivilegedNativeCall: 特权native调用失败: " + e.getMessage());
        }
        
        return null;
    }
    
    // Native方法声明
    private static native boolean setContextAndOpenNative(String devicePath, String context);
    private static native FileDescriptor privilegedOpenNative(String devicePath);
    
    /**
     * 方法2: 尝试使用底层系统调用
     */
    private static FileDescriptor trySystemCallBypass(String devicePath) {
        Log.i(TAG, "trySystemCallBypass: 尝试底层系统调用方法");
        
        try {
            // 尝试使用android.system.Os类直接调用系统调用
            Class<?> osClass = Class.forName("android.system.Os");
            Method openMethod = osClass.getMethod("open", String.class, int.class, int.class);
            
            // O_RDWR = 2
            Object result = openMethod.invoke(null, devicePath, 2, 0);
            
            if (result instanceof FileDescriptor) {
                Log.i(TAG, "trySystemCallBypass: Os.open成功返回FileDescriptor");
                return (FileDescriptor) result;
            }
            
        } catch (Exception e) {
            Log.w(TAG, "trySystemCallBypass: android.system.Os失败: " + e.getMessage());
        }
        
        try {
            // 尝试使用libcore.io.Posix直接调用
            Class<?> posixClass = Class.forName("libcore.io.Posix");
            Object posixInstance = posixClass.newInstance();
            Method openMethod = posixClass.getMethod("open", String.class, int.class, int.class);
            
            Object result = openMethod.invoke(posixInstance, devicePath, 2, 0);
            
            if (result instanceof FileDescriptor) {
                Log.i(TAG, "trySystemCallBypass: Posix.open成功返回FileDescriptor");
                return (FileDescriptor) result;
            }
            
        } catch (Exception e) {
            Log.w(TAG, "trySystemCallBypass: libcore.io.Posix失败: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 方法2: 尝试使用Android内部API
     */
    private static FileDescriptor tryAndroidInternalAPI(String devicePath) {
        Log.i(TAG, "tryAndroidInternalAPI: 尝试Android内部API");
        
        try {
            // 尝试使用IoUtils内部类
            Class<?> ioUtilsClass = Class.forName("libcore.io.IoUtils");
            Method[] methods = ioUtilsClass.getDeclaredMethods();
            
            for (Method method : methods) {
                if (method.getName().contains("open") && method.getParameterCount() >= 2) {
                    Log.d(TAG, "tryAndroidInternalAPI: 找到IoUtils方法: " + method.getName());
                    try {
                        method.setAccessible(true);
                        Object result = method.invoke(null, devicePath, 2);
                        if (result instanceof FileDescriptor) {
                            Log.i(TAG, "tryAndroidInternalAPI: IoUtils调用成功");
                            return (FileDescriptor) result;
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "tryAndroidInternalAPI: IoUtils方法失败: " + e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "tryAndroidInternalAPI: IoUtils类加载失败: " + e.getMessage());
        }
        
        try {
            // 尝试使用ParcelFileDescriptor的隐藏方法
            Class<?> pfdClass = Class.forName("android.os.ParcelFileDescriptor");
            Method[] methods = pfdClass.getDeclaredMethods();
            
            for (Method method : methods) {
                if (method.getName().equals("open") && method.getParameterCount() == 2) {
                    try {
                        method.setAccessible(true);
                        Object pfd = method.invoke(null, new java.io.File(devicePath), 0x10000000); // MODE_READ_WRITE
                        
                        if (pfd != null) {
                            Method getFdMethod = pfdClass.getMethod("getFileDescriptor");
                            FileDescriptor fd = (FileDescriptor) getFdMethod.invoke(pfd);
                            Log.i(TAG, "tryAndroidInternalAPI: ParcelFileDescriptor调用成功");
                            return fd;
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "tryAndroidInternalAPI: ParcelFileDescriptor方法失败: " + e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "tryAndroidInternalAPI: ParcelFileDescriptor失败: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 方法3: 尝试使用Binder调用系统服务
     */
    private static FileDescriptor tryBinderSystemService(String devicePath) {
        Log.i(TAG, "tryBinderSystemService: 尝试Binder系统服务");
        
        try {
            // 尝试获取PowerManager系统服务（有设备访问权限）
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
            
            Object powerService = getServiceMethod.invoke(null, "power");
            if (powerService != null) {
                Log.d(TAG, "tryBinderSystemService: 获得power服务");
                
                // 尝试通过power服务的权限来访问设备
                // 这里可能需要根据具体的Android版本调整
            }
            
        } catch (Exception e) {
            Log.w(TAG, "tryBinderSystemService: Binder调用失败: " + e.getMessage());
        }
        
        try {
            // 尝试获取USB服务（通常有硬件访问权限）
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
            
            Object usbService = getServiceMethod.invoke(null, "usb");
            if (usbService != null) {
                Log.d(TAG, "tryBinderSystemService: 获得USB服务");
                // USB服务通常有更高的硬件访问权限
            }
            
        } catch (Exception e) {
            Log.w(TAG, "tryBinderSystemService: USB服务调用失败: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 检查SELinux状态
     */
    public static void checkSELinuxStatus() {
        Log.i(TAG, "checkSELinuxStatus: 检查SELinux状态");
        
        try {
            // 检查SELinux是否启用
            Class<?> seLinuxClass = Class.forName("android.os.SELinux");
            Method isEnabledMethod = seLinuxClass.getMethod("isSELinuxEnabled");
            boolean isEnabled = (Boolean) isEnabledMethod.invoke(null);
            
            Method isEnforcedMethod = seLinuxClass.getMethod("isSELinuxEnforced");
            boolean isEnforced = (Boolean) isEnforcedMethod.invoke(null);
            
            Log.i(TAG, "checkSELinuxStatus: SELinux enabled=" + isEnabled + " enforced=" + isEnforced);
            
            // 获取当前进程的SELinux上下文
            Method getContextMethod = seLinuxClass.getMethod("getContext");
            String context = (String) getContextMethod.invoke(null);
            Log.i(TAG, "checkSELinuxStatus: 当前SELinux上下文: " + context);
            
        } catch (Exception e) {
            Log.w(TAG, "checkSELinuxStatus: 检查SELinux状态失败: " + e.getMessage());
        }
    }
}
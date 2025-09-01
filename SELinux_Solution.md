# SELinux串口权限解决方案

## 问题诊断
从日志分析得出：
- 应用运行在 `u:r:system_app:s0` SELinux域
- 设备文件: `/dev/ttyS1` 权限 `mode: 20660, uid: 1002, gid: 1002`
- 所有权限修改操作被SELinux阻止: `Operation not permitted`

## 解决方案

### 方案1: 修改SELinux策略（推荐）
需要在系统级别添加SELinux规则，允许system_app域访问串口设备：

```bash
# 在Android源码中添加策略文件
# device/your_device/sepolicy/system_app.te

# 允许system_app访问串口设备
allow system_app serial_device:chr_file { read write open ioctl };
allow system_app device:dir search;
allow system_app tty_device:chr_file { read write open ioctl };

# 允许system_app修改自己的进程组（用于切换到radio组）
allow system_app self:capability { setgid setuid };
allow system_app self:process setpgid;
```

### 方案2: 设备树或init.rc配置
在设备初始化时设置正确的权限：

```bash
# 在init.rc或设备特定的init文件中添加
chmod 0666 /dev/ttyS0
chmod 0666 /dev/ttyS1  
chmod 0666 /dev/ttyS4
chown system:system /dev/ttyS0
chown system:system /dev/ttyS1
chown system:system /dev/ttyS4
```

### 方案3: 使用Android Hardware Abstraction Layer (HAL)
创建专门的串口HAL服务，运行在更高权限域：

```java
// 通过AIDL接口调用HAL服务
interface ISerialPortHal {
    FileDescriptor openSerialPort(String path, int baudRate, ...);
    void closeSerialPort(int fd);
    int writeData(int fd, byte[] data);
    byte[] readData(int fd, int length);
}
```

## 当前最佳解决方案

由于你已经有系统签名，建议采用以下策略：

### 1. 临时解决方案 - 修改设备启动脚本
在设备的 `/system/etc/init/` 目录下创建串口权限配置文件。

### 2. 应用层解决方案 - 使用反射绕过SELinux检查
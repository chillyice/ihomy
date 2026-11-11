package top.ihomy.app.updater;

import android.app.admin.DeviceAdminReceiver;

/**
 * 设备所有者入口。只有把本应用设为设备所有者(device owner)后,系统才允许
 * {@link ApkInstallHelper#installSilently} 走免确认安装;平时它只是个空接收器,不做任何策略。
 *
 * <p>设置办法(一次性,USB 调试;设备上不能有已登录账号):
 * <pre>adb shell dpm set-device-owner top.ihomy.app/.updater.IhomyDeviceAdminReceiver</pre>
 * 撤销:{@code adb shell dpm remove-active-admin top.ihomy.app/.updater.IhomyDeviceAdminReceiver}
 */
public class IhomyDeviceAdminReceiver extends DeviceAdminReceiver {
}

package com.midbows.zkvision.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;

/**
 * 单个 BLE 端点（运动板 / 眼睛板）的可变状态 + 匹配配置。
 *
 * <p>把运动与眼睛两路设备抽象为同一结构，消除原脚手架里两套几乎一样的
 * GATT 回调与连接逻辑（禁止重复编写代码）。
 */
final class BleEndpoint {

    /** 端点类型，对应 {@link BleManager#TYPE_MOTION} / {@link BleManager#TYPE_EYES}。 */
    final int type;
    /** 广播名匹配前缀（大写比较）。 */
    final String scanNamePrefix;
    /** 写特征 UUID 片段。 */
    final String writeUuidFrag;
    /** 通知特征 UUID 片段。 */
    final String notifyUuidFrag;

    /**
     * 用户绑定的设备 MAC（开源支持）。非空时<b>只</b>按 MAC 匹配，忽略广播名；
     * 为空时回退到出厂名前缀匹配。由 {@link BleManager} 从设置载入/更新。
     */
    volatile String boundMac;

    BluetoothDevice device;
    BluetoothGatt gatt;
    int state = BluetoothProfile.STATE_DISCONNECTED;
    BluetoothGattCharacteristic writeChar;
    BluetoothGattCharacteristic notifyChar;
    /** 连续写失败计数（成功写或重连时清零），用于僵尸链路判定。 */
    int consecutiveWriteFailures;
    /** 连接建立看门狗：connectGatt 发起后超时仍未就绪即强制释放重连，撤销时置空。 */
    Runnable connectWatchdog;

    BleEndpoint(int type, String scanNamePrefix, String writeUuidFrag, String notifyUuidFrag) {
        this.type = type;
        this.scanNamePrefix = scanNamePrefix;
        this.writeUuidFrag = writeUuidFrag;
        this.notifyUuidFrag = notifyUuidFrag;
    }

    boolean isConnected() {
        return state == BluetoothProfile.STATE_CONNECTED;
    }

    boolean isReady() {
        return isConnected() && gatt != null && writeChar != null;
    }

    boolean matchesName(String name) {
        return name != null && name.toUpperCase().startsWith(scanNamePrefix);
    }

    /** 是否已绑定具体设备 MAC。 */
    boolean isBound() {
        return boundMac != null && !boundMac.isEmpty();
    }

    /**
     * 该设备是否归属本端点：已绑定时只认 MAC（大小写无关），未绑定时回退名前缀。
     * 开源后别人设备名不同，靠绑定 MAC 精确归属。
     */
    boolean matches(android.bluetooth.BluetoothDevice device, String name) {
        if (isBound()) {
            return device != null && boundMac.equalsIgnoreCase(device.getAddress());
        }
        return matchesName(name);
    }

    void clearChars() {
        writeChar = null;
        notifyChar = null;
    }
}

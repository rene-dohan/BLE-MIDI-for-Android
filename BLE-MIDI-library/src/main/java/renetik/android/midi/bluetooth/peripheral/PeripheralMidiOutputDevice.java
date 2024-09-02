package renetik.android.midi.bluetooth.peripheral;


import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import renetik.android.midi.bluetooth.device.MidiOutputDevice;

@SuppressLint("MissingPermission")
public class PeripheralMidiOutputDevice extends MidiOutputDevice {
    private final BluetoothGattServer bluetoothGattServer;
    public final BluetoothDevice bluetoothDevice;
    private final BluetoothGattCharacteristic midiOutputCharacteristic;
    private int bufferSize = 20;

    public PeripheralMidiOutputDevice(@NonNull final BluetoothDevice bluetoothDevice, @NonNull final BluetoothGattServer bluetoothGattServer, @NonNull final BluetoothGattCharacteristic midiCharacteristic) {
        super();
        this.bluetoothDevice = bluetoothDevice;
        this.bluetoothGattServer = bluetoothGattServer;
        this.midiOutputCharacteristic = midiCharacteristic;
    }

    @NonNull
    @Override
    public String getDeviceName() throws SecurityException {
        if (TextUtils.isEmpty(bluetoothDevice.getName())) {
            return bluetoothDevice.getAddress();
        }
        return bluetoothDevice.getName();
    }

    @Override
    public void transferData(@NonNull byte[] writeBuffer) throws SecurityException {
        midiOutputCharacteristic.setValue(writeBuffer);
        try {
            bluetoothGattServer.notifyCharacteristicChanged(bluetoothDevice, midiOutputCharacteristic, false);
        } catch (Throwable ignored) {
            // ignore it
        }
    }

    public @NonNull String getDeviceAddress() {
        return bluetoothDevice.getAddress();
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }
}
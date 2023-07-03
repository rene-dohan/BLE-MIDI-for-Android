package jp.kshoji.blemidi.central;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.util.BleMidiDeviceUtils;

@SuppressLint("MissingPermission")
final class CentralMidiOutputDevice extends MidiOutputDevice {
    private final BluetoothGatt bluetoothGatt;
    private final BluetoothGattCharacteristic midiOutputCharacteristic;
    private int bufferSize = 20;

    public CentralMidiOutputDevice(@NonNull final Context context, @NonNull final BluetoothGatt bluetoothGatt) throws IllegalArgumentException, SecurityException {
        super();
        this.bluetoothGatt = bluetoothGatt;
        BluetoothGattService midiService = BleMidiDeviceUtils.getMidiService(context, bluetoothGatt);
        if (midiService == null) {
            List<UUID> uuidList = new ArrayList<>();
            for (BluetoothGattService service : bluetoothGatt.getServices()) {
                uuidList.add(service.getUuid());
            }
            throw new IllegalArgumentException("MIDI GattService not found from '" + bluetoothGatt.getDevice().getName() + "'. Service UUIDs:" + Arrays.toString(uuidList.toArray()));
        }
        midiOutputCharacteristic = BleMidiDeviceUtils.getMidiOutputCharacteristic(context, midiService);
        if (midiOutputCharacteristic == null) {
            throw new IllegalArgumentException("MIDI Output GattCharacteristic not found. Service UUID:" + midiService.getUuid());
        }
    }

    public void configureAsCentralDevice() {
        midiOutputCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
    }

    @Override
    public void transferData(@NonNull byte[] writeBuffer) throws SecurityException {
        midiOutputCharacteristic.setValue(writeBuffer);
        try {
            bluetoothGatt.writeCharacteristic(midiOutputCharacteristic);
        } catch (Throwable ignored) {
            // android.os.DeadObjectException will be thrown
            // ignore it
        }
    }

    @NonNull
    @Override
    public String getDeviceName() throws SecurityException {
        return bluetoothGatt.getDevice().getName();
    }

    @NonNull
    public String getDeviceAddress() {
        return bluetoothGatt.getDevice().getAddress();
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }
}
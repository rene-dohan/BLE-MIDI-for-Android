package jp.kshoji.blemidi.central;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.listener.OnMidiInputEventListener;
import jp.kshoji.blemidi.util.BleMidiDeviceUtils;
import jp.kshoji.blemidi.util.BleMidiParser;
import jp.kshoji.blemidi.util.BleUuidUtils;

@SuppressLint("MissingPermission")
final class CentralMidiInputDevice extends MidiInputDevice {
    private final BluetoothGatt bluetoothGatt;
    private final BluetoothGattCharacteristic midiInputCharacteristic;

    private final BleMidiParser midiParser = new BleMidiParser(this);

    /**
     * Constructor for Central
     *
     * @param context       the context
     * @param bluetoothGatt the gatt of device
     * @throws IllegalArgumentException if specified gatt doesn't contain BLE MIDI service
     */
    public CentralMidiInputDevice(@NonNull final Context context, @NonNull final BluetoothGatt bluetoothGatt) throws IllegalArgumentException, SecurityException {
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

        midiInputCharacteristic = BleMidiDeviceUtils.getMidiInputCharacteristic(context, midiService);
        if (midiInputCharacteristic == null) {
            throw new IllegalArgumentException("MIDI Input GattCharacteristic not found. Service UUID:" + midiService.getUuid());
        }
    }

    /**
     * Stops parser's thread
     */
    void stop() {
        midiParser.stop();
    }

    /**
     * Configure the device as BLE Central
     */
    public void configureAsCentralDevice() throws SecurityException {
        bluetoothGatt.setCharacteristicNotification(midiInputCharacteristic, true);

        List<BluetoothGattDescriptor> descriptors = midiInputCharacteristic.getDescriptors();
        for (BluetoothGattDescriptor descriptor : descriptors) {
            if (BleUuidUtils.matches(BleUuidUtils.fromShortValue(0x2902), descriptor.getUuid())) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                bluetoothGatt.writeDescriptor(descriptor);
            }
        }

        bluetoothGatt.readCharacteristic(midiInputCharacteristic);
    }

    @Override
    public void setOnMidiInputEventListener(OnMidiInputEventListener midiInputEventListener) {
        midiParser.setMidiInputEventListener(midiInputEventListener);
    }

    @NonNull
    @Override
    public String getDeviceName() throws SecurityException {
        return bluetoothGatt.getDevice().getName();
    }

    /**
     * Obtains device address
     *
     * @return device address
     */
    @NonNull
    public String getDeviceAddress() {
        return bluetoothGatt.getDevice().getAddress();
    }

    /**
     * Parse the MIDI data
     *
     * @param data the MIDI data
     */
    void incomingData(@NonNull byte[] data) {
        midiParser.parse(data);
    }
}

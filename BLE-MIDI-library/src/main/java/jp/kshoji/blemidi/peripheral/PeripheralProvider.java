package jp.kshoji.blemidi.peripheral;

import static android.util.Log.w;
import static java.lang.Thread.sleep;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiInputEventListener;
import jp.kshoji.blemidi.util.BleMidiParser;
import jp.kshoji.blemidi.util.BleUuidUtils;
import jp.kshoji.blemidi.util.Constants;

@SuppressLint("MissingPermission")
public abstract class PeripheralProvider {

    private boolean isAdvertisingStarted = false;

    /**
     * Gatt Services
     */
    private static final UUID SERVICE_DEVICE_INFORMATION = BleUuidUtils.fromShortValue(0x180A);
    private static final UUID SERVICE_BLE_MIDI = UUID.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700");

    /**
     * Gatt Characteristics
     */
    private static final short MANUFACTURER_NAME = 0x2A29;
    private static final short MODEL_NUMBER = 0x2A24;
    private static final UUID CHARACTERISTIC_MANUFACTURER_NAME = BleUuidUtils.fromShortValue(MANUFACTURER_NAME);
    private static final UUID CHARACTERISTIC_MODEL_NUMBER = BleUuidUtils.fromShortValue(MODEL_NUMBER);
    private static final UUID CHARACTERISTIC_BLE_MIDI = UUID.fromString("7772e5db-3868-4112-a1a9-f2669d106bf3");

    /**
     * Gatt Characteristic Descriptor
     */
    private static final UUID DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION = BleUuidUtils.fromShortValue(0x2902);

    private static final int DEVICE_NAME_MAX_LENGTH = 100;
    public final BluetoothGattCharacteristic midiCharacteristic;
    public final Map<String, MidiInputDevice> midiInputDevicesMap = new HashMap<>();
    public final Map<String, MidiOutputDevice> midiOutputDevicesMap = new HashMap<>();
    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private final BluetoothGattService informationGattService;
    private final BluetoothGattService midiGattService;
    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
    };
    private final BluetoothGattCallback disconnectCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) throws SecurityException {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(Constants.TAG, "onConnectionStateChange status: " + status + ", newState: " + newState);
            if (gatt != null) gatt.disconnect();
        }
    };
    public BluetoothGattServer gattServer;
    private boolean gattServiceInitialized = false;
    private String manufacturer = "kshoji.jp";
    private String deviceName = "BLE MIDI";
    final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
            synchronized (midiOutputDevicesMap) {
                MidiOutputDevice midiOutputDevice = midiOutputDevicesMap.get(device.getAddress());
                if (midiOutputDevice != null) {
                    ((PeripheralMidiOutputDevice) midiOutputDevice).setBufferSize(mtu < 23 ? 20 : mtu - 3);
                }
            }
            Log.d(Constants.TAG, "Peripheral onMtuChanged address: " + device.getAddress() + ", mtu: " + mtu);
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            synchronized (this) {
                if (!isAdvertisingStarted) return;
            }
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    onDeviceConnected(device);
                    break;

                case BluetoothProfile.STATE_DISCONNECTED:
                    onDeviceDisconnected(device);
                    break;
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) throws SecurityException {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            UUID characteristicUuid = characteristic.getUuid();
            if (BleUuidUtils.matches(CHARACTERISTIC_BLE_MIDI, characteristicUuid)) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{});
            } else {
                switch (BleUuidUtils.toShortValue(characteristicUuid)) {
                    case MODEL_NUMBER:
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, deviceName.getBytes(StandardCharsets.UTF_8));
                        break;
                    case MANUFACTURER_NAME:
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, manufacturer.getBytes(StandardCharsets.UTF_8));
                        break;
                    default:
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{});
                        break;
                }
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) throws SecurityException {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            if (BleUuidUtils.matches(characteristic.getUuid(), CHARACTERISTIC_BLE_MIDI)) {
                MidiInputDevice midiInputDevice = midiInputDevicesMap.get(device.getAddress());
                if (midiInputDevice != null) {
                    ((PeripheralMidiInputDevice) midiInputDevice).incomingData(value);
                }
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{});
                }
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) throws SecurityException {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            byte[] descriptorValue = descriptor.getValue();
            try {
                System.arraycopy(value, 0, descriptorValue, offset, value.length);
                descriptor.setValue(descriptorValue);
            } catch (IndexOutOfBoundsException ignored) {
            }
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{});
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) throws SecurityException {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            if (offset == 0) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, descriptor.getValue());
            } else {
                final byte[] value = descriptor.getValue();
                byte[] result = new byte[value.length - offset];
                try {
                    System.arraycopy(value, offset, result, 0, result.length);
                } catch (IndexOutOfBoundsException ignored) {
                }
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, result);
            }
        }
    };

    /**
     * Constructor<br />
     * Before constructing the instance, check the Bluetooth availability.
     *
     * @param context the context
     */
    public PeripheralProvider(final Context context) throws UnsupportedOperationException {
        this.context = context.getApplicationContext();

        bluetoothManager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);

        final BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            throw new UnsupportedOperationException("Bluetooth is not available.");
        }

        if (!bluetoothAdapter.isEnabled()) {
            throw new UnsupportedOperationException("Bluetooth is disabled.");
        }

        Log.d(Constants.TAG, "isMultipleAdvertisementSupported:" + bluetoothAdapter.isMultipleAdvertisementSupported());
        if (!bluetoothAdapter.isMultipleAdvertisementSupported()) {
            throw new UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.");
        }

        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        Log.d(Constants.TAG, "bluetoothLeAdvertiser: " + bluetoothLeAdvertiser);
        if (bluetoothLeAdvertiser == null) {
            throw new UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.");
        }

        // Device information service
        informationGattService = new BluetoothGattService(SERVICE_DEVICE_INFORMATION, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        informationGattService.addCharacteristic(new BluetoothGattCharacteristic(CHARACTERISTIC_MANUFACTURER_NAME, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ));
        informationGattService.addCharacteristic(new BluetoothGattCharacteristic(CHARACTERISTIC_MODEL_NUMBER, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ));

        // MIDI service
        midiCharacteristic = new BluetoothGattCharacteristic(CHARACTERISTIC_BLE_MIDI, BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        midiCharacteristic.addDescriptor(descriptor);
        midiCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

        midiGattService = new BluetoothGattService(SERVICE_BLE_MIDI, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        midiGattService.addCharacteristic(midiCharacteristic);
    }

    /**
     * Starts advertising
     */

    public void startAdvertising() throws SecurityException {
        // register Gatt service to Gatt server
        if (gattServer == null) {
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        }

        if (gattServer == null) {
            Log.d(Constants.TAG, "gattServer is null, check Bluetooth is ON.");
            return;
        }

        // these service will be listened.
        while (!gattServiceInitialized) {
            gattServer.clearServices();
            try {
                gattServer.addService(informationGattService);
                while (gattServer.getService(informationGattService.getUuid()) == null) {
                    try {
                        sleep(10);
                    } catch (InterruptedException ignored) {
                    }
                }
                gattServer.addService(midiGattService);// NullPointerException, DeadObjectException thrown here
                while (gattServer.getService(midiGattService.getUuid()) == null) {
                    try {
                        sleep(10);
                    } catch (InterruptedException ignored) {
                    }
                }
                gattServiceInitialized = true;
            } catch (Exception e) {
                Log.d(Constants.TAG, "Adding Service failed, retrying..");

                try {
                    gattServer.clearServices();
                } catch (Throwable ignored) {
                }

                try {
                    sleep(100);
                } catch (InterruptedException ignored) {
                }
            }
        }

        // set up advertising setting
        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .build();

        // set up advertising data
        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .setIncludeTxPowerLevel(true)
                .setIncludeDeviceName(true)
                .build();

        // set up scan result
        AdvertiseData scanResult = new AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid.fromString(SERVICE_DEVICE_INFORMATION.toString()))
                .addServiceUuid(ParcelUuid.fromString(SERVICE_BLE_MIDI.toString()))
                .build();

        bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, scanResult, advertiseCallback);
        synchronized (this) {
            isAdvertisingStarted = true;
        }
    }

    /**
     * Stops advertising
     */
    public void stopAdvertising() throws SecurityException {
        synchronized (this) {
            isAdvertisingStarted = false;
        }
        try {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
        } catch (IllegalStateException ex) {
            w("renetik-android-midi-bt", "bluetoothLeAdvertiser stopAdvertising", ex);
        }
    }

//    /**
//     * Disconnects the specified device
//     *
//     * @param midiOutputDevice the device
//     */
//    public void disconnectDevice(@NonNull MidiOutputDevice midiOutputDevice) {
//        if (!(midiOutputDevice instanceof InternalMidiOutputDevice)) {
//            return;
//        }
//
//        disconnectByDeviceAddress(((InternalMidiOutputDevice) midiOutputDevice).bluetoothDevice);
//    }
//
//    public void disconnectDevice(@NonNull MidiInputDevice midiOutputDevice) {
//        if (!(midiOutputDevice instanceof InternalMidiInputDevice)) {
//            return;
//        }
//        disconnectByDeviceAddress(midiOutputDevice.getDeviceAddress());
//    }

//    /**
//     * Disconnects the device by its address
//     *
//     * @param deviceAddress the device address from {@link android.bluetooth.BluetoothGatt}
//     */
//    private void disconnectByDevice(@NonNull String deviceAddress) throws SecurityException {
//        synchronized (bluetoothDevicesMap) {
//            BluetoothDevice bluetoothDevice = bluetoothDevicesMap.get(deviceAddress);
//            if (bluetoothDevice != null) {
//                gattServer.cancelConnection(bluetoothDevice);
//                bluetoothDevice.connectGatt(context, true, disconnectCallback);
//            }
//        }
//    }

    public void disconnectDevice(@NonNull BluetoothDevice bluetoothDevice) throws SecurityException {
        gattServer.cancelConnection(bluetoothDevice);
        bluetoothDevice.connectGatt(context, true, disconnectCallback);
    }

    /**
     * Terminates provider
     */
    public void terminate() throws SecurityException {
        stopAdvertising();

//        synchronized (bluetoothDevicesMap) {
//            for (BluetoothDevice bluetoothDevice : bluetoothDevicesMap.values()) {
//                gattServer.cancelConnection(bluetoothDevice);
//                bluetoothDevice.connectGatt(context, true, disconnectCallback);
//            }
//            bluetoothDevicesMap.clear();
//        }

        if (gattServer != null) {
            try {
                gattServer.clearServices();
                gattServiceInitialized = false;
            } catch (Throwable ignored) {
                // android.os.DeadObjectException
            }
            try {
                gattServer.close();
            } catch (Throwable ignored) {
                // android.os.DeadObjectException
            }
            gattServer = null;
        }

        synchronized (midiInputDevicesMap) {
            for (MidiInputDevice midiInputDevice : midiInputDevicesMap.values()) {
                ((PeripheralMidiInputDevice) midiInputDevice).stop();
                midiInputDevice.setOnMidiInputEventListener(null);
            }
            midiInputDevicesMap.clear();
        }

        synchronized (midiOutputDevicesMap) {
            midiOutputDevicesMap.clear();
        }
    }

    protected void onDeviceConnected(@NonNull BluetoothDevice device) {
//        synchronized (bluetoothDevicesMap) {
//            bluetoothDevicesMap.put(device.getAddress(), device);
//        }
    }

    protected void onDeviceDisconnected(@NonNull BluetoothDevice device) {
        synchronized (midiInputDevicesMap) {
            midiInputDevicesMap.remove(device.getAddress());
        }
        synchronized (midiOutputDevicesMap) {
            midiOutputDevicesMap.remove(device.getAddress());
        }
//        synchronized (bluetoothDevicesMap) {
//            bluetoothDevicesMap.remove(device.getAddress());
//        }
    }

    public void setManufacturer(@NonNull String manufacturer) {
        // length check
        byte[] manufacturerBytes = manufacturer.getBytes(StandardCharsets.UTF_8);
        if (manufacturerBytes.length > DEVICE_NAME_MAX_LENGTH) {
            // shorten
            byte[] bytes = new byte[DEVICE_NAME_MAX_LENGTH];
            System.arraycopy(manufacturerBytes, 0, bytes, 0, DEVICE_NAME_MAX_LENGTH);
            this.manufacturer = new String(bytes, StandardCharsets.UTF_8);
        } else {
            this.manufacturer = manufacturer;
        }
    }

    public void setDeviceName(@NonNull String deviceName) {
        // length check
        byte[] deviceNameBytes = deviceName.getBytes(StandardCharsets.UTF_8);
        if (deviceNameBytes.length > DEVICE_NAME_MAX_LENGTH) {
            // shorten
            byte[] bytes = new byte[DEVICE_NAME_MAX_LENGTH];
            System.arraycopy(deviceNameBytes, 0, bytes, 0, DEVICE_NAME_MAX_LENGTH);
            this.deviceName = new String(bytes, StandardCharsets.UTF_8);
        } else {
            this.deviceName = deviceName;
        }
    }

}




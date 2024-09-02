package renetik.android.midi.bluetooth.central;

import static renetik.android.midi.bluetooth.util.Constants.TAG;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import renetik.android.midi.bluetooth.device.MidiInputDevice;
import renetik.android.midi.bluetooth.device.MidiOutputDevice;

@SuppressLint("MissingPermission")
public class CentralCallback extends BluetoothGattCallback {
    private volatile static Object gattDiscoverServicesLock = null;
    private final Map<String, Set<CentralMidiInputDevice>> midiInputDevicesMap = new HashMap<>();
    private final Map<String, Set<CentralMidiOutputDevice>> midiOutputDevicesMap = new HashMap<>();
    private final Map<String, List<BluetoothGatt>> deviceAddressGattMap = new HashMap<>();
    private final Context context;
    private CentralDeviceAttachedListener midiDeviceAttachedListener;
    private CentralDeviceDetachedListener midiDeviceDetachedListener;
    private boolean needsBonding = false;
    private BondingBroadcastReceiver bondingBroadcastReceiver;

    public CentralCallback(@NonNull final Context context) {
        super();
        this.context = context;
    }

    boolean isConnected(@NonNull BluetoothDevice device) {
        synchronized (deviceAddressGattMap) {
            return deviceAddressGattMap.containsKey(device.getAddress());
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) throws SecurityException {
        super.onConnectionStateChange(gatt, status, newState);
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            if (deviceAddressGattMap.containsKey(gatt.getDevice().getAddress())) {
                return;
            }
            // process a device for the same time
            while (gattDiscoverServicesLock != null) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                }
            }
            if (deviceAddressGattMap.containsKey(gatt.getDevice().getAddress())) {
                // same device has already registered
                return;
            }
            gattDiscoverServicesLock = gatt;
            if (!gatt.discoverServices()) {
                // already disconnected
                disconnectByDeviceAddress(gatt.getDevice().getAddress());
                gattDiscoverServicesLock = null;
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            disconnectByDeviceAddress(gatt.getDevice().getAddress());
            gattDiscoverServicesLock = null;
        }
    }

    @SuppressLint({"NewApi", "MissingPermission"})
    @Override
    public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);

        if (status != BluetoothGatt.GATT_SUCCESS) {
            gattDiscoverServicesLock = null;
            return;
        }

        final String gattDeviceAddress = gatt.getDevice().getAddress();

        // request maximum MTU size
        boolean result = gatt.requestMtu(517); // GATT_MAX_MTU_SIZE defined at `stack/include/gatt_api.h`
        Log.d(TAG, "Central requestMtu address: " + gatt.getDevice().getAddress() + ", succeed: " + result);

        // find MIDI Input device
        synchronized (midiInputDevicesMap) {
            if (midiInputDevicesMap.containsKey(gattDeviceAddress)) {
                Set<CentralMidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gattDeviceAddress);
                if (midiInputDevices != null) {
                    for (CentralMidiInputDevice midiInputDevice : midiInputDevices) {
                        midiInputDevice.stop();
                        midiInputDevice.setOnMidiInputEventListener(null);
                    }
                }
                midiInputDevicesMap.remove(gattDeviceAddress);
            }
        }

        CentralMidiInputDevice midiInputDevice = null;
        try {
            midiInputDevice = new CentralMidiInputDevice(context, gatt);
        } catch (IllegalArgumentException iae) {
            Log.d(TAG, iae.getMessage());
        }
        if (midiInputDevice != null) {
            synchronized (midiInputDevicesMap) {
                Set<CentralMidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gattDeviceAddress);
                if (midiInputDevices == null) {
                    midiInputDevices = new HashSet<>();
                    midiInputDevicesMap.put(gattDeviceAddress, midiInputDevices);
                }

                midiInputDevices.add(midiInputDevice);
            }

            // don't notify if the same device already connected
            if (!deviceAddressGattMap.containsKey(gattDeviceAddress)) {
                if (midiDeviceAttachedListener != null) {
                    midiDeviceAttachedListener.onMidiInputDeviceAttached(midiInputDevice);
                }
            }
        }

        // find MIDI Output device
        synchronized (midiOutputDevicesMap) {
            Set<CentralMidiOutputDevice> midiOutputDevices = midiOutputDevicesMap.get(gattDeviceAddress);
            if (midiOutputDevices != null) {
                for (MidiOutputDevice midiOutputDevice : midiOutputDevices) {
                    midiOutputDevice.stop();
                }
            }
            midiOutputDevicesMap.remove(gattDeviceAddress);
        }

        CentralMidiOutputDevice midiOutputDevice = null;
        try {
            midiOutputDevice = new CentralMidiOutputDevice(context, gatt);
        } catch (IllegalArgumentException iae) {
            Log.d(TAG, iae.getMessage());
        }
        if (midiOutputDevice != null) {
            synchronized (midiOutputDevicesMap) {
                Set<CentralMidiOutputDevice> midiOutputDevices = midiOutputDevicesMap.get(gattDeviceAddress);
                if (midiOutputDevices == null) {
                    midiOutputDevices = new HashSet<>();
                    midiOutputDevicesMap.put(gattDeviceAddress, midiOutputDevices);
                }

                midiOutputDevices.add(midiOutputDevice);
            }

            // don't notify if the same device already connected
            if (!deviceAddressGattMap.containsKey(gattDeviceAddress)) {
                if (midiDeviceAttachedListener != null) {
                    midiDeviceAttachedListener.onMidiOutputDeviceAttached(midiOutputDevice);
                }
            }
        }

        if (midiInputDevice != null || midiOutputDevice != null) {
            synchronized (deviceAddressGattMap) {
                List<BluetoothGatt> bluetoothGatts = deviceAddressGattMap.get(gattDeviceAddress);
                if (bluetoothGatts == null) {
                    bluetoothGatts = new ArrayList<>();
                    deviceAddressGattMap.put(gattDeviceAddress, bluetoothGatts);
                }
                bluetoothGatts.add(gatt);
            }

            if (needsBonding) {
                // Create bond and configure Gatt, if this is BLE MIDI device
                BluetoothDevice bluetoothDevice = gatt.getDevice();
                if (bluetoothDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
                    bluetoothDevice.createBond();
                    try {
                        bluetoothDevice.setPairingConfirmation(true);
                    } catch (Throwable t) {
                        // SecurityException if android.permission.BLUETOOTH_PRIVILEGED not available
                        Log.d(TAG, t.getMessage());
                    }

                    if (bondingBroadcastReceiver != null) {
                        context.unregisterReceiver(bondingBroadcastReceiver);
                    }
                    bondingBroadcastReceiver = new BondingBroadcastReceiver(midiInputDevice, midiOutputDevice);
                    IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                    context.registerReceiver(bondingBroadcastReceiver, filter);
                }
            } else {
                if (midiInputDevice != null) midiInputDevice.configureAsCentralDevice();
                if (midiOutputDevice != null) midiOutputDevice.configureAsCentralDevice();
            }

            // Set the connection priority to high(for low latency)
            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
        }

        // all finished
        gattDiscoverServicesLock = null;
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);

        Set<CentralMidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gatt.getDevice().getAddress());
        if (midiInputDevices != null) {
            for (CentralMidiInputDevice midiInputDevice : midiInputDevices) {
                midiInputDevice.incomingData(characteristic.getValue());
            }
        }
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        super.onMtuChanged(gatt, mtu, status);

        synchronized (midiOutputDevicesMap) {
            Set<CentralMidiOutputDevice> midiOutputDevices = midiOutputDevicesMap.get(gatt.getDevice().getAddress());
            if (midiOutputDevices != null) {
                for (CentralMidiOutputDevice midiOutputDevice : midiOutputDevices) {
                    midiOutputDevice.setBufferSize(mtu < 23 ? 20 : mtu - 3);
                }
            }
        }
        Log.d(TAG, "Central onMtuChanged address: " + gatt.getDevice().getAddress() + ", mtu: " + mtu + ", status: " + status);
    }

    void disconnectDevice(@NonNull MidiInputDevice midiInputDevice) {
        if (!(midiInputDevice instanceof CentralMidiInputDevice)) {
            return;
        }

        disconnectByDeviceAddress(midiInputDevice.deviceAddress());
    }

    void disconnectDevice(@NonNull MidiOutputDevice midiOutputDevice) {
        if (!(midiOutputDevice instanceof CentralMidiOutputDevice)) {
            return;
        }

        disconnectByDeviceAddress(midiOutputDevice.getDeviceAddress());
    }

    private void disconnectByDeviceAddress(@NonNull String deviceAddress) throws SecurityException {
        synchronized (deviceAddressGattMap) {
            List<BluetoothGatt> bluetoothGatts = deviceAddressGattMap.get(deviceAddress);

            if (bluetoothGatts != null) {
                for (BluetoothGatt bluetoothGatt : bluetoothGatts) {
                    bluetoothGatt.disconnect();
                    bluetoothGatt.close();
                }

                deviceAddressGattMap.remove(deviceAddress);
            }
        }

        synchronized (midiInputDevicesMap) {
            Set<CentralMidiInputDevice> midiInputDevices = midiInputDevicesMap.get(deviceAddress);
            if (midiInputDevices != null) {
                midiInputDevicesMap.remove(deviceAddress);
                for (CentralMidiInputDevice midiInputDevice : midiInputDevices) {
                    midiInputDevice.stop();
                    midiInputDevice.setOnMidiInputEventListener(null);
                    if (midiDeviceDetachedListener != null) {
                        midiDeviceDetachedListener.onMidiInputDeviceDetached(midiInputDevice);
                    }
                }
                midiInputDevices.clear();
            }
        }

        synchronized (midiOutputDevicesMap) {
            Set<CentralMidiOutputDevice> midiOutputDevices = midiOutputDevicesMap.get(deviceAddress);
            if (midiOutputDevices != null) {
                midiOutputDevicesMap.remove(deviceAddress);

                for (CentralMidiOutputDevice midiOutputDevice : midiOutputDevices) {
                    midiOutputDevice.stop();
                    if (midiDeviceDetachedListener != null) {
                        midiDeviceDetachedListener.onMidiOutputDeviceDetached(midiOutputDevice);
                    }
                }
                midiOutputDevices.clear();
            }
        }
    }

    public void terminate() throws SecurityException {
        synchronized (deviceAddressGattMap) {
            for (List<BluetoothGatt> bluetoothGatts : deviceAddressGattMap.values()) {
                if (bluetoothGatts != null) {
                    for (BluetoothGatt bluetoothGatt : bluetoothGatts) {
                        bluetoothGatt.disconnect();
                        bluetoothGatt.close();
                    }
                }
            }
            deviceAddressGattMap.clear();
        }

        synchronized (midiInputDevicesMap) {
            for (Set<CentralMidiInputDevice> midiInputDevices : midiInputDevicesMap.values()) {
                for (CentralMidiInputDevice midiInputDevice : midiInputDevices) {
                    midiInputDevice.stop();
                    midiInputDevice.setOnMidiInputEventListener(null);
                }
                midiInputDevices.clear();
            }
            midiInputDevicesMap.clear();
        }

        synchronized (midiOutputDevicesMap) {
            for (Set<CentralMidiOutputDevice> midiOutputDevices : midiOutputDevicesMap.values()) {
                for (MidiOutputDevice midiOutputDevice : midiOutputDevices) {
                    midiOutputDevice.stop();
                }

                midiOutputDevices.clear();
            }
            midiOutputDevicesMap.clear();
        }

        if (bondingBroadcastReceiver != null) {
            context.unregisterReceiver(bondingBroadcastReceiver);
            bondingBroadcastReceiver = null;
        }
    }

    public void setNeedsBonding(boolean needsBonding) {
        this.needsBonding = needsBonding;
    }

    @NonNull
    public Set<CentralMidiInputDevice> getMidiInputDevices() {
        Collection<Set<CentralMidiInputDevice>> values = midiInputDevicesMap.values();

        Set<CentralMidiInputDevice> result = new HashSet<>();
        for (Set<CentralMidiInputDevice> value : values) {
            result.addAll(value);
        }

        return Collections.unmodifiableSet(result);
    }

    @NonNull
    public Set<CentralMidiOutputDevice> getMidiOutputDevices() {
        Collection<Set<CentralMidiOutputDevice>> values = midiOutputDevicesMap.values();

        Set<CentralMidiOutputDevice> result = new HashSet<>();
        for (Set<CentralMidiOutputDevice> value : values) {
            result.addAll(value);
        }

        return Collections.unmodifiableSet(result);
    }

    public void setOnMidiDeviceAttachedListener(@Nullable CentralDeviceAttachedListener midiDeviceAttachedListener) {
        this.midiDeviceAttachedListener = midiDeviceAttachedListener;
    }

    public void setOnMidiDeviceDetachedListener(@Nullable CentralDeviceDetachedListener midiDeviceDetachedListener) {
        this.midiDeviceDetachedListener = midiDeviceDetachedListener;
    }

    private class BondingBroadcastReceiver extends BroadcastReceiver {
        final MidiInputDevice midiInputDevice;
        final MidiOutputDevice midiOutputDevice;

        BondingBroadcastReceiver(@Nullable MidiInputDevice midiInputDevice, @Nullable MidiOutputDevice midiOutputDevice) {
            this.midiInputDevice = midiInputDevice;
            this.midiOutputDevice = midiOutputDevice;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);

                if (state == BluetoothDevice.BOND_BONDED) {
                    // successfully bonded
                    context.unregisterReceiver(this);
                    bondingBroadcastReceiver = null;

                    if (midiInputDevice != null) {
                        ((CentralMidiInputDevice) midiInputDevice).configureAsCentralDevice();
                    }
                    if (midiOutputDevice != null) {
                        ((CentralMidiOutputDevice) midiOutputDevice).configureAsCentralDevice();
                    }
                }
            }
        }
    }
}
package jp.kshoji.blemidi.central;

import static android.os.ParcelUuid.fromString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanFilter.Builder;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiScanStatusListener;

@SuppressLint("MissingPermission")
public class CentralProvider {
    private final BluetoothAdapter bluetoothAdapter;
    private final Context context;
    private final Handler handler;
    private final CentralCallback midiCallback;
    private final BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord) throws SecurityException {

            if (bluetoothDevice.getType() != BluetoothDevice.DEVICE_TYPE_LE && bluetoothDevice.getType() != BluetoothDevice.DEVICE_TYPE_DUAL) {
                return;
            }

            if (context instanceof Activity) {
                ((Activity) context).runOnUiThread(new Runnable() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void run() {
                        bluetoothDevice.connectGatt(context, true, midiCallback);
                    }
                });
            } else {
                if (Thread.currentThread() == context.getMainLooper().getThread()) {
                    bluetoothDevice.connectGatt(context, true, midiCallback);
                } else {
                    handler.post(new Runnable() {
                        @SuppressLint("MissingPermission")
                        @Override
                        public void run() {
                            bluetoothDevice.connectGatt(context, true, midiCallback);
                        }
                    });
                }
            }
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult result : results) onScan(result);
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) throws SecurityException {
            super.onScanResult(callbackType, result);
            onScan(result);
        }
    };

    private void onScan(ScanResult result) {
        //                if (callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
        final BluetoothDevice device = result.getDevice();
//                    if (device.getType() != BluetoothDevice.DEVICE_TYPE_LE &&
//                            device.getType() != BluetoothDevice.DEVICE_TYPE_DUAL)
//                        return;
        if (!midiCallback.isConnected(device)) handler.post(new Runnable() {
            @Override
            public void run() throws SecurityException {
                device.connectGatt(CentralProvider.this.context, true, midiCallback);
            }
        });
//                }
    }

    private volatile boolean isScanning = false;
    private Runnable stopScanRunnable = null;
    private OnMidiScanStatusListener onMidiScanStatusListener;

    public CentralProvider(@NonNull final Context context) throws UnsupportedOperationException, SecurityException {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
            throw new UnsupportedOperationException("Bluetooth LE not supported on this device.");
        bluetoothAdapter = ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (bluetoothAdapter == null)
            throw new UnsupportedOperationException("Bluetooth is not available.");
        if (!bluetoothAdapter.isEnabled())
            throw new UnsupportedOperationException("Bluetooth is disabled.");
        this.context = context;
        this.midiCallback = new CentralCallback(context);
        this.handler = new Handler(context.getMainLooper());
    }

    @SuppressLint("MissingPermission")
    public void connectGatt(BluetoothDevice bluetoothDevice) {
        bluetoothDevice.connectGatt(context, true, midiCallback);
    }

    public void setRequestPairing(boolean needsPairing) {
        midiCallback.setNeedsBonding(needsPairing);
    }

    @SuppressLint({"Deprecation", "NewApi"})
    public void startScanDevice(int timeoutInMilliSeconds) throws SecurityException {
        BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        List<ScanFilter> scanFilters = new ArrayList<>();
        scanFilters.add(new Builder().setServiceUuid(fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")).build());
//        List<ScanFilter> scanFilters = BleMidiDeviceUtils.getBleMidiScanFilters(context);
        ScanSettings.Builder settings = new ScanSettings.Builder()
                .setReportDelay(5000)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
//                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
//                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                ;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setLegacy(false);
        bluetoothLeScanner.startScan(scanFilters, settings.build(), scanCallback);
        isScanning = true;

        if (onMidiScanStatusListener != null) {
            onMidiScanStatusListener.onMidiScanStatusChanged(isScanning);
        }

        if (stopScanRunnable != null) {
            handler.removeCallbacks(stopScanRunnable);
        }

        if (isScanning && timeoutInMilliSeconds > 0) {
            stopScanRunnable = new Runnable() {
                @Override
                public void run() {
                    stopScanDevice();
                    isScanning = false;
                    if (onMidiScanStatusListener != null) {
                        onMidiScanStatusListener.onMidiScanStatusChanged(isScanning);
                    }
                }
            };
            handler.postDelayed(stopScanRunnable, timeoutInMilliSeconds);
        }
    }

    @SuppressLint({"Deprecation", "NewApi"})
    public void stopScanDevice() throws SecurityException {
        try {
            final BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            bluetoothLeScanner.flushPendingScanResults(scanCallback);
            bluetoothLeScanner.stopScan(scanCallback);
        } catch (Throwable ignored) {
            // NullPointerException on Bluetooth is OFF
        }

        if (stopScanRunnable != null) {
            handler.removeCallbacks(stopScanRunnable);
            stopScanRunnable = null;
        }

        isScanning = false;
        if (onMidiScanStatusListener != null) {
            onMidiScanStatusListener.onMidiScanStatusChanged(isScanning);
        }
    }

    public void disconnectDevice(@NonNull MidiInputDevice midiInputDevice) {
        midiCallback.disconnectDevice(midiInputDevice);
    }

    public void disconnectDevice(@NonNull MidiOutputDevice midiOutputDevice) {
        midiCallback.disconnectDevice(midiOutputDevice);
    }

    @NonNull
    public Set<CentralMidiInputDevice> getMidiInputDevices() {
        return midiCallback.getMidiInputDevices();
    }

    @NonNull
    public Set<CentralMidiOutputDevice> getMidiOutputDevices() {
        return midiCallback.getMidiOutputDevices();
    }

    public void setOnMidiScanStatusListener(@Nullable OnMidiScanStatusListener onMidiScanStatusListener) {
        this.onMidiScanStatusListener = onMidiScanStatusListener;
    }

    public void setOnMidiDeviceAttachedListener(@Nullable CentralDeviceAttachedListener midiDeviceAttachedListener) {
        this.midiCallback.setOnMidiDeviceAttachedListener(midiDeviceAttachedListener);
    }

    public void setOnMidiDeviceDetachedListener(@Nullable CentralDeviceDetachedListener midiDeviceDetachedListener) {
        this.midiCallback.setOnMidiDeviceDetachedListener(midiDeviceDetachedListener);
    }

    public void terminate() {
        midiCallback.terminate();
        stopScanDevice();
    }
}

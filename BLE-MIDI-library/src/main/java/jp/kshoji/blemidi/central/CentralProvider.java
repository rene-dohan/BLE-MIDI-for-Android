package jp.kshoji.blemidi.central;

import static jp.kshoji.blemidi.util.BleUtils.SELECT_DEVICE_REQUEST_CODE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.companion.AssociationRequest;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.IntentSender;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Set;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiScanStatusListener;
import jp.kshoji.blemidi.util.BleMidiDeviceUtils;
import jp.kshoji.blemidi.util.Constants;

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

    private final ScanCallback scanCallback;
    private boolean useCompanionDeviceSetup;
    private volatile boolean isScanning = false;
    private Runnable stopScanRunnable = null;
    private OnMidiScanStatusListener onMidiScanStatusListener;

    public CentralProvider(@NonNull final Context context) throws UnsupportedOperationException, SecurityException {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            throw new UnsupportedOperationException("Bluetooth LE not supported on this device.");
        }

        try {
            // Checks `android.software.companion_device_setup` feature specified at AndroidManifest.xml
            FeatureInfo[] reqFeatures = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_CONFIGURATIONS).reqFeatures;
            if (reqFeatures != null) {
                for (FeatureInfo feature : reqFeatures) {
                    if (feature == null) {
                        continue;
                    }
                    if (PackageManager.FEATURE_COMPANION_DEVICE_SETUP.equals(feature.name)) {
                        useCompanionDeviceSetup = true;
                        break;
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        bluetoothAdapter = ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (bluetoothAdapter == null) {
            throw new UnsupportedOperationException("Bluetooth is not available.");
        }

        if (!bluetoothAdapter.isEnabled()) {
            throw new UnsupportedOperationException("Bluetooth is disabled.");
        }

        this.context = context;
        this.midiCallback = new CentralCallback(context);
        this.handler = new Handler(context.getMainLooper());

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) throws SecurityException {
                super.onScanResult(callbackType, result);

                if (callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
                    final BluetoothDevice bluetoothDevice = result.getDevice();

                    if (bluetoothDevice.getType() != BluetoothDevice.DEVICE_TYPE_LE && bluetoothDevice.getType() != BluetoothDevice.DEVICE_TYPE_DUAL) {
                        return;
                    }

                    if (!midiCallback.isConnected(bluetoothDevice)) {
                        if (context instanceof Activity) {
                            ((Activity) context).runOnUiThread(new Runnable() {
                                @Override
                                public void run() throws SecurityException {
                                    bluetoothDevice.connectGatt(CentralProvider.this.context, true, midiCallback);
                                }
                            });
                        } else {
                            if (Thread.currentThread() == context.getMainLooper().getThread()) {
                                bluetoothDevice.connectGatt(CentralProvider.this.context, true, midiCallback);
                            } else {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() throws SecurityException {
                                        bluetoothDevice.connectGatt(CentralProvider.this.context, true, midiCallback);
                                    }
                                });
                            }
                        }
                    }
                }
            }
        };
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && useCompanionDeviceSetup) {
            final CompanionDeviceManager deviceManager = context.getSystemService(CompanionDeviceManager.class);
            final AssociationRequest associationRequest = BleMidiDeviceUtils.getBleMidiAssociationRequest(context);
            // TODO: use another associate API when SDK_INT >= VERSION_CODES.TIRAMISU
            try {
                deviceManager.associate(associationRequest,
                        new CompanionDeviceManager.Callback() {
                            @Override
                            public void onDeviceFound(final IntentSender intentSender) {
                                try {
                                    ((Activity) context).startIntentSenderForResult(intentSender, SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0);
                                } catch (IntentSender.SendIntentException e) {
                                    Log.e(Constants.TAG, e.getMessage(), e);
                                }
                            }

                            @Override
                            public void onFailure(final CharSequence error) {
                                Log.e(Constants.TAG, "onFailure error: " + error);
                            }
                        }, null);
            } catch (IllegalStateException ignored) {
                Log.e(Constants.TAG, ignored.getMessage(), ignored);
                // Must declare uses-feature android.software.companion_device_setup in manifest to use this API
                // fallback to use BluetoothLeScanner
                useCompanionDeviceSetup = false;

                BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                List<ScanFilter> scanFilters = BleMidiDeviceUtils.getBleMidiScanFilters(context);
                ScanSettings scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
                bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback);
                isScanning = true;
            }
        } else {
            BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            List<ScanFilter> scanFilters = BleMidiDeviceUtils.getBleMidiScanFilters(context);
            ScanSettings scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback);
            isScanning = true;
        }

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && useCompanionDeviceSetup) {
                // using CompanionDeviceManager, do nothing
                return;
            } else {
                final BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                bluetoothLeScanner.flushPendingScanResults(scanCallback);
                bluetoothLeScanner.stopScan(scanCallback);
            }
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

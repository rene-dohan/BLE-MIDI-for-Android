package jp.kshoji.blemidi.peripheral;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.listener.OnMidiInputEventListener;
import jp.kshoji.blemidi.util.BleMidiParser;

@SuppressLint("MissingPermission")
public class PeripheralMidiInputDevice extends MidiInputDevice {
    public final BluetoothDevice bluetoothDevice;

    private final BleMidiParser midiParser = new BleMidiParser(this);

    public PeripheralMidiInputDevice(@NonNull BluetoothDevice bluetoothDevice) {
        super();
        this.bluetoothDevice = bluetoothDevice;
    }

    public void stop() {
        midiParser.stop();
    }

    @Override
    public void setOnMidiInputEventListener(OnMidiInputEventListener midiInputEventListener) {
        midiParser.setMidiInputEventListener(midiInputEventListener);
    }

    @NonNull
    @Override
    public String deviceName() throws SecurityException {
        if (TextUtils.isEmpty(bluetoothDevice.getName())) {
            return bluetoothDevice.getAddress();
        }
        return bluetoothDevice.getName();
    }

    void incomingData(@NonNull byte[] data) {
        midiParser.parse(data);
    }

    @NonNull
    public String deviceAddress() {
        return bluetoothDevice.getAddress();
    }
}
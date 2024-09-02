package renetik.android.midi.bluetooth.peripheral;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import renetik.android.midi.bluetooth.device.MidiInputDevice;
import renetik.android.midi.bluetooth.util.BleMidiParser;
import renetik.android.midi.bluetooth.listener.OnMidiInputEventListener;

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
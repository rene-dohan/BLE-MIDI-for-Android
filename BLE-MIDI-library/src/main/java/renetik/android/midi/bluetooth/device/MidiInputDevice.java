package renetik.android.midi.bluetooth.device;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import renetik.android.midi.bluetooth.listener.OnMidiInputEventListener;

/**
 * Represents BLE MIDI Input Device
 *
 * @author K.Shoji
 */
public abstract class MidiInputDevice {

    /**
     * Attaches {@link OnMidiInputEventListener}
     *
     * @param midiInputEventListener the listener
     */
    public abstract void setOnMidiInputEventListener(@Nullable OnMidiInputEventListener midiInputEventListener);

    /**
     * Obtains the device name
     *
     * @return device name
     */
    @NonNull
    public abstract String deviceName();

    /**
     * Obtains the device address
     *
     * @return device address
     */
    @NonNull
    public abstract String deviceAddress();

    @NonNull
    @Override
    public final String toString() {
        return deviceName();
    }
}

package renetik.android.midi.bluetooth.listener;

import androidx.annotation.NonNull;

import renetik.android.midi.bluetooth.device.MidiInputDevice;
import renetik.android.midi.bluetooth.device.MidiOutputDevice;

/**
 * Listener for MIDI detached events
 *
 * @author K.Shoji
 */
public interface OnMidiDeviceDetachedListener {

    /**
     * MIDI input device has been detached
     *
     * @param midiInputDevice detached MIDI Input device
     */
    void onMidiInputDeviceDetached(@NonNull MidiInputDevice midiInputDevice);

    /**
     * MIDI output device has been detached
     *
     * @param midiOutputDevice detached MIDI Output device
     */
    void onMidiOutputDeviceDetached(@NonNull MidiOutputDevice midiOutputDevice);
}

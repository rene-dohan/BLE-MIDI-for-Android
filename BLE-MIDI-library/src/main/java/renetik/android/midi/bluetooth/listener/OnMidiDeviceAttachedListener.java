package renetik.android.midi.bluetooth.listener;

import androidx.annotation.NonNull;

import renetik.android.midi.bluetooth.device.MidiInputDevice;
import renetik.android.midi.bluetooth.device.MidiOutputDevice;

/**
 * Listener for MIDI attached events
 *
 * @author K.Shoji
 */
public interface OnMidiDeviceAttachedListener {

    /**
     * MIDI input device has been attached
     *
     * @param midiInputDevice attached MIDI Input device
     */
    void onMidiInputDeviceAttached(@NonNull MidiInputDevice midiInputDevice);

    /**
     * MIDI output device has been attached
     *
     * @param midiOutputDevice attached MIDI Output device
     */
    void onMidiOutputDeviceAttached(@NonNull MidiOutputDevice midiOutputDevice);
}

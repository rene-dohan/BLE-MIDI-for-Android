package jp.kshoji.blemidi.central;

import androidx.annotation.NonNull;

public interface CentralDeviceAttachedListener {
    void onMidiInputDeviceAttached(@NonNull CentralMidiInputDevice midiInputDevice);

    void onMidiOutputDeviceAttached(@NonNull CentralMidiOutputDevice midiOutputDevice);
}

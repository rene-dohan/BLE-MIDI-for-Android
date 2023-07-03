package jp.kshoji.blemidi.central;

import androidx.annotation.NonNull;

public interface CentralDeviceDetachedListener {
    void onMidiInputDeviceDetached(@NonNull CentralMidiInputDevice midiInputDevice);

    void onMidiOutputDeviceDetached(@NonNull CentralMidiOutputDevice midiOutputDevice);
}

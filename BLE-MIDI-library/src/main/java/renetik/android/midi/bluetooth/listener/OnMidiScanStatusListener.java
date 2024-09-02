package renetik.android.midi.bluetooth.listener;

/**
 * Listener for Bluetooth LE Scanning status change events.
 *
 * @author K.Shoji
 */
public interface OnMidiScanStatusListener {

    /**
     * The scanning status changed
     *
     * @param isScanning scanning status
     */
    void onMidiScanStatusChanged(boolean isScanning);
}

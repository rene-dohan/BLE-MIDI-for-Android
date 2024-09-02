package renetik.android.midi.bluetooth.central

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import renetik.android.midi.bluetooth.device.MidiInputDevice
import renetik.android.midi.bluetooth.listener.OnMidiInputEventListener
import renetik.android.midi.bluetooth.util.BleMidiDeviceUtils.inputCharacteristic
import renetik.android.midi.bluetooth.util.BleMidiDeviceUtils.midiService
import renetik.android.midi.bluetooth.util.BleMidiParser
import renetik.android.midi.bluetooth.util.BleUuidUtils
import renetik.android.core.kotlin.unexpected

@SuppressLint("MissingPermission")
class CentralMidiInputDevice(context: Context,
    private val bluetoothGatt: BluetoothGatt
) : MidiInputDevice() {

    private val inputCharacteristic: BluetoothGattCharacteristic?

    fun configureAsCentralDevice() {
        bluetoothGatt.setCharacteristicNotification(inputCharacteristic, true)
        val descriptors = inputCharacteristic!!.descriptors
        for (descriptor in descriptors) {
            if (BleUuidUtils.matches(BleUuidUtils.fromShortValue(0x2902),
                    descriptor.uuid)) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                bluetoothGatt.writeDescriptor(descriptor)
            }
        }
        bluetoothGatt.readCharacteristic(inputCharacteristic)
    }

    override fun deviceName(): String = bluetoothGatt.device.name
    override fun deviceAddress(): String = bluetoothGatt.device.address
    private var midiParser: BleMidiParser? = null
    private var midiInputEventListener: OnMidiInputEventListener? = null

    init {
        val midiService = midiService(context, bluetoothGatt) ?: unexpected(
            "MIDI GattService not found from '${bluetoothGatt.device.name}'. Service UUIDs:"
                    + bluetoothGatt.services.map { it.uuid }.toTypedArray()
                .contentToString()
        )
        inputCharacteristic = inputCharacteristic(context, midiService) ?: unexpected(
            "MIDI Input GattCharacteristic not found. Service UUID:" + midiService.uuid
        )
    }

    override fun setOnMidiInputEventListener(
        midiInputEventListener: OnMidiInputEventListener?) {
        this.midiInputEventListener = midiInputEventListener
    }

    fun start() {
        midiParser = BleMidiParser(this)
        midiParser!!.setMidiInputEventListener(midiInputEventListener)
    }

    fun stop() {
        midiParser!!.stop()
        midiParser = null
    }

    fun incomingData(data: ByteArray) {
        if (midiParser != null) midiParser!!.parse(data)
    }
}

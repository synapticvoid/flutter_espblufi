package synapticvoid.espblufi

import android.bluetooth.BluetoothDevice


data class BLEDevice(val device: BluetoothDevice, val rssi: Int)

sealed class BLEScanEvent {
    companion object {
        const val TYPE_IDLE = 1
        const val TYPE_IN_PROGRESS = 2
    }

    object Idle : BLEScanEvent()
    data class InProgress(val devices: List<BLEDevice>) : BLEScanEvent()
    data class Error(val eventType: Int, val errorCode: Int, val message: String) : BLEScanEvent()
}

sealed class BlufiEvent {
    companion object {
        const val TYPE_CONNECTION_STATE = 1
        const val TYPE_CUSTOM_DATA = 2
    }

    data class Error(val eventType: Int, val errorCode: Int, val message: String) : BlufiEvent()

    data class ConnectionState(
        val connected: Boolean,
        val errorCode: Int = 0,
        val errorMessage: String = "",
    ) : BlufiEvent()

    data class CustomDataReceived(
        val data: String,
        val errorCode: Int = 0,
        val errorMessage: String = "",
    ) : BlufiEvent()
}
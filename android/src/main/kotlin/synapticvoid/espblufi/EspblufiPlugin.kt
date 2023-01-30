package synapticvoid.espblufi


import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import blufi.espressif.BlufiCallback
import blufi.espressif.BlufiClient
import blufi.espressif.params.BlufiParameter
import blufi.espressif.response.BlufiScanResult
import blufi.espressif.response.BlufiStatusResponse
import blufi.espressif.response.BlufiVersionResponse
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.util.*

private const val TAG = "EspblufiPlugin"

private const val REQUEST_PERMISSION = 1

private const val GATT_WRITE_TIMEOUT = 5000L

private const val DEFAULT_MTU_LENGTH = 512

/** EspblufiPlugin */
class EspblufiPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.RequestPermissionsResultListener {

    private var resultDeviceVersion: Result? = null
    private var scanResultsSink: EventChannel.EventSink? = null
    private var eventsSink: EventChannel.EventSink? = null

    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel
    private lateinit var scanResultsChannel: EventChannel
    private lateinit var stateChannel: EventChannel

    private lateinit var context: Context
    lateinit var binding: ActivityPluginBinding

    // BLE
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var scanning = false
    private lateinit var handler: Handler
    private val leDevices = mutableListOf<BLEDevice>()

    // Blufi
    private var device: BluetoothDevice? = null
    private var blufiClient: BlufiClient? = null
    private var connected: Boolean = false

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "espblufi")
        channel.setMethodCallHandler(this)

        scanResultsChannel =
            EventChannel(flutterPluginBinding.binaryMessenger, "espblufi/scanResults")
        scanResultsChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(obj: Any?, sink: EventChannel.EventSink?) {
                scanResultsSink = sink
            }

            override fun onCancel(obj: Any?) {
                scanResultsSink = null
            }
        })

        stateChannel = EventChannel(flutterPluginBinding.binaryMessenger, "espblufi/events")
        stateChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(obj: Any?, sink: EventChannel.EventSink?) {
                eventsSink = sink
            }

            override fun onCancel(obj: Any?) {
                eventsSink = null
            }
        })

        context = flutterPluginBinding.applicationContext
        handler = Handler(Looper.getMainLooper())
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            "startScan" -> {
                startScan()
            }
            "connect" -> {
                val macAddress = call.argument<String?>("macAddress") ?: ""
                connect(macAddress)
                result.success(true)
            }
            "disconnect" -> {
                disconnect()
                result.success(true)
            }
            "requestDeviceVersion" -> {
                requestDeviceVersion(result)
            }
            "postCustomData" -> {
                val data = call.argument<String?>("data") ?: ""
                postCustomData(data)
                result.success(true)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun postCustomData(data: String) {
        Log.i(TAG, "postCustomData: data=$data")
        if (data.isEmpty()) {
            return
        }

        blufiClient?.postCustomData(data.toByteArray())

    }

    private fun requestDeviceVersion(result: Result) {
        this.resultDeviceVersion = result
        blufiClient?.requestDeviceVersion()
    }

    private fun connect(macAddress: String) {
        device = leDevices.first { it.device.address == macAddress }.device
        if (blufiClient != null) {
            blufiClient?.close()
            blufiClient = null
        }

        blufiClient = BlufiClient(binding.activity, device)
        blufiClient!!.setGattCallback(GattCallback())
        blufiClient!!.setBlufiCallback(BlufiCallbackMain())
        blufiClient!!.setGattWriteTimeout(GATT_WRITE_TIMEOUT)
        blufiClient!!.connect()
    }

    private fun disconnect() {
        blufiClient?.requestCloseConnection()
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        scanResultsChannel.setStreamHandler(null)
        stateChannel.setStreamHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.binding = binding
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {}

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {}

    override fun onDetachedFromActivity() {
        binding.removeRequestPermissionsResultListener(this)

        // Stop Scan
        if (scanning) {
            scanLeDevice()
        }
    }

    private fun startScan() {
        // FIXME handle bluetooth setting status errors here
        Log.i(TAG, "startScan: called !")
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        scanLeDevice()
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            val device = result!!.device
            val name: String? = device.name
//            Log.d(
//                TAG,
//                "onScanResult: name=${name}, mac=${device.address}, type=${device.type}"
//            )

            if (name == null || !name.startsWith("BLUFI")) {
//                Log.d(TAG, "onScanResult: invalid name")
                return
            }

            // Update list with new device
            val bleDevice = BLEDevice(device, result.rssi)
            Log.i(TAG, "onScanResult: Added device ${device.name}")
            val index = leDevices.indexOfFirst { it.device.address == bleDevice.device.address }
            if (index >= 0) {
                leDevices[index] = bleDevice
            } else {
                leDevices.add(bleDevice)
            }
            leDevices.sortWith(compareBy { it.rssi })

            // Send result
            scanResultsSink?.success(leDevices.map {
                hashMapOf(
                    "macAddress" to it.device.address,
                    "name" to it.device.name,
                    "rssi" to it.rssi,
                )
            })
        }
    }

    private fun scanLeDevice() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                binding.activity, permissions.toTypedArray(), REQUEST_PERMISSION
            )
            return
        }

        if (!scanning) {
            handler.postDelayed({
                // FIXME ADD MISSING PERMISSION
                scanning = false
                bluetoothLeScanner.stopScan(leScanCallback)
            }, 10000)

            scanning = true
            bluetoothLeScanner.startScan(leScanCallback)
        } else {
            scanning = false
            bluetoothLeScanner.stopScan(leScanCallback)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ): Boolean {
        if (requestCode != REQUEST_PERMISSION) {
            return false
        }
        Log.i(TAG, "onRequestPermissionsResult: permissions=$permissions, results=$grantResults")

        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            // FIXME Handle permission error
        }

        scanLeDevice()

        return true

    }

    fun onGattConnected() {
        // FIXME fill method
        connected = true
    }

    fun onGattDisconnected() {
        // FIXME fill method
        connected = false
    }

    private fun onGattServiceCharacteristicDiscovered() {
        // FIXME implement
    }


    private inner class GattCallback : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val devAddr = gatt.device.address
            Log.d(
                TAG, String.format(
                    Locale.ENGLISH,
                    "onConnectionStateChange addr=%s, status=%d, newState=%d",
                    devAddr,
                    status,
                    newState
                )
            )
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        onGattConnected()
                        updateMessage(String.format("Connected %s", devAddr), false)
                        emitBlufiEvent(BlufiEvent.ConnectionState(true))
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        gatt.close()
                        onGattDisconnected()
                        updateMessage(String.format("Disconnected %s", devAddr), false)
                        emitBlufiEvent(BlufiEvent.ConnectionState(false))
                    }
                }
            } else {
                gatt.close()
                onGattDisconnected()
                updateMessage(
                    String.format(Locale.ENGLISH, "Disconnect %s, status=%d", devAddr, status),
                    false
                )
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, String.format(Locale.ENGLISH, "onMtuChanged status=%d, mtu=%d", status, mtu))
            if (status == BluetoothGatt.GATT_SUCCESS) {
                updateMessage(
                    String.format(Locale.ENGLISH, "Set mtu complete, mtu=%d ", mtu), false
                )

            } else {
                blufiClient?.setPostPackageLengthLimit(20)
                updateMessage(
                    String.format(
                        Locale.ENGLISH, "Set mtu failed, mtu=%d, status=%d", mtu, status
                    ), false
                )
            }
            onGattServiceCharacteristicDiscovered()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, String.format(Locale.ENGLISH, "onServicesDiscovered status=%d", status))
            if (status != BluetoothGatt.GATT_SUCCESS) {
                gatt.disconnect()
                updateMessage(
                    String.format(
                        Locale.ENGLISH, "Discover services error status %d", status
                    ), false
                )
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int
        ) {
            Log.d(TAG, String.format(Locale.ENGLISH, "onDescriptorWrite status=%d", status))
            if (descriptor.uuid == BlufiParameter.UUID_NOTIFICATION_DESCRIPTOR && descriptor.characteristic.uuid == BlufiParameter.UUID_NOTIFICATION_CHARACTERISTIC) {
                val msg = String.format(
                    Locale.ENGLISH,
                    "Set notification enable %s",
                    if (status == BluetoothGatt.GATT_SUCCESS) " complete" else " failed"
                )
                updateMessage(msg, false)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                gatt.disconnect()
                updateMessage(
                    String.format(Locale.ENGLISH, "WriteChar error status %d", status), false
                )
            }
        }
    }


    private inner class BlufiCallbackMain : BlufiCallback() {
        @SuppressLint("MissingPermission")
        override fun onGattPrepared(
            client: BlufiClient,
            gatt: BluetoothGatt,
            service: BluetoothGattService?,
            writeChar: BluetoothGattCharacteristic?,
            notifyChar: BluetoothGattCharacteristic?
        ) {
            if (service == null) {
                Log.w(TAG, "Discover service failed")
                gatt.disconnect()
                updateMessage("Discover service failed", false)
                return
            }
            if (writeChar == null) {
                Log.w(TAG, "Get write characteristic failed")
                gatt.disconnect()
                updateMessage("Get write characteristic failed", false)
                return
            }
            if (notifyChar == null) {
                Log.w(TAG, "Get notification characteristic failed")
                gatt.disconnect()
                updateMessage("Get notification characteristic failed", false)
                return
            }
            updateMessage("Discover service and characteristics success", false)
            val mtu: Int = DEFAULT_MTU_LENGTH
            Log.d(TAG, "Request MTU $mtu")
            val requestMtu = gatt.requestMtu(mtu)
            if (!requestMtu) {
                Log.w(TAG, "Request mtu failed")
                updateMessage(String.format(Locale.ENGLISH, "Request mtu %d failed", mtu), false)
                onGattServiceCharacteristicDiscovered()
            }
        }

        override fun onNegotiateSecurityResult(client: BlufiClient, status: Int) {
            if (status == STATUS_SUCCESS) {
                updateMessage("Negotiate security complete", false)
            } else {
                updateMessage("Negotiate security failed， code=$status", false)
            }
        }

        override fun onPostConfigureParams(client: BlufiClient, status: Int) {
            if (status == STATUS_SUCCESS) {
                updateMessage("Post configure params complete", false)
            } else {
                updateMessage("Post configure params failed, code=$status", false)
            }
        }

        override fun onDeviceStatusResponse(
            client: BlufiClient, status: Int, response: BlufiStatusResponse
        ) {
            if (status == STATUS_SUCCESS) {
                updateMessage(
                    String.format(
                        "Receive device status response:\n%s", response.generateValidInfo()
                    ), true
                )
            } else {
                updateMessage("Device status response error, code=$status", false)
            }
        }

        override fun onDeviceScanResult(
            client: BlufiClient, status: Int, results: List<BlufiScanResult>
        ) {
            if (status == STATUS_SUCCESS) {
                val msg = StringBuilder()
                msg.append("Receive device scan result:\n")
                for (scanResult in results) {
                    msg.append(scanResult.toString()).append("\n")
                }
                updateMessage(msg.toString(), true)
            } else {
                updateMessage("Device scan result error, code=$status", false)
            }
        }

        override fun onDeviceVersionResponse(
            client: BlufiClient, status: Int, response: BlufiVersionResponse
        ) {
            when (status) {
                STATUS_SUCCESS -> {
                    resultDeviceVersion?.success(response.versionString)
                    updateMessage(
                        String.format("Receive device version: %s", response.versionString), true
                    )
                }
                else -> {
                    val message = "Device version error, code=$status"
                    resultDeviceVersion?.error("device_version", message, null)
                    updateMessage(message, false)
                }
            }
        }

        override fun onPostCustomDataResult(client: BlufiClient, status: Int, data: ByteArray) {
            val dataStr = String(data)
            val format = "Post data %s %s"
            if (status == STATUS_SUCCESS) {
                updateMessage(String.format(format, dataStr, "complete"), false)
            } else {
                updateMessage(String.format(format, dataStr, "failed"), false)
            }
        }

        override fun onReceiveCustomData(client: BlufiClient, status: Int, data: ByteArray) {
            if (status == STATUS_SUCCESS) {
                val customStr = String(data)
                updateMessage(String.format("Receive custom data:\n%s", customStr), true)
                emitBlufiEvent(BlufiEvent.CustomDataReceived(data = customStr))
//                eventsSink?.success(customStr)
            } else {
                updateMessage("Receive custom data error, code=$status", false)
            }
        }

        override fun onError(client: BlufiClient, errCode: Int) {
            updateMessage(String.format(Locale.ENGLISH, "Receive error code %d", errCode), false)
            if (errCode == CODE_GATT_WRITE_TIMEOUT) {
                updateMessage("Gatt write timeout", false)
                client.close()
                onGattDisconnected()
                // FIXME what should we do with WIFI SCAN FAILED not resolved?
//            } else if (errCode == BlufiCallback.CODE_WIFI_SCAN_FAIL) {
//                updateMessage("Scan failed, please retry later", false)
//                mContent.blufiDeviceScan.setEnabled(true)
            }
        }
    }

    fun updateMessage(message: String, isNotification: Boolean) {
        Log.d(TAG, message)
    }

    fun emitBlufiEvent(event: BlufiEvent) {
        val data = when (event) {
            is BlufiEvent.ConnectionState -> hashMapOf(
                "type" to BlufiEvent.TYPE_CONNECTION_STATE,
                "connected" to event.connected,
            )
            is BlufiEvent.CustomDataReceived -> hashMapOf(
                "type" to BlufiEvent.TYPE_CUSTOM_DATA,
                "data" to event.data,
            )
        }

        Log.d(TAG, "emitBlufiEvent: data=$data")

        if (Looper.getMainLooper().isCurrentThread) {
            eventsSink?.success(data)
        } else {
            handler.post {
                eventsSink?.success(data)
            }
        }
    }

}

sealed class BlufiEvent {
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

    companion object {
        const val TYPE_CONNECTION_STATE = 1
        const val TYPE_CUSTOM_DATA = 2
    }
}

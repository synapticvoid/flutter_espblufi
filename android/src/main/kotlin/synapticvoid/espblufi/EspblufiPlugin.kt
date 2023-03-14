package synapticvoid.espblufi


import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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
import blufi.espressif.params.BlufiConfigureParams
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

private const val REQUEST_START_SCAN_PERMISSION = 1
private const val REQUEST_STOP_SCAN_PERMISSION = 2

private const val GATT_WRITE_TIMEOUT = 5000L

private const val DEFAULT_MTU_LENGTH = 512

private data class BLEScanConf(val filter: String, val timeout: Long)

private const val BLE_SCAN_DEFAULT_TIMEOUT = 5000L

/** EspblufiPlugin */
class EspblufiPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.RequestPermissionsResultListener {

    private var resultDeviceVersion: Result? = null
    private var resultDeviceStatus: Result? = null
    private var resultWifiScan: Result? = null
    private var resultConfigureParameters: Result? = null
    private var scanResultsSink: EventChannel.EventSink? = null
    private var eventsSink: EventChannel.EventSink? = null

    private lateinit var channel: MethodChannel
    private lateinit var scanResultsChannel: EventChannel
    private lateinit var stateChannel: EventChannel

    private lateinit var context: Context
    private lateinit var binding: ActivityPluginBinding
    private lateinit var handler: Handler

    // BLE
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var scanConf: BLEScanConf? = null
    private var scanning = false
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

        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

    }

    override fun onMethodCall(call: MethodCall, result: Result) = when (call.method) {
        "startScan" -> {
            scanConf = BLEScanConf(
                call.argument<String?>("filter") ?: "",
                (call.argument<Int>("timeout") ?: BLE_SCAN_DEFAULT_TIMEOUT).toLong()
            )
            startBLEScan()
            result.success(true)
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
        "requestDeviceStatus" -> {
            requestDeviceStatus(result)
        }
        "requestWifiScan" -> {
            requestWifiScan(result)
        }
        "configureParameters" -> {
            val params = BlufiConfigureParams()
            params.opMode = call.argument<Int?>("opMode") ?: BlufiParameter.OP_MODE_NULL
            params.staSSIDBytes = call.argument<String?>("staSSID")?.toByteArray()
            params.staPassword = call.argument("staPassword")

            configureParameters(result, params)
        }
        "postCustomData" -> {
            val data = call.argument<ByteArray?>("data") ?: byteArrayOf()
            postCustomData(data)
            result.success(true)
        }
        else -> {
            result.notImplemented()
        }
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

        stopBLEScan()
    }

    private fun postCustomData(data: ByteArray) {
        Log.i(TAG, "postCustomData: size=${data.size}, data=${data.contentToString()}")
        if (data.isEmpty()) {
            return
        }

        blufiClient?.postCustomData(data)
    }

    private fun requestDeviceVersion(result: Result) {
        this.resultDeviceVersion = result
        blufiClient?.requestDeviceVersion()
    }

    private fun requestDeviceStatus(result: Result) {
        this.resultDeviceStatus = result
        blufiClient?.requestDeviceStatus()
    }

    private fun requestWifiScan(result: Result) {
        this.resultWifiScan = result
        blufiClient?.requestDeviceWifiScan()
    }


    private fun configureParameters(result: Result, params: BlufiConfigureParams) {
        this.resultConfigureParameters = result
        blufiClient?.configure(params)

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


    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            val device = result!!.device
            val name: String = device.name ?: return
//            Log.d(
//                TAG,
//                "onScanResult: name=${name}, mac=${device.address}, type=${device.type}"
//            )

            Log.i(TAG, "onScanResult: name=$name, filter=${scanConf!!.filter}")
            if (scanConf != null && !name.startsWith(scanConf!!.filter)) {
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
            emitBLEScanEvent(BLEScanEvent.InProgress(devices = leDevices))
        }
    }


    private fun startBLEScan() {
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
                binding.activity, permissions.toTypedArray(), REQUEST_START_SCAN_PERMISSION
            )
            return
        }

        if (scanConf == null) {
            Log.w(TAG, "startBLEScan: Unintialized scan conf")
            scanResultsSink?.error("no scan conf", "Invalid state, uninitialized scanconf", null)
            return
        }

        if (scanning) {
            return
        }

        handler.postDelayed({
            stopBLEScan()
        }, scanConf!!.timeout)

        scanning = true
        bluetoothLeScanner.startScan(
            null,
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            leScanCallback,
        )
    }

    private fun stopBLEScan() {
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
                binding.activity, permissions.toTypedArray(), REQUEST_STOP_SCAN_PERMISSION
            )
            return
        }
        if (!scanning) {
            return
        }

        scanning = false
        bluetoothLeScanner.stopScan(leScanCallback)
        emitBLEScanEvent(BLEScanEvent.Idle)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ): Boolean {
        if (requestCode != REQUEST_START_SCAN_PERMISSION) {
            return false
        }
        Log.i(TAG, "onRequestPermissionsResult: permissions=$permissions, results=$grantResults")

        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            // FIXME Handle permission error
        }

        when (requestCode) {
            REQUEST_START_SCAN_PERMISSION -> startBLEScan()
            REQUEST_STOP_SCAN_PERMISSION -> stopBLEScan()
        }

        return true

    }

    fun onGattConnected() {
        connected = true
        emitBlufiEvent(BlufiEvent.ConnectionState(connected))
    }

    fun onGattDisconnected() {
        connected = false
        emitBlufiEvent(BlufiEvent.ConnectionState(connected))
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
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        gatt.close()
                        onGattDisconnected()
                        updateMessage(String.format("Disconnected %s", devAddr), false)
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
                resultConfigureParameters?.success(status)
                updateMessage("Post configure params complete", false)
            } else {
                val message = "Post configure params failed, code=$status"
                updateMessage(message, false)
                resultConfigureParameters?.error("configure_parameters", message, null)
            }

            resultConfigureParameters = null
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
                sendDeviceStatus(response)
            } else {
                val message = "Device status response error, code=$status"
                updateMessage(message, false)
                resultDeviceStatus?.error("device_version", message, null)
            }

            resultDeviceStatus = null
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
                sendWifiScanResult(results)
            } else {
                val message = "Device scan result error, code=$status"
                updateMessage(message, false)
                resultWifiScan?.error("wifi_scan", message, null)
            }

            resultWifiScan = null
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

            resultDeviceVersion = null
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
                emitBlufiEvent(BlufiEvent.CustomDataReceived(data = data))
            } else {
                val message = "Receive custom data error, code=$status"
                updateMessage(message, false)
                emitBlufiEvent(BlufiEvent.Error(BlufiEvent.TYPE_CUSTOM_DATA, status, message))
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

    private fun sendDeviceStatus(response: BlufiStatusResponse) {
        resultDeviceStatus?.success(
            hashMapOf<String, Any?>(
                "staSSID" to response.staSSID,
                "staPassword" to response.staPassword,
            )
        )
    }

    private fun sendWifiScanResult(results: List<BlufiScanResult>) {
        val wifiScanResult = results.map {
            hashMapOf<String, Any?>(
                "ssid" to it.ssid,
                "rssi" to it.rssi,
            )
        }
        resultWifiScan?.success(wifiScanResult)
    }

    fun updateMessage(message: String, isNotification: Boolean) {
        Log.d(TAG, message)
    }

    @SuppressLint("MissingPermission")
    private fun emitBLEScanEvent(event: BLEScanEvent) {
        when (event) {
            BLEScanEvent.Idle -> {
                val data = hashMapOf(
                    "type" to BLEScanEvent.TYPE_IDLE,
                )
                executeOnMainThread { scanResultsSink?.success(data) }
            }
            is BLEScanEvent.InProgress -> {
                val devices = leDevices.map {
                    hashMapOf(
                        "macAddress" to it.device.address,
                        "name" to it.device.name,
                        "rssi" to it.rssi,
                    )
                }

                val data = hashMapOf(
                    "type" to BLEScanEvent.TYPE_IN_PROGRESS,
                    "devices" to devices,
                )
                executeOnMainThread { scanResultsSink?.success(data) }
            }
            is BLEScanEvent.Error -> {
                executeOnMainThread {
                    eventsSink?.error(
                        event.errorCode.toString(), event.message, null
                    )
                }
            }
        }


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
            is BlufiEvent.Error -> {
                Log.d(TAG, "emitBlufiEvent: error, error=$event")
                executeOnMainThread {
                    eventsSink?.error(
                        event.errorCode.toString(), event.message, null
                    )
                }
                return
            }
        }

        Log.d(TAG, "emitBlufiEvent: success, data=$data")
        executeOnMainThread { eventsSink?.success(data) }
    }

    private fun executeOnMainThread(fn: () -> Unit) {
        if (Looper.getMainLooper().isCurrentThread) {
            fn()
        } else {
            handler.post {
                fn()
            }
        }
    }
}


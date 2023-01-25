package synapticvoid.espblufi


import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
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
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

private const val TAG = "EspblufiPlugin"

private const val REQUEST_PERMISSION = 1

/** EspblufiPlugin */
class EspblufiPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.RequestPermissionsResultListener {

    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel

    private lateinit var context: Context
    lateinit var binding: ActivityPluginBinding

    // BLE
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var scanning = false
    private lateinit var handler: Handler
    private val leDevices = mutableListOf<BluetoothDevice>()

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "espblufi")
        channel.setMethodCallHandler(this)

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
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.binding = binding
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        binding.removeRequestPermissionsResultListener(this)

        // Stop Scan
        if (scanning) {
            scanLeDevice()
        }
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    }

    override fun onDetachedFromActivity() {
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
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            val device = result!!.device
            if (ActivityCompat.checkSelfPermission(
                    binding.activity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                Log.w(TAG, "onScanResult: No BLUETOOTH_CONNECT PERMISSIONS")
                return
            }

            val name: String? = device.name
            Log.d(
                TAG,
                "onScanResult: name=${name}, mac=${device.address}, type=${device.type}"
            )

            if (name == null || !name.startsWith("BLUFI")) {
                Log.d(TAG, "onScanResult: invalid name")
                return
            }

            Log.i(TAG, "onScanResult: Added device ${device.name}")
            leDevices.add(device)
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
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                binding.activity,
                permissions.toTypedArray(),
                REQUEST_PERMISSION
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
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
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
}

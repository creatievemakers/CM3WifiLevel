package be.kuleuven.cm3wifilevel


import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import java.io.File
import java.io.PrintWriter

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var isWifiStatePermissionGranted = false
    private var isWifiChangePermissionGranted = false
    private var isFineLocationPermissionGranted = false
    private var isCoarseLocationPermissionGranted = false
    private var isWritePermissionGranted = false


    private val permissionsList = arrayOf(
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    lateinit var wifiManager: WifiManager


    private val filename = "CM3WifiLevel.txt"
    lateinit var file: File
    lateinit var pw: PrintWriter

    lateinit var textview: TextView


    private val broadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(contxt: Context?, intent: Intent?) {
            val resultList = wifiManager.scanResults as ArrayList<ScanResult>
            val sb = StringBuilder()
            val sb_file = StringBuilder()

            val SSIDs = ArrayList<String>()

            fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, object : CancellationToken() {
                override fun onCanceledRequested(p0: OnTokenCanceledListener) = CancellationTokenSource().token

                override fun isCancellationRequested() = false
            }).addOnSuccessListener { location: Location? ->
                var lat = 0.0
                var lon = 0.0
                if (location == null)
                    Toast.makeText(contxt, "Cannot get location.", Toast.LENGTH_SHORT).show()
                else {
                    // see https://developer.android.com/reference/android/location/Location for getting more information about the location
                    lat = location.latitude
                    lon = location.longitude
                }
                for (res in resultList) {
                    // ignore other APs with the same SSID
                    if (SSIDs.contains(res.SSID))
                        continue
                    sb_file.append("${System.currentTimeMillis()},${res.SSID},${res.level}")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        sb_file.append(",${res.channelWidth}")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        sb_file.append(",${res.wifiStandard}")
                    }
                    sb.appendLine("${res.SSID}, ${res.level}")
                    sb_file.appendLine(",${lat},${lon}")
                    sb.appendLine()
                    SSIDs += res.SSID
                }


                pw.append(sb_file.toString())
                sb.appendLine("${lat}, ${lon}")
                textview.text = sb.toString()
                wifiManager.startScan()

            }



        }

    }

    override fun onStop() {
        pw.close()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textview = findViewById(R.id.aps)

        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                permissions.entries.forEach { Log.i(TAG, "${it.key}: ${it.value}") }
                isWifiChangePermissionGranted = permissions[Manifest.permission.CHANGE_WIFI_STATE]
                    ?: isWifiChangePermissionGranted
                isWifiStatePermissionGranted = permissions[Manifest.permission.ACCESS_WIFI_STATE]
                    ?: isWifiStatePermissionGranted
                isCoarseLocationPermissionGranted =
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION]
                        ?: isCoarseLocationPermissionGranted
                isFineLocationPermissionGranted =
                    permissions[Manifest.permission.ACCESS_FINE_LOCATION]
                        ?: isFineLocationPermissionGranted
                isWritePermissionGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE]
                    ?: isWritePermissionGranted


                if (isWifiChangePermissionGranted && isWifiStatePermissionGranted && isCoarseLocationPermissionGranted && isFineLocationPermissionGranted && isWritePermissionGranted) {
                    startWifiScanning()
                } else {
                    Toast.makeText(this, "Not all request granted... :(", Toast.LENGTH_LONG).show()
                }
            }

        requestPermissions()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


    }

    private fun requestPermissions() {
        Log.i(TAG, "Checking permissions")

        val permissionRequest: MutableList<String> = ArrayList()

        for (permReq in permissionsList) {
            val permission = ContextCompat.checkSelfPermission(
                this,
                permReq
            )

            if (permission != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Permission $permReq is denied")
                permissionRequest.add(permReq)
            } else {
                Log.i(TAG, "$permReq is approved")
                when (permReq) {
                    Manifest.permission.ACCESS_WIFI_STATE -> isWifiStatePermissionGranted = true
                    Manifest.permission.ACCESS_COARSE_LOCATION -> isCoarseLocationPermissionGranted =
                        true
                    Manifest.permission.ACCESS_FINE_LOCATION -> isFineLocationPermissionGranted =
                        true
                    Manifest.permission.CHANGE_WIFI_STATE -> isWifiChangePermissionGranted = true
                    Manifest.permission.WRITE_EXTERNAL_STORAGE -> isWritePermissionGranted = true
                }
            }

        }

        if (permissionRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionRequest.toTypedArray())
        } else {
            startWifiScanning()
        }


    }

    private fun startWifiScanning() {
        val path =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        file = File(path, filename)
        file.parentFile?.mkdirs()
        if (file.exists())
            file.delete()
        pw = PrintWriter(file)

        Log.i(TAG, "Absolute Path to output file is: ${file.absolutePath}")

        pw.append("time_ms,ssid,rssi")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pw.append(",channel_width")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pw.append(", wifi_standard")
        }
        pw.append(", lat")
        pw.append(", lon")
        pw.appendLine()

        wifiManager =
            this.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        registerReceiver(
            broadcastReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )

        wifiManager.startScan()

    }
}
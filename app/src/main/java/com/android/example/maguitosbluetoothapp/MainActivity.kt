package com.android.example.maguitosbluetoothapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.example.listviewtest.RecyclerViewAdapter
import java.util.*
import kotlin.concurrent.schedule
import com.android.example.listviewtest.BluetoothConnectionService
import com.android.example.listviewtest.PhoneData
import android.telephony.PhoneStateListener

// This application communicates with the arduino following the example of:
//     https://circuitdigest.com/microcontroller-projects/build-an-arduino-smart-watch-by-interfacing-oled-display-with-android-phone
// Was implemented as a "reverse engineered" replacement of the app posted in tha link above, as that one is not working anymore on newer Android devices.
// It has known issues and improvement possibilities but gives a starting point to someone starting to work with Android / bluetooth and arduino
// Delivered "as-is", with no support, under MIT Licence
class MainActivity : AppCompatActivity(), RecyclerViewAdapter.OnNoteListener {

    val TAG = "JZ:MainActivity"
    var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    var bluetoothConnectionService: BluetoothConnectionService? = null
    var pairedDevice: BluetoothDevice? = null
    
    //Widgets declaration
    lateinit var buttonOnOff: Button
    lateinit var buttonDiscover: Button
    lateinit var btStatus: TextView
    lateinit var txtDate: TextView
    lateinit var txtTime: TextView
    lateinit var txtBatt: TextView
    lateinit var txtSignal: TextView
    lateinit var recycler:RecyclerView
    
    var bluetoothDevices = arrayListOf<BluetoothDevice>()
    lateinit var deviceListAdapter: RecyclerViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        //Register the listeners for battery and signal strength
        registerReceiver(PhoneData.batInfoReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val mTelephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        mTelephonyManager.listen(PhoneData, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)

        setContentView(R.layout.activity_main)
        Log.d(TAG,"onCreate")

        //Attach widgets to the view
        btStatus = findViewById<TextView>(R.id.bt_status)
        buttonOnOff = findViewById<Button>(R.id.bluetooth_switch)
        buttonDiscover = findViewById<Button>(R.id.discover_btn)
        txtDate = findViewById<TextView>(R.id.date_view)
        txtTime = findViewById<TextView>(R.id.time_view)
        txtBatt = findViewById<TextView>(R.id.batt_view)
        txtSignal = findViewById<TextView>(R.id.signal_view)

        //Setup the recyclerView with the devices list
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        recycler = findViewById(R.id.bt_device_list)
        deviceListAdapter = RecyclerViewAdapter(this, bluetoothDevices, this)

        //Initiate bluetooth button status
        buttonOnOff()

        //Click listeners for the two buttons
        buttonOnOff.setOnClickListener {
            enableDisableBT()
        }
        buttonDiscover.setOnClickListener {
            discoverDevices()
        }

        //Register receivers for bluetooth status changes
        var BTIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(broadcastStateChangedReceived, BTIntent)

        BTIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(broadcastStateChangedReceived, BTIntent)

        BTIntent = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(broadcastStateChangedReceived, BTIntent)

        BTIntent = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(broadcastStateChangedReceived, BTIntent)

        var discoverDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(broadcastDiscoverReceiver, discoverDevicesIntent)

        discoverDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(broadcastDiscoverReceiver, discoverDevicesIntent)

        val pairingIntent = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(bondStateChangedReceived, pairingIntent)

        //Start the 1 sec loop to update device data on screen and on bluetooth client
        handler.postDelayed(runnable, 1000)

    }

    //Enable or disable bluetooth
    fun enableDisableBT() {
        buttonOnOff()
        if(bluetoothAdapter == null) {
            Log.d(TAG, "No bluetooth available on this device!!")
            btStatus.text = "Bluetooth is: NOT AVAILABLE!"
        }
        if(bluetoothAdapter != null) {
            if (!bluetoothAdapter!!.isEnabled) {
                val enableBTIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivity(enableBTIntent)
            }
            if (bluetoothAdapter!!.isEnabled) {
                bluetoothAdapter!!.disable()
            }
        }

    }

    //Discover bluetooth devices
    fun discoverDevices(){
        Log.d(TAG,"Making own device discoverable...")
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        val DiscFilter = IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)

        Log.d(TAG,"Discovering devices...")
        bluetoothDevices.clear()
        buttonDiscover.isEnabled = false
        buttonDiscover.setText("DISCOVERING...")
        if(bluetoothAdapter!!.isDiscovering()){
            Log.d(TAG,"Stopping and starting discovery...")
            bluetoothAdapter!!.cancelDiscovery()
            checkBTPermission()
            bluetoothAdapter!!.startDiscovery()

        }
        if(!bluetoothAdapter!!.isDiscovering()){
            Log.d(TAG,"Starting discovery...")
            checkBTPermission()
            bluetoothAdapter!!.startDiscovery()
        }
        //Stop discovery after 5 seconds (resource intensive)
        Timer("CancelDiscovery", false).schedule(5000) {
            if (bluetoothAdapter!!.isDiscovering()) {
                Log.d(TAG, "Stopping discovery...")
                bluetoothAdapter!!.cancelDiscovery()
            }
        }
    }

    //Changes the bluetooth button status
    fun buttonOnOff() {
        if(bluetoothAdapter != null) {
            if (!bluetoothAdapter!!.isEnabled) {
                btStatus.text = "Bluetooth is: OFF"
                buttonOnOff.setText("Turn Bluetooth ON")
                buttonDiscover.isEnabled = false
            }
            if (bluetoothAdapter!!.isEnabled) {
                btStatus.text = "Bluetooth is: ON"
                buttonOnOff.setText("Turn Bluetooth OFF")
                buttonDiscover.isEnabled = true
            }
        }
        else{
            btStatus.text = "Bluetooth is: NOT AVAILABLE"
            buttonOnOff.setText("NO Bluetooth!")
            buttonOnOff.isEnabled = false
            buttonDiscover.isEnabled = false
        }
    }

    // Click listener for the recyclerView containing devices
    // If the device is already paired, the bluetooth service gets started.
    // If not, it tries to pair with the device (This is not working correctly in this version)
    override fun onNoteClick(position: Int) {
        Log.d(TAG,"You clicked position $position")
        bluetoothAdapter?.cancelDiscovery()
        val bondedDevices = bluetoothAdapter?.bondedDevices!!
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2){
            val device = bluetoothDevices.get(position)
            if (device in bondedDevices){
                pairedDevice = device
                bluetoothConnectionService = BluetoothConnectionService(this, bluetoothAdapter!!)
                bluetoothConnectionService!!.startClient(pairedDevice!!)
            } else{
                Log.d(TAG, "Trying to pair wih ${device.name}")
                device.createBond()
            }

        }
    }

    // Create a BroadcastReceiver for ACTION_STATE_CHANGED.
    // Used for logging states only. No functional usage in our case
    private val broadcastStateChangedReceived = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG,">>>>> broadcastStateChangedReceived.onReceive invocated!")
            val action: String = intent.action!!
            Log.d(TAG, "ACTION:$action")
            when(action) {

                BluetoothAdapter.ACTION_SCAN_MODE_CHANGED -> {
                    Log.d(TAG, "ACTION_SCAN_MODE_CHANGED")
                    val mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR)
                    when (mode) {
                        BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> Log.d(TAG, "Discoverability enabled")
                        BluetoothAdapter.SCAN_MODE_CONNECTABLE -> Log.d(TAG, "Discoverability enabled, able to receive connections")
                        BluetoothAdapter.SCAN_MODE_NONE -> Log.d(TAG, "Discoverability DISABLED!")
                        BluetoothAdapter.STATE_CONNECTING -> Log.d(TAG, "CONNECTING...")
                        BluetoothAdapter.STATE_CONNECTED -> Log.d(TAG, "CONNECTED!")
                    }

                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d(TAG, ">>>>> DISCOVERY STARTED!!")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, ">>>>> DISCOVERY DONE!!")
                    buttonDiscover.isEnabled = true
                    buttonDiscover.setText("DISCOVER DEVICES")
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when(state) {
                        BluetoothAdapter.STATE_ON -> {
                            btStatus.text = "Bluetooth is: ON"
                            buttonOnOff.setText("Turn Bluetooth OFF")
                            buttonDiscover.isEnabled = true
                            Log.d(TAG,">>>>>>>>>> BLUETOOTH >> ON <<")
                        }
                        BluetoothAdapter.STATE_OFF -> {
                            btStatus.text = "Bluetooth is: OFF"
                            buttonOnOff.setText("Turn Bluetooth ON")
                            buttonDiscover.isEnabled = false
                            Log.d(TAG,">>>>>>>>>> BLUETOOTH >> OFF <<")
                        }
                    }
                }
            }
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    // Used to update the recyclerView when a new BT-device is found
    private val broadcastDiscoverReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG,">>>>> broadcastDiscoverReceiver.onReceive invocated!")
            val action: String = intent.action!!
            Log.d(TAG, "ACTION:$action")
            when(action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if(device?.name != null) {
                        Log.d(TAG, ">>>> FOUND DEVICE: ${device?.name} with HW address: ${device?.address}")
                        bluetoothDevices.add(device!!)
                        recycler.adapter = deviceListAdapter
                        recycler.layoutManager = LinearLayoutManager(context)
                    }
                }

            }
        }
    }

    // Create a BroadcastReceiver for ACTION_BOND_STATE_CHANGED.
    // When a device is found and is not bonded, this receiver should prepare it to be used
    // There is a bug here, the device gets bonded but since we do not have a device UUID yet, the bluetooth service crashes
    // (should be easy to solve, if you want to try...)
    private val bondStateChangedReceived = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG,">>>>> bondStateChangedReceived.onReceive invocated!")
            val action: String = intent.action!!
            when(action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    Log.d(TAG, "ACTION_BOND_STATE_CHANGED OK")
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        Log.d(TAG, "device is not null")
                        when(device.bondState) {
                            BluetoothDevice.BOND_BONDED -> {
                                Log.d(TAG, "Device ${device.name} is now bonded!")
                                pairedDevice = device
                                bluetoothConnectionService = BluetoothConnectionService(context, bluetoothAdapter!!)
                                bluetoothConnectionService!!.startClient(pairedDevice!!)
                            }
                            BluetoothDevice.BOND_BONDING -> Log.d(TAG, "Bonding with device ${device.name}...")
                            BluetoothDevice.BOND_NONE -> Log.d(TAG, "Bonding with device ${device.name} has been lost")
                        }
                    }
                }
            }
        }
    }

    //Handler for the 1 second loop to update data.
    // There are surely more elegant ways to solve this, with broadcast / receive
    // techniques, but that would add complexity...
    val handler = Handler(Looper.getMainLooper())

    // The runnable loop itself. gets triggered every second
    val runnable = object : Runnable {
        override fun run() {
            val date = PhoneData.getDate()
            val time = PhoneData.getTime()
            val batt =PhoneData.battLevel.toString()
            val sign =PhoneData.mSignalStrength.toString()
            txtDate.text = "DATE: $date"
            txtTime.text = "TIME: $time"
            txtBatt.text = "BATT: $batt %"
            txtSignal.text = "SIGNAL: $sign %"

            val stringToSend = "#$date $time,$sign,,$batt,,,$"
            bluetoothConnectionService?.sendCommand(stringToSend)
            Log.d(TAG, "STRING TO SEND:$stringToSend")
            handler.postDelayed(this, 1000)
        }
    }


    // Checks Bluetooth permissions. Needs API level 26 or more.
    // With Pie this can be skipped, but Oreo just prevents Bluetooth usage if you do.
    private fun checkBTPermission() {
        Log.d(TAG, "Checking Permissions...")
        Log.d(TAG, ">>>>> SDK greater than LOLLIPOP")
        var permissionCheck = checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION")
        permissionCheck += checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION")
        if(permissionCheck != 0) {
            Log.d(TAG, ">>>>> Requesting permission...")
            val permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            requestPermissions(permissions, 1001)
        }
    }

    // De-register the receivers before destroying
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastStateChangedReceived)
        unregisterReceiver(broadcastDiscoverReceiver)
        unregisterReceiver(bondStateChangedReceived)
        unregisterReceiver(PhoneData.batInfoReceiver)
    }
}

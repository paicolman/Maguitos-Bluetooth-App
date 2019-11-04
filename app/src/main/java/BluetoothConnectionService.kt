package com.android.example.listviewtest

import android.bluetooth.BluetoothAdapter
import android.content.Context
import java.util.*
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothDevice
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

//Bluetooth connection service. When triggered, it starts two background threads in sequence:
// The first one waits until a connection is established (ConnectThread) and the other one checks
// constantly if there is some data available to be read and reads it. The reading part is only
// used for logging, since we only send data to the Arduino. But it could be used to implement a
// more elegant way to send the data (not blindly every second, but when the Arduino has sent something).
class BluetoothConnectionService(contxt: Context, btAdapter: BluetoothAdapter) {
    val TAG = "JZ:BTConnectionService"

    val bluetoothAdpater: BluetoothAdapter= BluetoothAdapter.getDefaultAdapter()//btAdapter
    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null

    // Connect thread. Triggered by startClient(). Starts a background thread that stops at the line "mmSocket?.connect()".
    // This is quite cool, it just stops there and waits until the connection is successful. But it's also the reason a
    // background thread is needed, otherwise the app would freeze until its connected (not nice). After it's connected, the thread ends.
    private inner class ConnectThread(val device: BluetoothDevice) : Thread() {

        val devUUIDs = device.uuids
        private var mmSocket: BluetoothSocket? = device.createInsecureRfcommSocketToServiceRecord(devUUIDs[0].uuid)

        // Background thread!
        override fun run() {
            Log.d(TAG, "Connect Thread runnning, connecting to device:${device.name} (${device.address})")
            for (devUUID in devUUIDs){
                Log.d(TAG,"UUID:${devUUID.toString()}")
            }

            // Cancel discovery (if its not cancelled yet) because it otherwise slows down the connection.
            bluetoothAdpater.cancelDiscovery()

            try {
                Log.d(TAG, "try Connecting")
                mmSocket?.connect()
            } catch (e: IOException) {
                Log.e(TAG, "Could not connect socket", e)
                Log.d(TAG, "Closing socket...")
                mmSocket?.close()
            }
            Log.d(TAG, "Will manage the connected socket now...")
            manageMyConnectedSocket(mmSocket!!)
        }

        // Closes the client socket and causes the thread to finish. Not used, left here for completion.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }

    //This is the second background thread. It handles reading or writing to the bluetooth interface.
    // Gets triggered by manageMyConnectedSocket and kind of loops forever. It actually stops in the "mmInStream.read()" line,
    // reads what has arrived and logs it, then loops back to that line.. To be clear: Only the read is done in a background thread.
    // Writing is done directly by invoking the write() function.
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {

        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        //Background thread!
        override fun run() {
            Log.d(TAG, "ConnectedThread running...")
            var numBytes: Int // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                // Read from the InputStream.
                numBytes = try {
                    Log.d(TAG, "Reading... available:${mmInStream.available()}")
                    mmInStream.read(mmBuffer)

                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    break
                }
                val incomingMessage = String(mmBuffer, 0, numBytes)
                Log.d(TAG, ">>> MESSAGE: $incomingMessage ")
            }
        }


        // Call this from the main activity to send data to the remote device.
        fun write(bytes: ByteArray) {
            Log.d(TAG, "Writing to output stream...")
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)
            }
        }

        // Call this method from the main activity to shut down the connection. Not used, but it's
        // not so clean to leave a thread running... Luckily, the OS takes care of that ;)
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    //Convenience method to trigger the write method of the ConnectedThread class with a string
    // expected by the Arduino. This is called from MainActivity but it would be more elegant to
    // call it from the background thread. The problem there is that you cannot update the view's
    // widgets from a background thread so it adds complexity...
    //The string expected by the arduino code is: "#<DATE> <TIME>,<SIGNAL>,,<BATTERY>,,,<DummyInfo>$"
    fun sendCommand(command: String) {
        Log.d(TAG, "sending a $command...")
        val bytes = ByteArray(command.length)
        var i = 0
        for (stringChar in command) {
            bytes[i] = stringChar.toByte()
            i += 1
        }
        mConnectedThread?.write(bytes)
    }

    //Triggers the background thread for connecting
    fun startClient(device: BluetoothDevice) {
        Log.d(TAG, "startClient")
        mConnectThread = ConnectThread(device)
        mConnectThread?.start()
    }

    //Triggers the background thread for reading and enables writing.
    private fun manageMyConnectedSocket(socket: BluetoothSocket) {
        Log.d(TAG, "manageMyConnectedSocket")

        mConnectedThread = ConnectedThread(socket)
        mConnectedThread?.start()
    }


}
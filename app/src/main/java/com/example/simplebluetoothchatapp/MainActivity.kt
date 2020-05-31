package com.example.simplebluetoothchatapp

import android.app.Activity
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import java.util.*

class MainActivity : AppCompatActivity() {
    private var status: TextView? = null
    private var btnConnect: Button? = null
    private var listView: ListView? = null
    private var dialog: Dialog? = null
    private var inputLayout: TextInputLayout? = null
    private var chatAdapter: ArrayAdapter<String>? = null
    private var chatMessages: ArrayList<String>? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var chatController: ChatController? = null
    private var connectingDevice: BluetoothDevice? = null
    private var discoveredDevicesAdapter: ArrayAdapter<String>? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewsByIds()
        //check device support bluetooth or not
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available!", Toast.LENGTH_SHORT).show()
            finish()
        }
        //show bluetooth devices dialog when click connect button
        btnConnect?.setOnClickListener { showPrinterPickDialog() }
        //set chat adapter
        chatMessages = ArrayList()
        chatAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, chatMessages!!)
        listView?.adapter = chatAdapter
    }

    private val handler =
        Handler(Handler.Callback { msg ->
            when (msg.what) {
                MESSAGE_STATE_CHANGE -> when (msg.arg1) {
                    ChatController.STATE_CONNECTED -> {
                        setStatus("Connected to: " + connectingDevice?.name)
                        btnConnect?.isEnabled = false
                    }
                    ChatController.STATE_CONNECTING -> {
                        setStatus("Connecting...")
                        btnConnect?.isEnabled = false
                    }
                    ChatController.STATE_LISTEN, ChatController.STATE_NONE -> setStatus("Not connected")
                }
                MESSAGE_WRITE -> {
                    val writeBuf = msg.obj as ByteArray
                    val writeMessage = String(writeBuf)
                    chatMessages?.add("Me: $writeMessage")
                    chatAdapter?.notifyDataSetChanged()
                }
                MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray
                    val readMessage = String(readBuf, 0, msg.arg1)
                    chatMessages?.add(connectingDevice?.name + ":  " + readMessage)
                    chatAdapter?.notifyDataSetChanged()
                }
                MESSAGE_DEVICE_OBJECT -> {
                    connectingDevice = msg.data
                        .getParcelable(DEVICE_OBJECT)
                    Toast.makeText(
                        applicationContext, "Connected to " + connectingDevice?.name,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                MESSAGE_TOAST -> Toast.makeText(
                    applicationContext, msg.data.getString("toast"),
                    Toast.LENGTH_SHORT
                ).show()
            }
            false
        })

    private fun showPrinterPickDialog() {
        dialog = Dialog(this)
        dialog?.setContentView(R.layout.layout_bluetooth)
        dialog?.setTitle("Bluetooth Devices")
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }
        bluetoothAdapter?.startDiscovery()
        //Initializing bluetooth adapters
        val pairedDevicesAdapter =
            ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        discoveredDevicesAdapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1)
        //locate listviews and attatch the adapters
        val listView =
            dialog?.findViewById<View>(R.id.pairedDeviceList) as ListView
        val listView2 =
            dialog?.findViewById<View>(R.id.discoveredDeviceList) as ListView
        listView.adapter = pairedDevicesAdapter
        listView2.adapter = discoveredDevicesAdapter
        // Register for broadcasts when a device is discovered
        var filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(discoveryFinishReceiver, filter)
        // Register for broadcasts when discovery has finished
        filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(discoveryFinishReceiver, filter)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val pairedDevices =
            bluetoothAdapter?.getBondedDevices()
        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices?.size!! > 0) {
            for (device in pairedDevices) {
                pairedDevicesAdapter.add(device.name + "\n" + device.address)
            }
        } else {
            pairedDevicesAdapter.add(getString(R.string.none_paired))
        }
        //Handling listview item click event
        listView.onItemClickListener = OnItemClickListener { parent, view, position, id ->
            bluetoothAdapter?.cancelDiscovery()
            val info = (view as TextView).text.toString()
            val address = info.substring(info.length - 17)
            connectToDevice(address)
            dialog?.dismiss()
        }
        listView2.onItemClickListener = OnItemClickListener { adapterView, view, i, l ->
            bluetoothAdapter?.cancelDiscovery()
            val info = (view as TextView).text.toString()
            val address = info.substring(info.length - 17)
            connectToDevice(address)
            dialog?.dismiss()
        }
        dialog!!.findViewById<View>(R.id.cancelButton).setOnClickListener { dialog?.dismiss() }
        dialog?.setCancelable(false)
        dialog?.show()
    }

    private fun setStatus(s: String) {
        status?.text = s
    }

    private fun connectToDevice(deviceAddress: String) {
        bluetoothAdapter?.cancelDiscovery()
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        chatController?.connect(device)
    }

    private fun findViewsByIds() {
        status = findViewById<View>(R.id.status) as TextView
        btnConnect = findViewById<View>(R.id.btn_connect) as Button
        listView = findViewById<View>(R.id.list) as ListView
        inputLayout = findViewById<View>(R.id.input_layout) as TextInputLayout
        val btnSend = findViewById<View>(R.id.btn_send)
        btnSend.setOnClickListener {
            if (inputLayout?.editText?.text.toString() == "") {
                Toast.makeText(this@MainActivity, "Please input some texts", Toast.LENGTH_SHORT)
                    .show()
            } else { //TODO: here
                sendMessage(inputLayout?.editText?.text.toString())
                inputLayout?.editText?.setText("")
            }
        }
    }

    public override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_ENABLE_BLUETOOTH -> if (resultCode == Activity.RESULT_OK) {
                chatController = ChatController(this, handler)
            } else {
                Toast.makeText(
                    this,
                    "Bluetooth still disabled, turn off application!",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun sendMessage(message: String) {
        if (chatController?.getState() != ChatController.STATE_CONNECTED) {
            Toast.makeText(this, "Connection was lost!", Toast.LENGTH_SHORT).show()
            return
        }
        if (message.isNotEmpty()) {
            val send = message.toByteArray()
            chatController?.write(send)
        }
    }

    public override fun onStart() {
        super.onStart()
        if (bluetoothAdapter?.isEnabled == true) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH)
        } else {
            chatController = ChatController(this, handler)
        }
    }

    public override fun onResume() {
        super.onResume()
        if (chatController != null) {
            if (chatController?.getState() == ChatController.STATE_NONE) {
                chatController?.start()
            }
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (chatController != null) chatController?.stop()
    }

    private val discoveryFinishReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device =
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    discoveredDevicesAdapter?.add(device.name + "\n" + device.address)
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                if (discoveredDevicesAdapter?.count == 0) {
                    discoveredDevicesAdapter?.add(getString(R.string.none_found))
                }
            }
        }
    }

    companion object {
        const val MESSAGE_STATE_CHANGE = 1
        const val MESSAGE_READ = 2
        const val MESSAGE_WRITE = 3
        const val MESSAGE_DEVICE_OBJECT = 4
        const val MESSAGE_TOAST = 5
        const val DEVICE_OBJECT = "device_name"
        private const val REQUEST_ENABLE_BLUETOOTH = 1
    }
}
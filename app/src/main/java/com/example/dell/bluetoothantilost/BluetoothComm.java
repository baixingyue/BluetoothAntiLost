package com.example.dell.bluetoothantilost;

/**
 * Created by DELL on 2016/11/29.
 */


import java.util.UUID;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.example.dell.bluetoothantilost.*;

public class BluetoothComm extends Activity {
    // Debugging
    private static final String TAG = "BluetoothComm";
    private static final boolean D = true;
    // 请求开启蓝牙的requestCode
    static final int REQUEST_ENABLE_BT = 1;
    // 请求连接的requestCode
    static final int REQUEST_CONNECT_DEVICE = 2;
    // bluetoothCommService 传来的消息状态
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    // 蓝牙设备
    private BluetoothDevice device = null;

    //private EditText txEdit;
    private EditText rxEdit;
    private EditText inputEdit;
    // 连接的设备
    private TextView connectDevices;
    // 发送按键
    private Button sendButton;
    // 清空接收记录按键
    private Button clearRxButton;
    // 清楚发送记录按键
    private Button clearTxButton;
    // 断开连接按键
    private Button disconnectButton;
    private Button clearAll;
    // 本地蓝牙适配器
    private BluetoothAdapter bluetooth;
    // 创建一个蓝牙串口服务对象
    private BluetoothCommService mCommService = null;

    private StringBuffer mOutStringBuffer = new StringBuffer("");

    private String mConnectedDeviceName = null;

    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 获得控件
        sendButton = (Button) findViewById(R.id.sendButton);
        sendButton.setOnClickListener(new btnClickedListener());
        clearRxButton = (Button) findViewById(R.id.clearRx);
        clearRxButton.setOnClickListener(new btnClickedListener());
        clearTxButton = (Button) findViewById(R.id.clearTx);
        clearTxButton.setOnClickListener(new btnClickedListener());
        disconnectButton = (Button) findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(new btnClickedListener());
        //txEdit = (EditText) findViewById(R.id.tx_history);
        rxEdit = (EditText) findViewById(R.id.rx_history);
        inputEdit = (EditText) findViewById(R.id.inputEdit);
        connectDevices = (TextView) findViewById(R.id.connected_device);
        // 获得本地蓝牙设备
        bluetooth = BluetoothAdapter.getDefaultAdapter();
        if (bluetooth == null) {// 设备没有蓝牙设备
            Toast.makeText(this, "没有找到蓝牙适配器", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!bluetooth.isEnabled()) {
            // 请求打开蓝牙设备
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            if (mCommService == null) {
                mCommService = new BluetoothCommService(this, mHandler);
            }
        }
    }

    @Override
    protected synchronized void onResume() {
        super.onResume();
        if (mCommService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't
            // started already
            if (mCommService.getState() == BluetoothCommService.STATE_NONE) {
                // Start the Bluetooth services，开启监听线程
                mCommService.start();
            }
        }
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if (D)
            Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if (bluetooth != null) {
            bluetooth.disable();
        }
        if (D)
            Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mCommService != null)
            mCommService.stop();
        if (D)
            Log.e(TAG, "--- ON DESTROY ---");
    }

    /**
     * onActivityResult方法，当启动startActivityForResult返回之后调用， 根据用户的操作来执行相应的操作
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    if (D)
                        Log.d(TAG, "打开蓝牙设备");
                    Toast.makeText(this, "成功打开蓝牙", Toast.LENGTH_SHORT).show();
                } else {
                    if (D)
                        Log.d(TAG, "不允许打开蓝牙设备");
                    Toast.makeText(this, "不能打开蓝牙,程序即将关闭", Toast.LENGTH_SHORT).show();
                    finish();// 用户不打开设备，程序结束
                }
                break;
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {// 用户选择连接的设备
                    // Get the device MAC address
                    String address = data.getExtras().getString(ScanDeviceActivity.EXTRA_DEVICE_ADDRESS);
                    // Get the BLuetoothDevice object
                    device = bluetooth.getRemoteDevice(address);
                    // 尝试连接设备
                    mCommService.connect(device);
                }
                break;
        }
        return;
    }

    private class btnClickedListener implements OnClickListener {

        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.sendButton) {
                if (device == null) {
                    Toast.makeText(BluetoothComm.this, "请连接设备！", Toast.LENGTH_LONG).show();
                    inputEdit.setText("");
                } else {
                    String txString = inputEdit.getText() + "";
                    // inputEdit.setText("");
                    //txEdit.append(txString);
                    sendMessage(txString);
                }
            } else if (v.getId() == R.id.clearRx) {
                rxEdit.setText("");
            } else if (v.getId() == R.id.clearTx) {
                inputEdit.setText("");
            } else if (v.getId() == R.id.disconnectButton) {
                if (mCommService != null) {
                    mCommService.stop();
                }
            }
        }
    }

    private void ensureDiscoverable() {
        if (bluetooth.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            // 最长可见时间为300s
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    // 创建菜单选项
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    // 菜单项被点击
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.scan:
                // Launch the ScanDeviceActivity to see devices and do scan
                Intent serverIntent = new Intent(this, ScanDeviceActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                return true;
            case R.id.discoverable:
                ensureDiscoverable();
                return true;
            case R.id.about:
                Intent intent = new Intent(BluetoothComm.this, AboutActivity.class);
                startActivity(intent);
                return true;
            case R.id.exit:
                finish();
                return true;
        }
        return false;
    }

    /**
     * Sends a message.
     *
     * @param message
     *            A string of text to send.
     */
    private void sendMessage(String message) {
        // 没有连接设备，不能发送
        if (mCommService.getState() != BluetoothCommService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.nodevice, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mCommService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
        }
    }

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    if (D)
                        Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothCommService.STATE_CONNECTED:
                            connectDevices.setText(R.string.title_connected_to);
                            connectDevices.append(mConnectedDeviceName);
                            // mConversationArrayAdapter.clear();
                            break;
                        case BluetoothCommService.STATE_CONNECTING:
                            connectDevices.setText(R.string.title_connecting);
                            break;
                        case BluetoothCommService.STATE_LISTEN:
                        case BluetoothCommService.STATE_NONE:
                            connectDevices.setText(R.string.title_not_connected);
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    // String writeMessage = new String(writeBuf);
                    // mConversationArrayAdapter.add("Me: " + writeMessage);
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    // mConversationArrayAdapter.add(mConnectedDeviceName+": " +
                    // readMessage);
                    rxEdit.append(readMessage);
                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT)
                            .show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

}
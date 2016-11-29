package com.example.dell.bluetoothantilost;

import java.util.ArrayList;
import java.util.Set;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import com.example.dell.bluetoothantilost.*;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

public class ScanDeviceActivity extends Activity{
    // Debugging
    private static final String TAG = "BluetoothComm";
    private static final boolean D = true;

    // Return Intent extra
    public static String EXTRA_DEVICE_ADDRESS = "device_address";
    //没有搜到新设备设为1，搜到新设备为0
    private int noDeviceFlag = 0;

    // Member fields
    private BluetoothAdapter bluetooth;
    private ArrayList<String> newDevices = new ArrayList<String>();
    private ListView newDevicesList;
    private ListView pairedDevicesList;
    private ArrayAdapter<String> pairedDevicesAdapter;
    private ArrayAdapter<String> newDevicesAdapter;
    private Button scanButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Setup the window
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.device_list);
        bluetooth = BluetoothAdapter.getDefaultAdapter();
        // Get a set of currently paired devices
        Set<BluetoothDevice> pairedDevices = bluetooth.getBondedDevices();

        scanButton = (Button)findViewById(R.id.scanButton);
        scanButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                doDiscovery();
                v.setVisibility(View.GONE);
            }
        });
        newDevicesList = (ListView)findViewById(R.id.newDevices);
        newDevicesAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,
                newDevices);
        newDevicesList.setAdapter(newDevicesAdapter);
        newDevicesList.setOnItemClickListener(mNewDeviceClickListener);

        // Find and set up the ListView for paired devices
        pairedDevicesList = (ListView)findViewById(R.id.pairedDevices);
        pairedDevicesAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1);
        pairedDevicesList.setAdapter(pairedDevicesAdapter);
        //当点击时，启动线程connect
        pairedDevicesList.setOnItemClickListener(mDeviceClickListener);

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            //   findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices) {
                pairedDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            String noDevices = getResources().getText(R.string.noPairedDevice).toString();
            pairedDevicesAdapter.add(noDevices);
        }

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Make sure we're not doing discovery anymore
        if (bluetooth != null) {
            bluetooth.cancelDiscovery();
        }
        // Unregister broadcast listeners
        this.unregisterReceiver(mReceiver);
    }


    private void doDiscovery(){
        // Indicate scanning in the title
        setProgressBarIndeterminateVisibility(true);
        setTitle(R.string.scanning);
        if(bluetooth.isDiscovering()){
            bluetooth.cancelDiscovery();
        }
        bluetooth.startDiscovery();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            //当发现设备
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                noDeviceFlag = 0;
                //如果设备已经配对，则忽略
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    newDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setProgressBarIndeterminateVisibility(false);
                setTitle(R.string.select_device);
                if (newDevicesAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.noNewDevice).toString();
                    newDevicesAdapter.add(noDevices);
                    noDeviceFlag = 1;
                }
                else {
                    noDeviceFlag = 0;
                }
                if(D) Log.d(TAG, "搜索完成noDeviceFlag="+noDeviceFlag);
            }
        }

    };

    /**
     * the ItemClickListener for all devices in the two ListViews
     */
    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            //当选择连接之后，关闭搜索过程，此过程消耗比较大
            bluetooth.cancelDiscovery();

            // 从点击的Item中获得Mac地址，由于Mac地址格式可以其为最后的17位
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            // Create the result Intent and include the MAC address
            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

            // Set result and finish this Activity
            setResult(Activity.RESULT_OK, intent);
            if(D) Log.d(TAG, "start to connecting"+address);
            finish();
        }
    };

    private OnItemClickListener mNewDeviceClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            //当选择连接之后，关闭搜索过程，此过程消耗比较大
            bluetooth.cancelDiscovery();

            // 从点击的Item中获得Mac地址，由于Mac地址格式可以其为最后的17位
            //添加noDeviceFlag标志，防止在没有搜索到设备时仍然选择连接而造成程序中断的bug
            if(noDeviceFlag==0) {
                String info = ((TextView) v).getText().toString();
                String address = info.substring(info.length() - 17);
                // Create the result Intent and include the MAC address
                Intent intent = new Intent();
                intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

                // Set result and finish this Activity
                setResult(Activity.RESULT_OK, intent);
            }
            finish();
        }
    };
}
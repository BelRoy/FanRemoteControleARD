package com.devqt.fan.remote.controle.arduino;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ScanDevice extends AppCompatActivity{
        private final static String TAG = DeviceControl.class.getSimpleName();

        public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
        public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

        private TextView mConnectionState;
        private TextView mDataField;
        private String mDeviceName;
        private String mDeviceAddress;
        private ExpandableListView mGattServicesList;
        private BluetoothService mBluetoothService;
        private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
                new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
        private boolean mConnected = false;
        private BluetoothGattCharacteristic mNotifyCharacteristic;

        private final String LIST_NAME = "NAME";
        private final String LIST_UUID = "UUID";

        // Code to manage Service lifecycle.
        private final ServiceConnection mServiceConnection = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName componentName, IBinder service) {
                mBluetoothService = ((BluetoothService.LocalBinder) service).getService();
                if (!mBluetoothService.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth");
                    finish();
                }

                mBluetoothService.connect(mDeviceAddress);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                mBluetoothService = null;
            }
        };


        private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (BluetoothService.ACTION_GATT_CONNECTED.equals(action)) {
                    mConnected = true;
                    updateConnectionState(R.string.connected);
                    invalidateOptionsMenu();
                } else if (BluetoothService.ACTION_GATT_DISCONNECTED.equals(action)) {
                    mConnected = false;
                    updateConnectionState(R.string.disconnected);
                    invalidateOptionsMenu();
                    clearUI();
                } else if (BluetoothService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                    // Show all the supported services and characteristics on the user interface.
                    //displayGattServices(mBluetoothLeService.getSupportedGattServices());
                } else if (BluetoothService.ACTION_DATA_AVAILABLE.equals(action)) {
                    displayData(intent.getStringExtra(BluetoothService.EXTRA_DATA));
                }
            }
        };


        private final ExpandableListView.OnChildClickListener servicesListClickListner =
                new ExpandableListView.OnChildClickListener() {
                    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
                    @Override
                    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                                int childPosition, long id) {
                        if (mGattCharacteristics != null) {
                            final BluetoothGattCharacteristic characteristic =
                                    mGattCharacteristics.get(groupPosition).get(childPosition);
                            final int charaProp = characteristic.getProperties();
                            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {

                                if (mNotifyCharacteristic != null) {
                                    mBluetoothService.setCharacteristicNotification(
                                            mNotifyCharacteristic, false);
                                    mNotifyCharacteristic = null;
                                }
                                mBluetoothService.readCharacteristic(characteristic);
                            }
                            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                                mNotifyCharacteristic = characteristic;
                                mBluetoothService.setCharacteristicNotification(
                                        characteristic, true);
                            }
                            return true;
                        }
                        return false;
                    }
                };

        private void clearUI() {

            mDataField.setText(R.string.no_data);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.button_control);

            final Intent intent = getIntent();
            mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
            mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);


            mDataField = (TextView) findViewById(R.id.data_value);

            getActionBar().setTitle(mDeviceName);
            getActionBar().setDisplayHomeAsUpEnabled(true);
            Intent gattServiceIntent = new Intent(this, BluetoothService.class);
            bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        }

        @Override
        protected void onResume() {
            super.onResume();
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
            if (mBluetoothService != null) {
                final boolean result = mBluetoothService.connect(mDeviceAddress);
                Log.d(TAG, "Connect request result=" + result);
            }
        }

        @Override
        protected void onPause() {
            super.onPause();
            unregisterReceiver(mGattUpdateReceiver);
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            unbindService(mServiceConnection);
            mBluetoothService = null;
        }

        @Override
        public boolean onCreateOptionsMenu(Menu menu) {
            getMenuInflater().inflate(R.menu.gatt_services, menu);
            if (mConnected) {
                menu.findItem(R.id.menu_connect).setVisible(false);
                menu.findItem(R.id.menu_disconnect).setVisible(true);
            } else {
                menu.findItem(R.id.menu_connect).setVisible(true);
                menu.findItem(R.id.menu_disconnect).setVisible(false);
            }
            return true;
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch(item.getItemId()) {
                case R.id.menu_connect:
                    mBluetoothService.connect(mDeviceAddress);
                    return true;
                case R.id.menu_disconnect:
                    mBluetoothService.disconnect();
                    return true;
                case android.R.id.home:
                    onBackPressed();
                    return true;
            }
            return super.onOptionsItemSelected(item);
        }

        private void updateConnectionState(final int resourceId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //mConnectionState.setText(resourceId);
                }
            });
        }

        private void displayData(String data) {
            if (data != null) {
                mDataField.setText(data);
            }
        }


        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
        private void displayGattServices(List<BluetoothGattService> gattServices) {
            if (gattServices == null) return;
            String uuid = null;
            String unknownServiceString = getResources().getString(R.string.unknown_service);
            String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
            ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
            ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                    = new ArrayList<ArrayList<HashMap<String, String>>>();
            mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();


            for (BluetoothGattService gattService : gattServices) {
                HashMap<String, String> currentServiceData = new HashMap<String, String>();
                uuid = gattService.getUuid().toString();
                currentServiceData.put(
                        LIST_NAME, GAttributes.lookup(uuid, unknownServiceString));
                currentServiceData.put(LIST_UUID, uuid);
                gattServiceData.add(currentServiceData);

                ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                        new ArrayList<HashMap<String, String>>();
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();
                ArrayList<BluetoothGattCharacteristic> charas =
                        new ArrayList<BluetoothGattCharacteristic>();


                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    charas.add(gattCharacteristic);
                    HashMap<String, String> currentCharaData = new HashMap<String, String>();
                    uuid = gattCharacteristic.getUuid().toString();
                    currentCharaData.put(
                            LIST_NAME, GAttributes.lookup(uuid, unknownCharaString));
                    currentCharaData.put(LIST_UUID, uuid);
                    gattCharacteristicGroupData.add(currentCharaData);
                }
                mGattCharacteristics.add(charas);
                gattCharacteristicData.add(gattCharacteristicGroupData);
            }

            SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                    this,
                    gattServiceData,
                    android.R.layout.simple_expandable_list_item_2,
                    new String[] {LIST_NAME, LIST_UUID},
                    new int[] { android.R.id.text1, android.R.id.text2 },
                    gattCharacteristicData,
                    android.R.layout.simple_expandable_list_item_2,
                    new String[] {LIST_NAME, LIST_UUID},
                    new int[] { android.R.id.text1, android.R.id.text2 }
            );
            mGattServicesList.setAdapter(gattServiceAdapter);
        }

        private static IntentFilter makeGattUpdateIntentFilter() {
            final IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BluetoothService.ACTION_GATT_CONNECTED);
            intentFilter.addAction(BluetoothService.ACTION_GATT_DISCONNECTED);
            intentFilter.addAction(BluetoothService.ACTION_GATT_SERVICES_DISCOVERED);
            intentFilter.addAction(BluetoothService.ACTION_DATA_AVAILABLE);
            return intentFilter;
        }
        public void onClickUpLight(View v){
            if(mBluetoothService != null) {
                mBluetoothService.writeCustomCharacteristic(15);
            }
        }
        public void onClickLowLight(View v){
            if(mBluetoothService != null) {
                mBluetoothService.writeCustomCharacteristic(16);
            }
        }
        public void onClickHi(View v){
            if(mBluetoothService != null) {
                mBluetoothService.writeCustomCharacteristic(17);
            }
        }
        public void onClickMed(View v){
            if(mBluetoothService != null) {
                mBluetoothService.writeCustomCharacteristic(18);
            }
        }
        public void onClickLow(View v){
            if(mBluetoothService != null) {
                mBluetoothService.writeCustomCharacteristic(19);
            }
        }
        public void onClickOff(View v){
            if(mBluetoothService != null) {
                mBluetoothService.writeCustomCharacteristic(20);
            }
        }
}

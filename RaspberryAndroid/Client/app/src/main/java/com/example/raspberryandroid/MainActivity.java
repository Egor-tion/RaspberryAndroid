package com.example.raspberryandroid;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Set;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import android.bluetooth.BluetoothSocket;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

import static android.R.layout.simple_list_item_1;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 0;
    private static final int REQUEST_DISCOVER_BT = 1;
    final String UUID_STRING_WELL_KNOWN_SPP = "00001108-0000-1000-8000-00805f9b34fb";

    TextView mStatusBlueTv;
    ImageView mBlueIv;
    Button mOnBtn, mOffBtn, mDiscoverBtn, mPairedBtn;

    BluetoothAdapter mBlueAdapter;

    ArrayList< String> pairedDeviceArrayList;
    ListView listViewPairedDevice;
    TextView textStatus;
    ArrayAdapter< String> pairedDeviceAdapter;
    ThreadConnectBTdevice myThreadConnectBTdevice;
    ThreadConnected myThreadConnected;
    private UUID myUUID;
    private StringBuilder sb = new StringBuilder();



    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mStatusBlueTv = findViewById(R.id.statusBluetoothTv);
        listViewPairedDevice         = findViewById(R.id.list);
        mBlueIv       = findViewById(R.id.bluetoothIv);
        mOnBtn        = findViewById(R.id.onBtn);
        mOffBtn       = findViewById(R.id.offBtn);
        mDiscoverBtn  = findViewById(R.id.discoverableBtn);
        mPairedBtn    = findViewById(R.id.pairedBtn);
        textStatus    = findViewById(R.id.status);

        myUUID = UUID.fromString(UUID_STRING_WELL_KNOWN_SPP);
        //adapter
        mBlueAdapter = BluetoothAdapter.getDefaultAdapter();

        //check if bluetooth is available or not
        if (mBlueAdapter == null){
            mStatusBlueTv.setText("Bluetooth is not available");
        }
        else {
            mStatusBlueTv.setText("Bluetooth is available");
        }



        //on btn click
        mOnBtn.setOnClickListener(v -> {
            if (!mBlueAdapter.isEnabled()){
                showToast("Turning On Bluetooth...");
                //intent to on bluetooth
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, REQUEST_ENABLE_BT);
            }
            else {
                showToast("Bluetooth is already on");
            }
            setup();
        });
        //discover bluetooth btn click
        mDiscoverBtn.setOnClickListener(v -> {
            if (!mBlueAdapter.isDiscovering()){
                showToast("Making Your Device Discoverable");
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                startActivityForResult(intent, REQUEST_DISCOVER_BT);
            }
        });
        //off btn click
        mOffBtn.setOnClickListener(v -> {
            if (mBlueAdapter.isEnabled()){
                mBlueAdapter.disable();
                showToast("Turning Bluetooth Off");
            }
            else {
                showToast("Bluetooth is already off");
            }
        });



    }

    private void setup(){
        Set<BluetoothDevice> pairedDevices = mBlueAdapter.getBondedDevices();
        if (pairedDevices.size()>0){
            pairedDeviceArrayList = new ArrayList<>();
            for (BluetoothDevice device:pairedDevices){
                pairedDeviceArrayList.add(device.getName() + "\n" + device.getAddress());
            }
            pairedDeviceAdapter = new ArrayAdapter<>(MainActivity.this, simple_list_item_1, pairedDeviceArrayList);
            listViewPairedDevice.setAdapter(pairedDeviceAdapter);
            listViewPairedDevice.setOnItemClickListener((parent, view, position, id) -> {
                listViewPairedDevice.setVisibility(View.GONE);
                String itemValue = (String)listViewPairedDevice.getItemAtPosition(position);
                String MAC = itemValue.substring(itemValue.length() - 17);
                BluetoothDevice device2 = mBlueAdapter.getRemoteDevice(MAC);
                myThreadConnectBTdevice = new ThreadConnectBTdevice(device2);
                myThreadConnectBTdevice.start();
            });
        }

    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        if(myThreadConnectBTdevice!=null)
            myThreadConnectBTdevice.cancel();
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                //bluetooth is on
                showToast("Bluetooth is on");
            } else {
                //user denied to turn bluetooth on
                showToast("could't on bluetooth");
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private class ThreadConnectBTdevice extends Thread{
        private BluetoothSocket bluetoothSocket = null;
        private ThreadConnectBTdevice(BluetoothDevice device){
            try{
                bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(myUUID);
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }

        @Override
        public void run(){
            boolean success = false;
            try{
                bluetoothSocket.connect();
                success = true;
            }
            catch (IOException e){
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "No connection", Toast.LENGTH_LONG).show();
                    listViewPairedDevice.setVisibility(View.VISIBLE);
                });
                try {
                    bluetoothSocket.close();
                }
                catch (IOException e1){
                    e1.printStackTrace();
                }
            }
            if(success){
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connected successfully", Toast.LENGTH_LONG).show());

                myThreadConnected = new ThreadConnected(bluetoothSocket);
                myThreadConnected.start();
                myThreadConnected.write();

            }
        }

        public void cancel(){
            Toast.makeText(getApplicationContext(), "Close socket", Toast.LENGTH_LONG).show();
            try{
                bluetoothSocket.close();
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private class ThreadConnected extends Thread{
        private final InputStream connectedInputStream;
        private final OutputStream connectedOutputStream;
        private String sbprint;

        public ThreadConnected (BluetoothSocket socket){
            InputStream in = null;
            OutputStream out = null;
            try{
                in = socket.getInputStream();
                out = socket.getOutputStream();
            }
            catch (IOException e){
                e.getStackTrace();
            }
            connectedInputStream = in;
            connectedOutputStream = out;


            PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(out));
            printWriter.write("Meow");
            printWriter.flush();
        }

        @Override
        public void run(){
            byte[] buffer = new byte[4096];
            int bytes;
            while(true){
                try{

                    bytes = connectedInputStream.read(buffer);
                    String strRecieved = new String(buffer, 0, bytes);
                    final String msgRecieved = String.valueOf(bytes) + "bytes recieved: \n" + strRecieved;
                    runOnUiThread(() -> textStatus.setText(msgRecieved));
                }
                catch (IOException e){
                    e.printStackTrace();

                    final String msgLost = "Connection lost :( \n" + e.getMessage();
                    runOnUiThread(() -> textStatus.setText(msgLost));
                }
            }
        }

        public void write(){
            try{
                byte[] buffer = new byte[4096];
                buffer[1]=1;
                connectedOutputStream.write(buffer[1]);
                connectedOutputStream.flush();
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    //toast message function
    private void showToast(String msg){
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }


}
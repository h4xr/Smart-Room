package com.ieeeniec.ieeeniecsmartroom;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    BluetoothAdapter mBluetoothAdapter;
    Set<BluetoothDevice> pairedDevice;
    BluetoothDevice mDevice;
    ConnectThread mConnectThread;
    Switch lt1;
    Switch lt2;
    Switch lt3;
    Switch f1;
    Switch f2;
    Switch f3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lt1 = (Switch) findViewById(R.id.light1);
        lt2 = (Switch) findViewById(R.id.light2);
        lt3 = (Switch) findViewById(R.id.light3);
        f1 = (Switch) findViewById(R.id.fan1);
        f2 = (Switch)  findViewById(R.id.fan2);
        f3 = (Switch) findViewById(R.id.fan3);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        lt1.setOnCheckedChangeListener(occl);
        lt2.setOnCheckedChangeListener(occl);
        lt3.setOnCheckedChangeListener(occl);
        f1.setOnCheckedChangeListener(occl);
        f2.setOnCheckedChangeListener(occl);
        f3.setOnCheckedChangeListener(occl);

        if(mBluetoothAdapter==null) {
            //Check to see if the device supports Bluetooth or not
            Toast.makeText(MainActivity.this, "Bluetooth Support Not Found", Toast.LENGTH_LONG).show();
        }

        if(!mBluetoothAdapter.isEnabled()) {
            //Check to see if bluetooth is enabled or not, if not then enable it
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }

        pairedDevice = mBluetoothAdapter.getBondedDevices();
        if(pairedDevice.size()>0) {
            //We loop through the list of paired bluetooth devices and select the last one
            for(BluetoothDevice device : pairedDevice) {
                mDevice = device;
            }
        }

        mConnectThread = new ConnectThread(mDevice);
        mConnectThread.start();



    }

    CompoundButton.OnCheckedChangeListener occl = new CompoundButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if(isChecked) {
                String tag = (String) buttonView.getTag();
                mConnectThread.mConnectedThread.write(tag.getBytes());
            }
        }
    };

    /**
     * A private class to manage the bluetooth connection
     * Don't merge with the Main Thread since connection request may cause a significant block in
     * UI Thread.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        ConnectedThread mConnectedThread;

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            }catch (IOException e) {
                Toast.makeText(MainActivity.this, "Bluetooth socket error", Toast.LENGTH_LONG).show();
            }
            mmSocket = tmp;
        }

        public void run() {
            mBluetoothAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
            } catch (IOException connectException) {
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Toast.makeText(MainActivity.this, "Bluetooth connection error", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            mConnectedThread = new ConnectedThread(mmSocket);
            mConnectedThread.start();
        }

        public void cancel() {
            try {
                mmSocket.close();
            }catch(IOException closeException) {}
        }
    }

    /**
     * ConnectedThread is used to send and receive data from the connected bluetooth device
     * Since the task needs to happen in background, we run it in a separate thread
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            }catch (IOException e) {
                Toast.makeText(MainActivity.this, "IO Stream Error", Toast.LENGTH_LONG).show();
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte [] buffer = new byte[1024];
            int begin = 0;
            int bytes = 0;
            while (true) {
                try {
                    bytes += mmInStream.read(buffer, bytes, buffer.length - bytes);
                    for(int i=begin;i<bytes;i++) {
                        if(buffer[i]=="#".getBytes()[0]) {
                            mHandler.obtainMessage(1, begin, i, buffer).sendToTarget();
                            begin = i+1;
                            if(i==i-1) {
                                bytes = 0;
                                begin = 0;
                            }
                        }
                    }
                }catch (IOException e) {
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            }catch (IOException e) {
                Toast.makeText(MainActivity.this, "Unable to send data", Toast.LENGTH_SHORT).show();
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            }catch (IOException e) {}
        }
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            byte[] writeBuf = (byte[]) msg.obj;
            int begin = (int)msg.arg1;
            int end = (int)msg.arg2;

            switch(msg.what) {
                case 1:
                    String writeMessage = new String(writeBuf);
                    writeMessage = writeMessage.substring(begin,end);
                    break;
            }
        }

    };

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
}

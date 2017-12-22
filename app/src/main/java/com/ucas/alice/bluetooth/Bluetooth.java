package com.ucas.alice.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import iie.dcs.crypto.Crypto;

/**
 * Created by tang on 12/4/17.
 */

public class Bluetooth{

    // External varies
    private Activity mainActivity;
    private Crypto mCrypto=Crypto.getInstance();

    // Bluetooth varies
    private String TAG = "Bluetooth";
    private BluetoothAdapter mBluetoothAdapter;
    private List<String> devicesAddress;

    // Message code
    private final static String CLIENT_HI = "0";
    private final static String SERVER_HI = "1";
    private final static String SERVER_SUCC = "2";

    // Socket input and output
    private InputStream mInputStream = null;
    private OutputStream mOutputStream = null;
    private BluetoothSocket mSocket = null;

    // Socket status
    private int currentDevice = -1;

    public Bluetooth(Activity activity){
        mainActivity = activity;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    //check if the device support bluetooth
    void CheckHardware(){
        if (mBluetoothAdapter == null ){
            // Device does not support Bluetooth, exit this App
            utils.popup(mainActivity, mainActivity.getResources().getString(R.string.hardware_issue));
            System.exit(1);
        }

        if(!mBluetoothAdapter.isEnabled()){
            utils.popup(mainActivity, mainActivity.getResources().getString(R.string.enable_bt));
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mainActivity.startActivityForResult(enableBtIntent, 1);
        }
    }

    // Get the list of connected devices
    String[] Devices(){
        Set<BluetoothDevice> pairedDevices= mBluetoothAdapter.getBondedDevices();

        int number = pairedDevices.size();
        if (number > 0){
            devicesAddress = new ArrayList<>();
            List<String> deviceName = new ArrayList<>();

            for (BluetoothDevice device : pairedDevices){
                devicesAddress.add(device.getAddress());
                deviceName.add(device.getName());
            }

            return deviceName.toArray(new String[0]);
        } else {
            // No devices connected
            return null;
        }
    }

    // Initial bluetooth socket with specific device
    private void ConnectWithPeer(int position){
        if (currentDevice != position){
            currentDevice = position;
            // Get bluetooth device from device address
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(devicesAddress.get(position));
            UUID DEFAULT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice
                mSocket = device.createRfcommSocketToServiceRecord(DEFAULT_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }

            // Initial input and output stream
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                Log.e(TAG, "Failed to connect server", connectException);
                try {
                    mSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
            }

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                mInputStream = mSocket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Input stream null", e);
            }
            try {
                mOutputStream = mSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Output stream null", e);
            }
        }
    }

    boolean Authenticate(int position){
        // Cancel discovery, and connect with the specific device
        mBluetoothAdapter.cancelDiscovery();
        ConnectWithPeer(position);
        boolean result;
        if (mInputStream == null || mOutputStream == null){
            Log.d(TAG, "input output stream null");
            result =true;
        } else {
            result = DoAuthenticate();
        }
        return result;
    }

    // Authentication framework, which contains request socket from the system
    // and exchange authentication data with peer.
    private boolean DoAuthenticate(){
        // Check socket connection
        if ( shakeHand() ){
            Log.d(TAG, "shake hand error");
            return true;
        }

        // Check secure core
        if(!mCrypto.ConnectSecureCore(mainActivity)){
            Log.d(TAG, "connecting secure core failed");
            return true;
        } else if(!mCrypto.isReady()){
            Log.d(TAG, "secure core is not ready");
            return true;
        }

        // Get public key from secure core, and send it to pc
        byte[] pubKey=mCrypto.getPublicKey();
        if(pubKey==null){
            Log.d(TAG, "public key null");
            return true;
        }
        write(pubKey);

        // Receive challenge message
        String serverMessage = read();
        if (serverMessage == null){
            // Log.d(TAG, "Challenge data empty");
            return true;
        }
        // Sign the message, and send signature to peer
        byte[] sign = mCrypto.hashAndSignData(serverMessage.getBytes());
        if (sign == null){
            Log.d(TAG, "signature failed");
            return true;
        }
        write(sign);

        String serverResult = read();
        if (serverResult == null){
            Log.d(TAG, "result code null");
            return true;
        } else {
            // Check authentication result, if it equals "2" return false, otherwise, true.
            return !serverResult.equals(SERVER_SUCC);
        }
    }

    // Shake hands with peer
    private boolean shakeHand(){
        // Shake hands with server
        write(CLIENT_HI.getBytes());
        String serverHI = read();
        if (serverHI == null){
            Log.d(TAG, "serverHI null");
            return true;
        } else {
            return !serverHI.equals(SERVER_HI);
        }
    }

    // write bytes to peer
    public void write(byte[] bytes) {
        try {
            mOutputStream.write(bytes);
        } catch (IOException e) {
            // Log.e(TAG, "Error sending data", e);
        }
    }

    // read bytes from peer
    private String read() {
        byte[] buffer = new byte[1024];
        try {
            // Read from the InputStream.
            int length = mInputStream.read(buffer);
            String data = new String(buffer).replace("\0", "");
            Log.d(TAG, "Data("+length+"): "+data);
            return data;
        } catch (IOException e) {
            Log.d(TAG, "Error reading data", e);
            return null;
        }
    }
}
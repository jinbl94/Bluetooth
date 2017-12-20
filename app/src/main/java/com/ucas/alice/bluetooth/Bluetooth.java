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
import java.math.BigInteger;
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
                // Log.e(TAG, "Socket's create() method failed", e);
            }

            // Initial input and output stream
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mSocket.close();
                } catch (IOException closeException) {
                    // Log.e(TAG, "Could not close the client socket", closeException);
                }
            }

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                mInputStream = mSocket.getInputStream();
            } catch (IOException e) {
                // Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                mOutputStream = mSocket.getOutputStream();
            } catch (IOException e) {
                // Log.e(TAG, "Error occurred when creating output stream", e);
            }
        }
    }

    void Authenticate(int position){
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

        // Log.d(TAG, "authentication result: "+result);
        if(result){
            utils.popup(mainActivity, mainActivity.getResources().getString(R.string.auth_fail));
        } else {
            utils.popup(mainActivity, mainActivity.getResources().getString(R.string.auth_succeed));
        }
    }

    // Authentication framework, which contains request socket from the system
    // and exchange authentication data with peer.
    private boolean DoAuthenticate(){
        // Check socket connection
        if ( shakeHand() ){
            // Log.d(TAG, "shake hand error");
            return true;
        }

        // Check secure core
        if(!mCrypto.ConnectSecureCore(mainActivity)){
            // Log.d(TAG, "connecting secure core failed");
            return true;
        } else if(!mCrypto.isReady()){
            // Log.d(TAG, "secure core is not ready");
            return true;
        }

        // Get public key from secure core, and send it to pc
        byte[] pubKey=mCrypto.getPublicKey();
        if(pubKey==null){
            // Log.d(TAG, "can't get public key");
            return true;
        }
        // Log.d(TAG, "pubKey: "+new BigInteger(pubKey).toString(16));
        write(pubKey);

        // Receive challenge message
        byte[] bitMessage = read();
        if (bitMessage == null){
            // Log.d(TAG, "Challenge data empty");
            return true;
        }
        String strMessage = new String(bitMessage).replace("\0", "");
        // Log.d(TAG, "cMessage: "+new String(cMessage).replace("\0", ""));
        // Sign the message, and send signature to peer
        byte[] sign = mCrypto.hashAndSignData(strMessage.getBytes());
        // Log.d(TAG, "sign: "+new BigInteger(sign).toString(16));
        if (sign == null){
            // Log.d(TAG, "signature failed");
            return true;
        }
        write(sign);

        byte[] code = read();
        if (code == null){
            // Log.d(TAG, "Empty code");
            return true;
        } else {
            // Check authentication result, if it equals "2" return false, otherwise, true.
            String pcResult = new String(code).replace("\0", "");
            // Log.d(TAG, "pcResult: "+new String(pcResult).replace("\0", ""));
            return !pcResult.equals(SERVER_SUCC);
        }
    }

    // Shake hands with peer
    private boolean shakeHand(){
        // Use this buffer to point to received data
        byte[] data;
        // Shake hands with server
        write(CLIENT_HI.getBytes());
        data = read();
        if (data == null){
            // Log.d(TAG, "no shake hand data received");
            return true;
        } else {
            String pcCode = new String(data).replace("\0", "");
            return !pcCode.equals(SERVER_HI);
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
    private byte[] read() {
        byte[] buffer = new byte[1024];
        try {
            // Read from the InputStream.
            int length = mInputStream.read(buffer);
            // Log.d(TAG, "Data length: "+length);
            return buffer;
        } catch (IOException e) {
            // Log.d(TAG, "Error reading data", e);
            return null;
        }
    }
}
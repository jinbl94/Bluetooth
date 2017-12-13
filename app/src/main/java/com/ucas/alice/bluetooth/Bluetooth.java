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

    private String TAG = "Bluetooth";
    private Activity mainActivity;
    private BluetoothAdapter mBluetoothAdapter;

    private final static String CLIENT_HI = "0";
    private final static String SERVER_HI = "1";
    private final static String SERVER_SUCC = "2";
    private final static String SERVER_FAIL = "3";
    
    private InputStream mInputStream = null;
    private OutputStream mOutputStream = null;

    private Crypto mCrypto=Crypto.getInstance();

    private List<String> devicesAddress;

    public Bluetooth(Activity activity){
        mainActivity = activity;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    //check if the device support bluetooth
    public void CheckHardware(){
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
    public String[] Devices(){
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
    public void ConnectWithPeer(String address){
        // Get bluetooth device from device address
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        BluetoothSocket socket = null;
        UUID DEFAULT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

        try {
            // Get a BluetoothSocket to connect with the given BluetoothDevice
            socket = device.createRfcommSocketToServiceRecord(DEFAULT_UUID);
        } catch (IOException e) {
            Log.e(TAG, "Socket's create() method failed", e);
        }

        // Initial input and output stream
        try {
            // Connect to the remote device through the socket. This call blocks
            // until it succeeds or throws an exception.
            socket.connect();
        } catch (IOException connectException) {
            // Unable to connect; close the socket and return.
            try {
                socket.close();
            } catch (IOException closeException) {
                Log.e(TAG, "Could not close the client socket", closeException);
            }
        }

        // Get the input and output streams; using temp objects because
        // member streams are final.
        try {
            mInputStream = socket.getInputStream();
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when creating input stream", e);
        }
        try {
            mOutputStream = socket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when creating output stream", e);
        }
    }

    // Authentication framework, which contains request socket from the system
    // and exchange authentication data with peer.
    public boolean Authenticate(int position){
        // Check secure core
        if(!mCrypto.ConnectSecureCore(mainActivity)){
            utils.popup(mainActivity, mainActivity.getResources().getString(R.string.secure_core_failed));
            return true;
        } else if(!mCrypto.isReady()){
            utils.popup(mainActivity, mainActivity.getResources().getString(R.string.secure_core_failed));
            return true;
        }

        // Cancel discovery because it otherwise slows down the connection
        mBluetoothAdapter.cancelDiscovery();
        // Check connection
        ConnectWithPeer(devicesAddress.get(position));
        boolean statu = shakeHand();
        if (statu){
            utils.popup(mainActivity,mainActivity.getResources().getString(R.string.auth_fail));
            return statu;
        }

        // Get public key from secure core
        byte[] pubKey=mCrypto.getPublicKey();
        if(pubKey==null){
            utils.popup(mainActivity, mainActivity.getResources().getString(R.string.public_key_failed));
            return true;
        }
        // Send public key to server
        write(pubKey);

        // Receive chanlenge message
        byte[] cMessage = read();

        // Sign chalenge message
        byte[] sign = mCrypto.hashAndSignData(cMessage);
        if (sign == null){
            utils.popup(mainActivity, mainActivity.getResources().getString(R.string.signature_failed));
            return true;
        }

        // Send signature to server
        write(sign);

        // Check result from pc
        byte[] pcResult = read();
        if (pcResult.toString().equals(SERVER_SUCC)){
            return false;
        } else if(pcResult.toString().equals(SERVER_FAIL)) {
            utils.popup(mainActivity, mainActivity.getResources().getString(R.string.unlock_failed));
            return true;
        } else {
            utils.popup(mainActivity, mainActivity.getResources().getString(R.string.unknown_code));
            return true;
        }
    }

    // Shake hands with peer
    private boolean shakeHand(){
        // Use this buffer to point to received data
        String mBuffer;

        // Shake hands with server
        write(CLIENT_HI.getBytes());
        mBuffer = read().toString();

        // todo: the format of bytes receive from server is uncertain, this codes need further modify
        if (mBuffer != SERVER_HI){
            return true;
        }
        
        return false;
    }

    // write bytes to peer
    public void write(byte[] bytes) {
        try {
            mOutputStream.write(bytes);
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when sending data", e);
        }
    }

    // read bytes from peer
    public byte[] read() {
        byte[] buffer = new byte[1024];

        try {
            // Read from the InputStream.
            mInputStream.read(buffer);
            return buffer;
        } catch (IOException e) {
            Log.d(TAG, "Input stream was disconnected", e);
        }

        return null;
    }
}

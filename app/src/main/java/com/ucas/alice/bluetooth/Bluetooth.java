package com.ucas.alice.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    private SharedPreferences mSharedPreferences;
    private final static String PREFFILE = "preference_file";
    private final static String AUTHCODE = "authentication_code";
    private final static String RSAPRIKEY = "rsa_private_key";
    private final static String RSAPUBKEY = "rsa_public key";

    private String mAuthCode = null;
    private String mPrivateKey = null;
    private String mPublicKey = null;

    private List<String[]> devices = new ArrayList<>();

    public Bluetooth(Activity activity){
        mainActivity = activity;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mSharedPreferences = mainActivity.getSharedPreferences(PREFFILE, 0);
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
    public List<String[]> Devices(){
        Set<BluetoothDevice> pairedDevices= mBluetoothAdapter.getBondedDevices();

        int number = pairedDevices.size();
        if (number > 0){
            String deviceNameAddress[] = new String[2];

            for (BluetoothDevice device : pairedDevices){
                deviceNameAddress[0] = device.getName();
                deviceNameAddress[1] = device.getAddress();
                devices.add(deviceNameAddress);
            }

            return devices;
        } else {
            // No devices connected
            utils.popup(mainActivity, mainActivity.getResources().getString(R.string.no_device));
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

    // Start with nothing
    // Say, generate keypair, store private key and authentication message
    // send public key and authentication message
    public void Initialize(String address){
        // Generate authentication message
        mAuthCode = UUID.randomUUID().toString();

        // Generate keypair
        KeyPairGenerator kpg = null;
        KeyPair kp;
        PublicKey publicKey;
        PrivateKey privateKey;
        try {
            kpg = KeyPairGenerator.getInstance("RSA");

        } catch (Exception e){
            Log.e(TAG,"No such alg" ,e);
        }

        kpg.initialize(1024);
        kp = kpg.genKeyPair();
        mPublicKey = kp.getPublic().toString();
        mPrivateKey = kp.getPrivate().toString();
        
        // Store authentication message
        StoreMessage(mAuthCode, mPrivateKey, mPublicKey);
        
        if(SendInitMessage(mAuthCode, mPublicKey)){
            utils.popup(mainActivity, mainActivity.getResources().getString(R.string.auth_fail));
        } else {
            utils.popup(mainActivity, mainActivity.getResources().getString(R.string.auth_succeed));
        }
    }

    // Store message
    private boolean StoreMessage(String authCode, String privatekey, String publickey){
        // Cache String values
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putString(AUTHCODE, authCode);
        editor.putString(RSAPRIKEY, privatekey);
        editor.putString(RSAPUBKEY, publickey);

        // Commit data into file
        editor.commit();
        return false;
    }

    // Send initialization data to server
    private boolean SendInitMessage(String authCode, String publickey){
        // Use this buffer to point to received data
        String mBuffer;

        if (ShakeHand()) {
            return true;
        } else {
            // Send public key and authentication message
            write(authCode);
            write(publickey);
        }

        mBuffer = read();
        if (mBuffer == SERVER_SUCC){
            return false;
        } else {
            return true;
        }
    }

    // Authentication framework, which contains request socket from the system
    // and exchange authentication data with peer.
    public void Authenticate(String address, String authCode){
        // Cancel discovery because it otherwise slows down the connection
        mBluetoothAdapter.cancelDiscovery();

        ConnectWithPeer(address);

        boolean result = DoAuthentication();

        // Check if the authentication was succeed
        if (result){
            utils.popup(mainActivity, mainActivity.getResources().getString(R.string.auth_fail));
        } else {
            utils.popup(mainActivity, mainActivity.getResources().getString(R.string.auth_succeed));
        }
    }

    // Details of authentication
    /*
    This procedure has several steps.
    1. Say hello to peer.
    2. Sign the code with private key.
    3. Check authentication result.
    * */
    private boolean DoAuthentication (){
        // Use this buffer to point to received data
        String mBuffer;
        
        if (ShakeHand()) {
            return true;
        }

        // initialize RSA with saved keypair
        // "Decrypt" message with private key, namely signature
        // RSA mRSA = new RSA();
        LoadMessage();
        try {
            //mRSA.SetPrivateKey(mPrivateKey);
        } catch (Exception e){
            Log.e(TAG, "Set RSA private key error", e);
        }

        // "Encrypt authCode"
        String encCode = null;
        try {
            //encCode = mRSA.Decrypt(mAuthCode);
        } catch (Exception e) {
            Log.e(TAG, "RSA decrypt error", e);
        }

        // Send the result to server
        write(encCode);

        mBuffer = read();

        // Check what the server returned
        if(mBuffer == SERVER_SUCC) {
            //authentication succeed
            return false;
        } else if (mBuffer == SERVER_FAIL){
            // authentication failed
            return true;
        } else {
            // unknown code
            return true;
        }
    }

    // Load private key and authentication code
    private void LoadMessage(){
        // Load data
        mAuthCode = mSharedPreferences.getString(AUTHCODE, null);
        mPrivateKey = mSharedPreferences.getString(RSAPRIKEY, null);
        mPublicKey = mSharedPreferences.getString(RSAPUBKEY, null);
    }

    // Shake hands with peer
    private boolean ShakeHand(){
        // Use this buffer to point to received data
        String mBuffer;

        // Shake hands with server
        write(CLIENT_HI);
        mBuffer = read();

        // todo: the format of bytes receive from server is uncertain, this codes need further modify
        if (mBuffer != SERVER_HI){
            utils.popup(mainActivity,mainActivity.getResources().getString(R.string.auth_fail));
            return true;
        }
        
        return false;
    }

    // write bytes to peer
    public void write(String string) {
        try {
            mOutputStream.write(string.getBytes());
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when sending data", e);
        }
    }

    // read bytes from peer
    public String read() {
        byte[] buffer = new byte[1024];

        try {
            // Read from the InputStream.
            mInputStream.read(buffer);
            return buffer.toString();
        } catch (IOException e) {
            Log.d(TAG, "Input stream was disconnected", e);
        }

        return null;
    }
}

package com.ucas.alice.bluetooth;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import iie.dcs.crypto.Crypto;
import iie.dcs.utils.StringUtils;

public class MainActivity extends AppCompatActivity {

//    Bluetooth mBlutooth;
    TextView textM, textC1, textC2;

    RSA mRSA;

    private final static String TAG = "MainActivity";

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        mBlutooth = new Bluetooth(this);
//
//        mBlutooth.CheckHardware();
//        List<String[]> devices=mBlutooth.Devices();

        // Example of a call to a native method
        TextView tv = findViewById(R.id.sample_text);

        textM = findViewById(R.id.text_m);
        textC1 = findViewById(R.id.text_c1);
        textC2 = findViewById(R.id.text_c2);
    }
}

package com.ucas.alice.bluetooth;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    Bluetooth mBlutooth;
    TextView textM, textC1, textC2;

    private final static String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBlutooth = new Bluetooth(this);

        mBlutooth.CheckHardware();

//        textM = findViewById(R.id.text_m);
//        textC1 = findViewById(R.id.text_c1);
//        textC2 = findViewById(R.id.text_c2);
    }

    public void onEncrypt(View view){
        Log.d(TAG, "Encrypt");

        List<String[]> devices = mBlutooth.Devices();
        mBlutooth.ConnectWithPeer(devices.get(0)[1]);
        mBlutooth.write("hello tang");
    }

    public void onDecrypt(View view){

    }
}
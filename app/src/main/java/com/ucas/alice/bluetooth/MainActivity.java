package com.ucas.alice.bluetooth;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class MainActivity extends AppCompatActivity {

    private Bluetooth mBlutooth;
    private ListView mListView;

    private final static String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBlutooth = new Bluetooth(this);

        mBlutooth.CheckHardware();

        mListView = findViewById(R.id.device_list);

        refreshList();
    }

    public void onRefresh(View view){
        refreshList();
    }

    private void refreshList(){
        final String[] devices = mBlutooth.Devices();

        if (devices == null){
            utils.popup(this, getResources().getString(R.string.no_device));
        } else {

            mListView.setAdapter(
                    new ArrayAdapter<>(this,
                            android.R.layout.simple_list_item_1,
                            devices)
            );

            mListView.setOnItemClickListener(
                    new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//                        mBlutooth.Authenticate(position);
                            Log.d(TAG, "clicked " + position);
                        }
                    }
            );
        }
    }

//    private Crypto mCrypto = Crypto.getInstance();

//    public class SignTest implements Runnable {
//        Crypto mCrypto;
//        Context context;
//        SignTest(Crypto mCrypto, Context context){
//            this.mCrypto = mCrypto;
//            this.context = context;
//        }
//
//        @Override
//        public void run(){
//            //security core test
//            // Check secure core
//            if(!mCrypto.ConnectSecureCore(context)){
//                Log.d(TAG, "Can't connect security core");
//                return;
//            } else if(!mCrypto.isReady()){
//                Log.d(TAG, "Security core is not ready yet");
//                return;
//            }
//
//            // Get public key from secure core
//            byte[] pubKey=mCrypto.getPublicKey();
//            if(pubKey==null){
//                Log.d(TAG, "Failed to get public key");
//                return;
//            }
//
//            Log.d(TAG, "Public key: "+pubKey);
//
//            byte[] cMessage = {1,2,3,4};
//
//            // Sign chalenge message
//            byte[] sign = mCrypto.hashAndSignData(cMessage);
//            if (sign == null){
//                Log.d(TAG, "Signature generating failed");
//                return;
//            }
//
//            Log.d(TAG, "Signature: "+sign);
//
//            long result = mCrypto.hashAndVerifyData(cMessage, sign);
//
//            Log.d(TAG, "Signature verification result: "+result);
//        }
//    }

}
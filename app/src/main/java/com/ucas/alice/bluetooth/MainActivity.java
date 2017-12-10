package com.ucas.alice.bluetooth;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class MainActivity extends AppCompatActivity {

    Bluetooth mBlutooth;
    ListView mListView;

    private final static String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBlutooth = new Bluetooth(this);

        mBlutooth.CheckHardware();

        mListView = findViewById(R.id.device_list);
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
//                        String address = devices.get(position)[1];
//                        mBlutooth.Authenticate(address);
                            Log.d(TAG, "clicked " + position);
                        }
                    }
            );
        }
    }
}
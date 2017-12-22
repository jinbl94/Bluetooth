package com.ucas.alice.bluetooth;

import android.app.Activity;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private Activity activity;
    private Bluetooth mBlutooth;
    private ListView mListView;

    private final static String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        activity = this;

        mBlutooth = new Bluetooth(activity);

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
                            boolean result = mBlutooth.Authenticate(position);
                            if(result){
                                utils.popup(activity, getResources().getString(R.string.auth_fail));
                            } else {
                                utils.popup(activity, getResources().getString(R.string.auth_succeed));
                            }
                        }
                    }
            );
        }
    }
}
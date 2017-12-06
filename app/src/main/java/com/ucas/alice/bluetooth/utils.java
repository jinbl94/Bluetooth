package com.ucas.alice.bluetooth;

import android.app.Activity;
import android.widget.Toast;

/**
 * Created by tang on 12/4/17.
 */

public class utils {
    static public void popup(Activity activity, String message){
        Toast mToast = Toast.makeText(activity.getApplicationContext(),message,Toast.LENGTH_SHORT);
        mToast.setMargin(50,50);
        mToast.setText(message);
        mToast.show();
    }
}

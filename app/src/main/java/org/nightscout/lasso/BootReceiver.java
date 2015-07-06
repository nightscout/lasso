package org.nightscout.lasso;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("Lasso", "Received boot event");
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean startOnBoot = preferences.getBoolean(context.getString(R.string.start_on_boot_key), true);
        if (startOnBoot) {
            Log.d("Lasso", "Starting service");
            Intent serviceIntent = new Intent(context, NightscoutMonitor.class);
            context.startService(serviceIntent);
        }
    }
}

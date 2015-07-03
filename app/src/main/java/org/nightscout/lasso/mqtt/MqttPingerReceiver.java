package org.nightscout.lasso.mqtt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.nightscout.core.mqtt.Constants;
import com.nightscout.core.mqtt.MqttPinger;

public class MqttPingerReceiver extends BroadcastReceiver {
    private static final String TAG = MqttPingerReceiver.class.getSimpleName();
    private MqttPinger pinger;

    MqttPingerReceiver(MqttPinger pinger) {
        this.pinger = pinger;
        Log.d(TAG, "Pinger receiver created");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Constants.KEEPALIVE_INTENT_FILTER)) {
            Log.v(TAG, "Received a request to perform an MQTT keepalive operation on " + intent.getExtras().get("device"));
            pinger.ping();
        }
    }
}
package org.nightscout.lasso.alarm;

import android.content.Context;
import android.util.Log;

import com.nightscout.core.preferences.NightscoutPreferences;

import org.nightscout.lasso.alarm.ar2.Ar2;
import org.nightscout.lasso.preferences.AndroidPreferences;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module(
        injects = Alarm.class,
        library = true
)
public class AlarmStrategyModule {
    private Context context;

    public AlarmStrategyModule(Context context) {
        this.context = context;
    }

//    Remote = 0
//    None = 1
//    Simple = 2
//    AR2 = 3


    @Provides
    @Singleton
    AlarmStrategy provideAlarmStrategy() {
        NightscoutPreferences preferences = new AndroidPreferences(context);
        AlarmStrategy strategy;
        switch (preferences.getAlarmStrategy()) {
            case 2:
                Log.d("AlarmStrategy", "Creating Simple alarm");
                strategy = new SimpleAlarm(context);
                ((SimpleAlarm) strategy).setUrgentHighThreshold(preferences.getUrgentHighThreshold());
                ((SimpleAlarm) strategy).setWarningHighThreshold(preferences.getWarningHighThreshold());
                ((SimpleAlarm) strategy).setUrgentLowThreshold(preferences.getUrgentLowThreshold());
                ((SimpleAlarm) strategy).setWarningLowThreshold(preferences.getWarningLowThreshold());
                break;
            case 3:
                Log.d("AlarmStrategy", "Creating AR2 alarm");
                strategy = new Ar2(context);
                break;
            default:
                Log.d("AlarmStrategy", "Creating noop alarm");
                strategy = new NoopAlarm();
        }
        return strategy;
    }
}

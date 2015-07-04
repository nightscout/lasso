package org.nightscout.lasso.alarm;

import android.content.Context;

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
            case 0:
            case 1:
                strategy = new NoopAlarm();
                break;
            case 2:
                strategy = new SimpleAlarm(context);
                ((SimpleAlarm) strategy).setUrgentHighThreshold(preferences.getUrgentHighThreshold());
                ((SimpleAlarm) strategy).setWarningHighThreshold(preferences.getWarningHighThreshold());
                ((SimpleAlarm) strategy).setUrgentLowThreshold(preferences.getUrgentLowThreshold());
                ((SimpleAlarm) strategy).setWarningLowThreshold(preferences.getWarningLowThreshold());
                break;
            case 3:
                strategy = new Ar2(context);
                break;
            default:
                strategy = new NoopAlarm();
        }
        return strategy;
    }
}

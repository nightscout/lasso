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

    @Provides
    @Singleton
    AlarmStrategy provideAlarmStrategy() {
        NightscoutPreferences preferences = new AndroidPreferences(context);
        AlarmStrategy strategy;
        if (preferences.getAlarmStrategy().equals("Simple")) {
            strategy = new SimpleAlarm(context);
            ((SimpleAlarm) strategy).setUrgentHighThreshold(preferences.getUrgentHighThreshold());
            ((SimpleAlarm) strategy).setWarningHighThreshold(preferences.getWarningHighThreshold());
            ((SimpleAlarm) strategy).setUrgentLowThreshold(preferences.getUrgentLowThreshold());
            ((SimpleAlarm) strategy).setWarningLowThreshold(preferences.getWarningLowThreshold());
        } else {
            strategy = new Ar2(context);
        }
        return strategy;
    }
}

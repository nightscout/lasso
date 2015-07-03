package org.nightscout.lasso;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.nightscout.core.dexcom.records.EGVRecord;
import com.nightscout.core.model.G4Noise;
import com.nightscout.core.model.GlucoseUnit;
import com.nightscout.core.utils.IsigReading;

public class WatchMaker {
    public static void sendReadings(EGVRecord reading, G4Noise noise, IsigReading isigReading, GlucoseUnit unit, String delta, int uploaderBattery, int receiverBattery, Context context) {
        Intent intent1 = new Intent();
        intent1.setAction("com.twofortyfouram.locale.intent.action.FIRE_SETTING");
        Bundle bundle = new Bundle();
        bundle.putString("tasker_var_name", "%NSBG");
        bundle.putString("tasker_var", reading.getReading().asStr(unit));
        intent1.putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", bundle);
        context.sendBroadcast(intent1);
        bundle.putString("tasker_var_name", "%NSRTIME");
        bundle.putString("tasker_var", String.valueOf(reading.getWallTime().getMillis()));
        intent1.putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", bundle);
        context.sendBroadcast(intent1);
        bundle.putString("tasker_var_name", "%NSTR");
        bundle.putString("tasker_var", reading.getTrend().symbol());
        intent1.putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", bundle);
        context.sendBroadcast(intent1);
        bundle.putString("tasker_var_name", "%NSRAW");
        bundle.putString("tasker_var", isigReading.asStr(unit));
        intent1.putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", bundle);
        context.sendBroadcast(intent1);
        bundle.putString("tasker_var_name", "%NSULBAT");
        bundle.putString("tasker_var", String.valueOf(uploaderBattery));
        intent1.putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", bundle);
        context.sendBroadcast(intent1);
        bundle.putString("tasker_var_name", "%NSRCVBAT");
        bundle.putString("tasker_var", String.valueOf(receiverBattery));
        intent1.putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", bundle);
        context.sendBroadcast(intent1);
        bundle.putString("tasker_var_name", "%NSNOISE");
        bundle.putString("tasker_var", noise.name());
        intent1.putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", bundle);
        context.sendBroadcast(intent1);
        bundle.putString("tasker_var_name", "%NSDELTA");
        bundle.putString("tasker_var", delta);
        intent1.putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", bundle);
        context.sendBroadcast(intent1);

    }

}

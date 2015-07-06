package org.nightscout.lasso.wearables;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.nightscout.core.dexcom.SpecialValue;
import com.nightscout.core.dexcom.records.EGVRecord;
import com.nightscout.core.model.G4Noise;
import com.nightscout.core.model.GlucoseUnit;
import com.nightscout.core.utils.IsigReading;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class WatchMaker {
    public static void sendReadings(EGVRecord reading, G4Noise noise, IsigReading isigReading, GlucoseUnit unit, String delta, int uploaderBattery, int receiverBattery, Context context) {
        Intent intent1 = new Intent();
        intent1.setAction("com.twofortyfouram.locale.intent.action.FIRE_SETTING");
        Bundle bundle = new Bundle();
        bundle.putString("tasker_var_name", "%NSBG");
        String bg = null;
        String trend = "";
        if (SpecialValue.isSpecialValue(reading.getBgMgdl())) {
            bg = SpecialValue.getEGVSpecialValue(reading.getBgMgdl()).get().toString();
            delta = "";
        } else {
            bg = reading.getReading().asStr(unit);
            trend = reading.getTrend().symbol();
        }
        bundle.putString("tasker_var", bg);
        intent1.putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", bundle);
        context.sendBroadcast(intent1);
        DateTimeFormatter fmt = DateTimeFormat.forPattern("HH:mm:ss");
        String time = fmt.print(reading.getWallTime());
        bundle.putString("tasker_var_name", "%NSRTIME");
        bundle.putString("tasker_var", time);
        intent1.putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", bundle);
        context.sendBroadcast(intent1);
        bundle.putString("tasker_var_name", "%NSTR");
        bundle.putString("tasker_var", trend);
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

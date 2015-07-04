package org.nightscout.lasso.alarm;

import android.util.Log;

import com.nightscout.core.dexcom.records.EGVRecord;
import com.nightscout.core.model.GlucoseUnit;

import java.util.List;

public class NoopAlarm implements AlarmStrategy {
    @Override
    public AlarmResults analyze(List<EGVRecord> egvRecords, GlucoseUnit unit) {
        Log.d(this.getClass().getSimpleName(), "Initialized");
        return new AlarmResults();
    }
}

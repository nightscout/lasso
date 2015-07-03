package org.nightscout.lasso.alarm;

import android.content.Context;

import com.nightscout.core.dexcom.Constants;
import com.nightscout.core.dexcom.records.EGVRecord;
import com.nightscout.core.model.GlucoseUnit;

import org.nightscout.lasso.R;

import java.util.List;

public class SimpleAlarm implements AlarmStrategy {
    private int URGENT_HIGH_THRESHOLD = 260;
    private int WARNING_HIGH_THRESHOLD = 180;
    private int WARNING_LOW_THRESHOLD = 80;
    private int URGENT_LOW_THRESHOLD = 55;
    private Context context;

    public SimpleAlarm(Context context) {
        this.context = context;
    }

    private boolean inBounds(int threshold) {
        return threshold <= Constants.MAX_EGV && threshold >= Constants.MIN_EGV;
    }

    public void setThresholds(int urgentHigh, int warningHigh, int warningLow, int urgentLow) {
        setUrgentHighThreshold(urgentHigh);
        setWarningHighThreshold(warningHigh);
        setWarningLowThreshold(warningLow);
        setUrgentLowThreshold(urgentLow);
    }

    public void setUrgentHighThreshold(int threshold) throws IllegalArgumentException {
        if (inBounds(threshold)) {
            if (threshold > Math.max(WARNING_HIGH_THRESHOLD, Math.max(WARNING_LOW_THRESHOLD, URGENT_LOW_THRESHOLD))) {
                URGENT_HIGH_THRESHOLD = threshold;
            } else {
                throw new IllegalArgumentException("Invalid Urgent High threshold when compared to other thresholds");
            }
        } else {
            throw new IllegalArgumentException("Urgent High Threshold is outside of sensor boundaries");
        }
    }

    public void setWarningHighThreshold(int threshold) throws IllegalArgumentException {
        if (inBounds(threshold)) {
            if (threshold > Math.max(WARNING_LOW_THRESHOLD, URGENT_LOW_THRESHOLD) && threshold < URGENT_HIGH_THRESHOLD) {
                WARNING_HIGH_THRESHOLD = threshold;
            } else {
                throw new IllegalArgumentException("Invalid Warning High threshold when compared to other thresholds");
            }
        } else {
            throw new IllegalArgumentException("Warning High Threshold is outside of sensor boundaries");
        }
    }

    public void setUrgentLowThreshold(int threshold) throws IllegalArgumentException {
        if (inBounds(threshold)) {
            if (threshold < Math.min(WARNING_LOW_THRESHOLD, Math.min(URGENT_HIGH_THRESHOLD, WARNING_HIGH_THRESHOLD))) {
                URGENT_LOW_THRESHOLD = threshold;
            } else {
                throw new IllegalArgumentException("Invalid Urgent Low threshold when compared to other thresholds");
            }
        } else {
            throw new IllegalArgumentException("Urgent Low Threshold is outside of sensor boundaries");
        }
    }

    public void setWarningLowThreshold(int threshold) throws IllegalArgumentException {
        if (inBounds(threshold)) {
            if (threshold > URGENT_LOW_THRESHOLD && threshold < Math.min(URGENT_HIGH_THRESHOLD, WARNING_HIGH_THRESHOLD)) {
                WARNING_LOW_THRESHOLD = threshold;
            } else {
                throw new IllegalArgumentException("Invalid warning low threshold when compared to other thresholds");
            }
        } else {
            throw new IllegalArgumentException("Warning Low threshold is outside of sensor boundaries");
        }
    }

    @Override
    public AlarmResults analyze(List<EGVRecord> egvRecords, GlucoseUnit unit) {
        EGVRecord lastEgvRecord = egvRecords.get(egvRecords.size() - 1);
        AlarmResults alarmResults = new AlarmResults();
        alarmResults.severity = AlarmSeverity.NONE;
        alarmResults.message = context.getString(R.string.alarm_notification_standard_body,
                lastEgvRecord.getReading().asStr(unit),
                lastEgvRecord.getTrend().symbol());
        if (lastEgvRecord.getBgMgdl() > URGENT_HIGH_THRESHOLD) {
            alarmResults.severity = AlarmSeverity.URGENT;
            alarmResults.title = context.getString(R.string.alarm_notification_urgent_title, context.getString(R.string.app_name));
        } else if (lastEgvRecord.getBgMgdl() > WARNING_HIGH_THRESHOLD) {
            alarmResults.severity = AlarmSeverity.WARNING;
            alarmResults.title = context.getString(R.string.alarm_notification_warning_title, context.getString(R.string.app_name));
        }
        if (lastEgvRecord.getBgMgdl() < URGENT_LOW_THRESHOLD) {
            alarmResults.severity = AlarmSeverity.URGENT;
            alarmResults.title = context.getString(R.string.alarm_notification_urgent_title, context.getString(R.string.app_name));
        } else if (lastEgvRecord.getBgMgdl() < WARNING_LOW_THRESHOLD) {
            alarmResults.severity = AlarmSeverity.WARNING;
            alarmResults.title = context.getString(R.string.alarm_notification_warning_title, context.getString(R.string.app_name));
        }
        return alarmResults;
    }
}

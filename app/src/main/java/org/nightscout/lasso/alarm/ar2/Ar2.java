package org.nightscout.lasso.alarm.ar2;

import android.content.Context;
import android.util.Log;

import com.nightscout.core.dexcom.Constants;
import com.nightscout.core.dexcom.SpecialValue;
import com.nightscout.core.dexcom.records.EGVRecord;
import com.nightscout.core.model.GlucoseUnit;

import net.tribe7.common.base.Optional;

import org.joda.time.DateTime;
import org.joda.time.Hours;
import org.joda.time.Minutes;
import org.nightscout.lasso.BuildConfig;
import org.nightscout.lasso.NightscoutMonitor;
import org.nightscout.lasso.R;
import org.nightscout.lasso.alarm.AlarmResults;
import org.nightscout.lasso.alarm.AlarmSeverity;
import org.nightscout.lasso.alarm.AlarmStrategy;

import java.util.List;


public class Ar2 implements AlarmStrategy {

    public static final int BG_REF = 140;
    public static final int BG_MIN = 36;
    public static final int BG_MAX = 400;
    public static final double WARN_THRESHOLD = 0.05;
    public static final double URGENT_THRESHOLD = 0.10;
    private Context context;

    public Ar2(Context context) {
        this.context = context;
        Log.d(this.getClass().getSimpleName(), "Initialized");
    }

    private Ar2Result forecast(List<EGVRecord> sgvs) {
        Ar2Result result = new Ar2Result();
        if (sgvs.size() < 2) {
            return new Ar2Result();
        }
        if (sgvs.get(sgvs.size() - 1).getBgMgdl() > 39 && sgvs.get(sgvs.size() - 2).getBgMgdl() > 39) {
            int lastIndex = sgvs.size() - 1;
            DateTime lastValidReadingTime = sgvs.get(lastIndex).getWallTime();
            long ONE_MINUTE = Minutes.minutes(1).toStandardDuration().getMillis();
            double elapsedTime = (sgvs.get(lastIndex).getWallTime().getMillis() - sgvs.get(lastIndex - 1).getWallTime().getMillis()) / ONE_MINUTE;
            double y = Math.log((double) sgvs.get(lastIndex).getBgMgdl() / (double) BG_REF);
            double[] yPair = {y, y};
            if (elapsedTime < 5.1) {
                yPair = new double[]{Math.log((double) sgvs.get(lastIndex - 1).getBgMgdl() / (double) BG_REF), y};
            }
            long ONE_HOUR = Hours.ONE.toStandardDuration().getMillis();
            Long currentTimelong = new DateTime().getMillis();
            Long lastValidReadingTimeLong = lastValidReadingTime.getMillis();
            double n = Math.ceil(12 * (0.5d + (double) (currentTimelong - lastValidReadingTimeLong) / (double) ONE_HOUR));
            double[] AR = {-0.723, 1.716};
            DateTime dt = lastValidReadingTime;
            for (int i = 0; i <= n; i++) {
                yPair = new double[]{yPair[1], AR[0] * yPair[0] + AR[1] * yPair[1]};
                dt = dt.plus(Minutes.minutes(5));
                BGPoint point = new BGPoint(dt, Math.max(BG_MIN, Math.min(BG_MAX, Math.round(BG_REF * Math.exp(yPair[1])))));
                result.predicted.add(point);
            }
            long size = Math.min(result.predicted.size() - 1, 6);
            for (int j = 0; j <= size; j++) {
                result.avgLoss += 1 / (double) size * Math.pow(Math.log10((double) result.predicted.get(j).y / (double) 120), 2);
            }
        }
        Log.e("AR2", "Results: avgLoss=" + result.avgLoss);
        int count = 0;
        for (BGPoint bgPoint : result.predicted) {
            if (BuildConfig.DEBUG) {
                Log.e("AR2", "Results: Predicted #" + count + "=" + bgPoint.y + " by " + bgPoint.x);
            }
            count += 1;
        }

        return result;
    }

    @Override
    public AlarmResults analyze(List<EGVRecord> egvRecords, GlucoseUnit unit) {
        if (egvRecords.size() == 0) {
            return new AlarmResults(context.getString(R.string.no_data_title, AlarmSeverity.WARNING.name()), AlarmSeverity.WARNING, context.getString(R.string.no_data_message, NightscoutMonitor.ANALYSIS_DURATION.getStandardMinutes()));
        }
        AlarmResults alarmResults = new AlarmResults();
        EGVRecord lastEgvRecord = egvRecords.get(egvRecords.size() - 1);
        Optional<String> specialValueMessage = SpecialValue.getSpecialValueDescr(lastEgvRecord.getBgMgdl());
        if (specialValueMessage.isPresent()) {
            if (lastEgvRecord.getBgMgdl() == Constants.MAX_EGV || lastEgvRecord.getBgMgdl() == Constants.MIN_EGV) {
                alarmResults.setTitle(context.getString(R.string.alarm_notification_urgent_title, context.getString(R.string.app_name)));
                alarmResults.setSeverityAtHighest(AlarmSeverity.URGENT);
            } else {
                alarmResults.setTitle(context.getString(R.string.alarm_notification_normal_body, context.getString(R.string.app_name)));
                alarmResults.setSeverityAtHighest(AlarmSeverity.NORMAL);
            }
            alarmResults.addMessage(specialValueMessage.get());
            return alarmResults;
        } else {
            Ar2Result ar2Results = forecast(egvRecords);
            alarmResults.addMessage(context.getString(R.string.alarm_notification_urgent_body,
                    lastEgvRecord.getReading().asStr(unit),
                    lastEgvRecord.getTrend().symbol()));
            if (ar2Results.avgLoss > URGENT_THRESHOLD) {
                Log.w("Alarm", "Urgent alarm");
                alarmResults.setSeverityAtHighest(AlarmSeverity.URGENT);
                alarmResults.setTitle(context.getString(R.string.alarm_notification_urgent_title, context.getString(R.string.app_name)));
            } else if (ar2Results.avgLoss > WARN_THRESHOLD) {
                Log.w("Alarm", "Warning");
                alarmResults.setSeverityAtHighest(AlarmSeverity.WARNING);
                alarmResults.setTitle(context.getString(R.string.alarm_notification_warning_title, context.getString(R.string.app_name)));
            } else {
                Log.w("Alarm", "All is good");
                alarmResults.setSeverityAtHighest(AlarmSeverity.NONE);
                alarmResults.setTitle(context.getString(R.string.alarm_notification_normal_title, context.getString(R.string.app_name)));
            }
        }
        return alarmResults;

    }
}

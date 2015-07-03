package org.nightscout.lasso.alarm;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.nightscout.core.dexcom.records.EGVRecord;
import com.nightscout.core.model.GlucoseUnit;

import net.tribe7.common.base.Optional;

import org.joda.time.DateTime;
import org.joda.time.Instant;
import org.joda.time.Minutes;
import org.nightscout.lasso.BuildConfig;
import org.nightscout.lasso.MainActivity;
import org.nightscout.lasso.R;
import org.nightscout.lasso.preferences.AndroidPreferences;

import java.util.List;

import javax.inject.Inject;

import dagger.ObjectGraph;


public class Alarm {
    public Minutes ALARM_TIMEAGO_WARN_MINS = Minutes.minutes(15);
    public Minutes ALARM_TIMEAGO_URGENT_MINS = Minutes.minutes(30);
    @Inject
    AlarmStrategy strategy;
    private MediaPlayer mediaPlayer;
    private NotificationManagerCompat mNotificationManager;
    private NotificationCompat.Builder mNotifyBuilder;
    private Context context;
    private SharedPreferences sharedPreferences;
    private AlarmResults previousAlarmResults = new AlarmResults();
    private AlarmResults alarmResults = new AlarmResults();
    private int notifyId = 1;
    private int ALARM_BATTERY_WARN = 15;
    private int ALARM_BATTERY_URGENT = 10;
    private AndroidPreferences preferences;


    public Alarm(Context context) {
        this.context = context;
        preferences = new AndroidPreferences(context);
        mNotificationManager = NotificationManagerCompat.from(context);
        mediaPlayer = new MediaPlayer();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        ObjectGraph.create(new AlarmStrategyModule(context)).inject(this);
        sharedPreferences.edit().remove("snooze_" + AlarmSeverity.WARNING.name()).apply();
        sharedPreferences.edit().remove("snooze_" + AlarmSeverity.URGENT.name()).apply();
    }

    public void setMediaPlayer(MediaPlayer mediaPlayer) {
        this.mediaPlayer = mediaPlayer;
    }

    public void setNotifyId(int notifyId) {
        this.notifyId = notifyId;
    }

    public AlarmResults analyzeTime(List<EGVRecord> egvRecords, GlucoseUnit unit, DateTime downloadTime) {
        AlarmResults results = new AlarmResults();
        if (egvRecords.size() == 0) {
            return results;
        }
        DateTime lastRecordWallTime = egvRecords.get(egvRecords.size() - 1).getWallTime();
        if (preferences.isStaleAlarmEnabled()) {
            if (lastRecordWallTime.plus(ALARM_TIMEAGO_URGENT_MINS).isBeforeNow()) {
                Log.d("Alarm", "Urgent stale data");
                results.setSeverityAtHighest(AlarmSeverity.URGENT);
                results.appendMessage(context.getString(R.string.alarm_timeago_urgent_message, Minutes.minutesBetween(lastRecordWallTime, Instant.now()).getMinutes()));
                results.title = context.getString(R.string.alarm_timeago_standard_title);
            } else if (lastRecordWallTime.plus(ALARM_TIMEAGO_WARN_MINS).isBeforeNow()) {
                Log.d("Alarm", "Warning stale data");
                results.setSeverityAtHighest(AlarmSeverity.WARNING);
                results.appendMessage(context.getString(R.string.alarm_timeago_warn_message, egvRecords.get(egvRecords.size() - 1).getReading().asStr(unit), unit.name(), Minutes.minutesBetween(lastRecordWallTime, Instant.now()).getMinutes()));
                results.title = context.getString(R.string.alarm_timeago_standard_title);
            }
        }
        if (downloadTime.minus(Minutes.minutes(5)).isAfter(lastRecordWallTime)) {
            Log.d("OOR", "Out of range detected");
            results.appendMessage(context.getString(R.string.alarm_out_of_range_message, Minutes.minutesBetween(lastRecordWallTime, Instant.now()).getMinutes()));
        }
        return results;
    }

    public AlarmResults analyzeBattery(Optional<Integer> uploaderBattery) {
        AlarmResults results = new AlarmResults();
        if (uploaderBattery.isPresent()) {
            Log.e("Alarm", "Battery is " + uploaderBattery.get());
            if (uploaderBattery.get() < ALARM_BATTERY_URGENT) {
                Log.d("Alarm", "Urgent low battery");
                results.setSeverityAtHighest(AlarmSeverity.URGENT);
                results.appendMessage(context.getString(R.string.alarm_uploader_battery_urgent_message));
                results.title = context.getString(R.string.alarm_uploader_battery_urgent_title);
            } else if (uploaderBattery.get() < ALARM_BATTERY_WARN) {
                Log.d("Alarm", "Warning low battery");
                results.setSeverityAtHighest(AlarmSeverity.WARNING);
                results.appendMessage(context.getString(R.string.alarm_uploader_battery_warn_message));
                results.title = context.getString(R.string.alarm_uploader_battery_warn_title);
            }
        } else {
            Log.e("Alarm", "battery is not present");
        }
        return results;
    }

    public void analyze(List<EGVRecord> egvRecords, GlucoseUnit unit, Optional<Integer> uploaderBattery, DateTime downloadTime) {
        alarmResults = new AlarmResults();
        alarmResults.mergeAlarmResults(analyzeTime(egvRecords, unit, downloadTime));
        alarmResults.mergeAlarmResults(analyzeBattery(uploaderBattery));
        alarmResults.mergeAlarmResults(strategy.analyze(egvRecords, unit));
    }

    public AlarmResults getAlarmResults() {
        return alarmResults;
    }

    public void alarm() {
        if (alarmResults.severity == AlarmSeverity.NONE && previousAlarmResults.severity != AlarmSeverity.NONE) {
            mNotificationManager.cancel(notifyId);
            stopAlert();
        }

        if (alarmResults.severity == AlarmSeverity.NONE && !preferences.areAllNotificationsEnabled()) {
            return;
        }
        showNotification(alarmResults.severity, alarmResults.title, alarmResults.message);
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        // First check to see if we are in silent mode
        boolean silentMode = ((am.getRingerMode() == AudioManager.RINGER_MODE_SILENT) ||
                am.getRingerMode() == (AudioManager.RINGER_MODE_VIBRATE));
        // If we are in silent mode then do not play the media
        if (!silentMode) {
            if (alarmResults.severity == AlarmSeverity.WARNING) {
                playAlert(R.raw.alarm);
            } else if (alarmResults.severity == AlarmSeverity.URGENT) {
                playAlert(R.raw.alarm2);
            }
        } else {
            Log.d("Alarms", "Honoring silent mode");
        }
        previousAlarmResults = alarmResults;
    }

    private void showNotification(AlarmSeverity severity, String title, String message) {
        NotificationCompat.WearableExtender wearableExtender =
                new NotificationCompat.WearableExtender();
        Intent notifyIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(context, 0, notifyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
        long[] vibePattern;

        switch (severity) {
            case URGENT:
                vibePattern = new long[]{75, 50, 50, 50, 75, 50, 50, 50, 75, 50, 50, 50, 75, 50, 50, 50, 75, 50, 50, 50, 75, 50, 50, 50, 75};
                break;
            case WARNING:
                vibePattern = new long[]{300, 100, 50, 100, 300, 100, 50, 100, 300, 100, 50, 100, 300, 100, 50, 100, 300, 100, 50, 100, 300, 100, 50, 100, 300, 100, 50, 100, 300, 100, 50, 100, 300};
                break;
            default:
                vibePattern = new long[]{};
        }

        mNotifyBuilder = new NotificationCompat.Builder(context)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .extend(wearableExtender)
                .setPriority(Notification.PRIORITY_HIGH)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(R.drawable.ic_launcher);
        if (!isSnoozed() && (severity.ordinal() > AlarmSeverity.NONE.ordinal())) {
            Log.d("alerting", "not vibrating due to snooze");
            Intent snoozeIntent = new Intent("org.nightscout.scout.SNOOZE");
            PendingIntent pendingSnooze =
                    PendingIntent.getBroadcast(context, 0, snoozeIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
            mNotifyBuilder.addAction(R.drawable.ic_alarm_black_24dp, "Snooze", pendingSnooze)
                    .setVibrate(vibePattern);
        }
        if (preferences.getContactPhone().isPresent()) {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + preferences.getContactPhone().get()));
            PendingIntent pendingCall =
                    PendingIntent.getActivity(context, 0, callIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
            Intent msgIntent = new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", preferences.getContactPhone().get(), null));
            PendingIntent pendingMsg =
                    PendingIntent.getActivity(context, 0, msgIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            mNotifyBuilder.addAction(R.drawable.ic_call_black_24dp, "Call", pendingCall)
                    .addAction(R.drawable.ic_message_black_24dp, "Msg", pendingMsg);
        }

        mNotificationManager.notify(notifyId, mNotifyBuilder.build());
    }

    private void playAlert(int alert) {
        if (isSnoozed()) {
            Log.d("alert", "Not playing alarm due to snooze");
            return;
        }
        Log.d("playAlert", "playing alert");
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer = MediaPlayer.create(context, alert);
            mediaPlayer.setLooping(true);
            mediaPlayer.start();
        }
        if (BuildConfig.DEBUG) {
            Log.d("playAlert", "Is playing " + mediaPlayer.isPlaying());
        }
    }

    private void stopAlert() {
        if (BuildConfig.DEBUG) {
            Log.d("stopAlert", "Is playing " + mediaPlayer.isPlaying());
        }
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }

    public void alarmSnooze(long durationMs) {
        String key = "snooze_" + previousAlarmResults.severity.name();
        if (BuildConfig.DEBUG) {
            Log.d("snooze", "Setting snooze until: " + new DateTime().getMillis() + durationMs);
        }
        sharedPreferences.edit().putLong(key, new DateTime().getMillis() + durationMs).apply();
        showNotification(previousAlarmResults.severity, previousAlarmResults.title, previousAlarmResults.message);
        stopAlert();
    }

    public boolean isSnoozed() {
        Long currentTs = new DateTime().getMillis();
        Long snoozeTime = sharedPreferences.getLong("snooze_" + previousAlarmResults.severity.name(), 0);
        if (BuildConfig.DEBUG) {
            Log.d("snooze", "Snoozed until: " + snoozeTime);
            Log.d("snooze", "Current time: " + currentTs);
        }
        return snoozeTime > currentTs;
    }

}

package org.nightscout.lasso;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.nightscout.core.dexcom.TrendArrow;
import com.nightscout.core.dexcom.records.EGVRecord;
import com.nightscout.core.model.G4Noise;
import com.nightscout.core.model.GlucoseUnit;
import com.nightscout.core.utils.GlucoseReading;

import net.tribe7.common.base.Optional;

import org.joda.time.DateTime;
import org.joda.time.Minutes;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.nightscout.lasso.alarm.Alarm;
import org.nightscout.lasso.alarm.AlarmResults;
import org.nightscout.lasso.alarm.AlarmSeverity;
import org.nightscout.lasso.alarm.AlarmStrategy;
import org.nightscout.lasso.alarm.SimpleAlarm;
import org.nightscout.lasso.alarm.ar2.Ar2;
import org.nightscout.lasso.test.RobolectricTestBase;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowAudioManager;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.ShadowNotificationManager;
import org.robolectric.util.ActivityController;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.robolectric.Shadows.shadowOf;

public class AlarmTest extends RobolectricTestBase {
    ActivityController<MainActivity> activityController;
    SharedPreferences preferences;
    Activity activity;
    AlarmResults alarmResults;
    private ShadowNotificationManager shadowNotificationManager;
    private ShadowMediaPlayer shadowMediaPlayer;
    private ShadowAudioManager shadowAudioManager;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(MainActivity.class).create().start().get();
        shadowNotificationManager = shadowOf((NotificationManager) RuntimeEnvironment.application
                .getSystemService(Context.NOTIFICATION_SERVICE));
        shadowMediaPlayer = new ShadowMediaPlayer();
        shadowAudioManager = shadowOf((AudioManager) RuntimeEnvironment.application.getSystemService(Context.AUDIO_SERVICE));
        //(AudioManager) RuntimeEnvironment.application.getSystemService(Context.AUDIO_SERVICE);
        ShadowLog.stream = System.out;
        preferences = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
        alarmResults = new AlarmResults();
    }

    private void setSimpleAlarm() {
        preferences.edit().putString(getContext().getString(R.string.alarm_model), "0").apply();
    }

    private void setAr2Alarm() {
        preferences.edit().putString(getContext().getString(R.string.alarm_model), "1").apply();
    }

    private List<EGVRecord> normalEgvList() {
        List<EGVRecord> egvRecordList = new ArrayList<>();
        EGVRecord egvRecord = new EGVRecord(100, TrendArrow.FLAT, new DateTime().minus(Minutes.minutes(5)), new DateTime().minus(Minutes.minutes(5)), G4Noise.CLEAN, new DateTime().minus(Minutes.minutes(5)));
        egvRecordList.add(egvRecord);
        egvRecord = new EGVRecord(100, TrendArrow.FLAT, new DateTime(), new DateTime(), G4Noise.CLEAN, new DateTime());
        egvRecordList.add(egvRecord);
        return egvRecordList;
    }

    private List<EGVRecord> urgentHighEgvList() {
        List<EGVRecord> egvRecordList = new ArrayList<>();
        EGVRecord egvRecord = new EGVRecord(250, TrendArrow.UP_45, new DateTime().minus(Minutes.minutes(5)), new DateTime().minus(Minutes.minutes(5)), G4Noise.CLEAN, new DateTime().minus(Minutes.minutes(5)));
        egvRecordList.add(egvRecord);
        egvRecord = new EGVRecord(265, TrendArrow.FLAT, new DateTime(), new DateTime(), G4Noise.CLEAN, new DateTime());
        egvRecordList.add(egvRecord);
        return egvRecordList;
    }

    private List<EGVRecord> urgentLowEgvList() {
        List<EGVRecord> egvRecordList = new ArrayList<>();
        EGVRecord egvRecord = new EGVRecord(55, TrendArrow.DOWN_45, new DateTime().minus(Minutes.minutes(5)), new DateTime().minus(Minutes.minutes(5)), G4Noise.CLEAN, new DateTime().minus(Minutes.minutes(5)));
        egvRecordList.add(egvRecord);
        egvRecord = new EGVRecord(40, TrendArrow.FLAT, new DateTime(), new DateTime(), G4Noise.CLEAN, new DateTime());
        egvRecordList.add(egvRecord);
        return egvRecordList;
    }

    private List<EGVRecord> warnHighEgvList() {
        List<EGVRecord> egvRecordList = new ArrayList<>();
        EGVRecord egvRecord = new EGVRecord(190, TrendArrow.UP_45, new DateTime().minus(Minutes.minutes(5)), new DateTime().minus(Minutes.minutes(5)), G4Noise.CLEAN, new DateTime().minus(Minutes.minutes(5)));
        egvRecordList.add(egvRecord);
        egvRecord = new EGVRecord(195, TrendArrow.FLAT, new DateTime(), new DateTime(), G4Noise.CLEAN, new DateTime());
        egvRecordList.add(egvRecord);
        return egvRecordList;
    }

    private List<EGVRecord> warnLowEgvList() {
        List<EGVRecord> egvRecordList = new ArrayList<>();
        EGVRecord egvRecord = new EGVRecord(70, TrendArrow.DOWN_45, new DateTime().minus(Minutes.minutes(5)), new DateTime().minus(Minutes.minutes(5)), G4Noise.CLEAN, new DateTime().minus(Minutes.minutes(5)));
        egvRecordList.add(egvRecord);
        egvRecord = new EGVRecord(68, TrendArrow.FLAT, new DateTime(), new DateTime(), G4Noise.CLEAN, new DateTime());
        egvRecordList.add(egvRecord);
        return egvRecordList;
    }

    @Test
    public void testAlarmResultSeverityTakesHighestServerity() {
        alarmResults = new AlarmResults();
        alarmResults.setSeverityAtHighest(AlarmSeverity.NONE);
        alarmResults.setSeverityAtHighest(AlarmSeverity.URGENT);
        assertThat(alarmResults.getSeverity(), is(AlarmSeverity.URGENT));
    }

    @Test
    public void testAlarmResultAppendsMessage() {
        String expectedResults = "Test1\nTest2";
        alarmResults = new AlarmResults();
        alarmResults.appendMessage("Test1");
        alarmResults.appendMessage("Test2");
        assertThat(alarmResults.getMessage(), is(expectedResults));
    }

    // FIXME useless test
    @Test
    public void testAlarmResultMergeAlarmResultMergesProperly() {
        AlarmResults expectedResults = new AlarmResults();
        expectedResults.setSeverityAtHighest(AlarmSeverity.URGENT);
        expectedResults.appendMessage("Test1");
        expectedResults.appendMessage("Test2");
        expectedResults.appendMessage("Test3");
        expectedResults.appendMessage("Test4");
        alarmResults = new AlarmResults();
        alarmResults.setSeverityAtHighest(AlarmSeverity.NONE);
        alarmResults.appendMessage("Test1");
        alarmResults.appendMessage("Test2");
        AlarmResults alarmResults2 = new AlarmResults();
        alarmResults2.setSeverityAtHighest(AlarmSeverity.URGENT);
        alarmResults2.appendMessage("Test3");
        alarmResults2.appendMessage("Test4");
        alarmResults.mergeAlarmResults(alarmResults2);
        assertThat(alarmResults, is(expectedResults));
    }

    @Test
    public void testBatteryAbsentDoesNotAlarm() {
        Optional<Integer> battery = Optional.absent();
        Alarm alarm = new Alarm(getContext());
        alarmResults = alarm.analyzeBattery(battery);
        AlarmResults emptyResults = new AlarmResults();
        assertThat(alarmResults, is(emptyResults));
    }

    @Test
    public void testNormalBatteryDoesNotAlarm() {
        Optional<Integer> battery = Optional.of(95);
        Alarm alarm = new Alarm(getContext());
        alarmResults = alarm.analyzeBattery(battery);
        AlarmResults emptyResults = new AlarmResults();
        assertThat(alarmResults, is(emptyResults));
    }

    @Test
    public void testWarnBatteryAlarms() {
        Optional<Integer> battery = Optional.of(11);
        Alarm alarm = new Alarm(getContext());
        alarmResults = alarm.analyzeBattery(battery);
        assertThat(alarmResults.getSeverity(), is(AlarmSeverity.WARNING));
    }

    @Test
    public void testUrgentBatteryAlarms() {
        Optional<Integer> battery = Optional.of(5);
        Alarm alarm = new Alarm(getContext());
        alarmResults = alarm.analyzeBattery(battery);
        assertThat(alarmResults.getSeverity(), is(AlarmSeverity.URGENT));
    }

    private List<EGVRecord> generateStaleEgvRecordList(int minutes) {
        DateTime staleTime = new DateTime().minus(Minutes.minutes(minutes));
        List<EGVRecord> egvRecords = new ArrayList<>();
        egvRecords.add(new EGVRecord(100, TrendArrow.FLAT, staleTime, staleTime, G4Noise.CLEAN, staleTime));
        return egvRecords;
    }

    @Test
    public void testStaleAlarmDisabledWithStaleDataDoesNotAlarm() {
        preferences.edit().putBoolean(getContext().getString(R.string.stale_alarm_enabled), false).apply();
        Alarm alarm = new Alarm(getContext());
        List<EGVRecord> egvRecords = generateStaleEgvRecordList(16);
        alarmResults = alarm.analyzeTime(egvRecords, GlucoseUnit.MGDL, new DateTime().minus(Minutes.minutes(16)));
        AlarmResults emptyResults = new AlarmResults();
        assertThat(alarmResults, is(emptyResults));
    }

    @Test
    public void testStaleAlarmWithWarnStaleDataAlarms() {
        preferences.edit().putBoolean(getContext().getString(R.string.stale_alarm_enabled), true).apply();
        Alarm alarm = new Alarm(getContext());
        List<EGVRecord> egvRecords = generateStaleEgvRecordList(16);
        alarmResults = alarm.analyzeTime(egvRecords, GlucoseUnit.MGDL, new DateTime());
        assertThat(alarmResults.getSeverity(), is(AlarmSeverity.WARNING));
    }

    @Test
    public void testStaleAlarmWithUrgentStaleDataAlarms() {
        preferences.edit().putBoolean(getContext().getString(R.string.stale_alarm_enabled), true).apply();
        Alarm alarm = new Alarm(getContext());
        List<EGVRecord> egvRecords = generateStaleEgvRecordList(45);
        alarmResults = alarm.analyzeTime(egvRecords, GlucoseUnit.MGDL, new DateTime());
        assertThat(alarmResults.getSeverity(), is(AlarmSeverity.URGENT));
    }

    @Test
    public void testOutOfRangeSetsAlarmResultsMessage() {
        preferences.edit().putBoolean(getContext().getString(R.string.stale_alarm_enabled), true).apply();
        Alarm alarm = new Alarm(getContext());
        String expectedMessage = getContext().getString(R.string.alarm_out_of_range_message, 45);
        List<EGVRecord> egvRecords = generateStaleEgvRecordList(45);
        alarmResults = alarm.analyzeTime(egvRecords, GlucoseUnit.MGDL, new DateTime());
        assertThat(alarmResults.getMessage(), containsString(expectedMessage));
    }

    @Test
    public void testSimpleAlarmStrategyNoAlarm() {
        AlarmStrategy strategy = new SimpleAlarm(getContext());
        List<EGVRecord> egvRecords = normalEgvList();
        alarmResults = strategy.analyze(egvRecords, GlucoseUnit.MGDL);
        AlarmResults expectedResults = new AlarmResults();
        expectedResults.setSeverityAtHighest(AlarmSeverity.NONE);
        expectedResults.appendMessage(getContext().getString(R.string.alarm_notification_standard_body,
                100, TrendArrow.FLAT.symbol()));
        assertThat(alarmResults, is(expectedResults));
    }

    @Test
    public void testSimpleAlarmStrategyLowWarnAlarm() {
        AlarmStrategy strategy = new SimpleAlarm(getContext());
        List<EGVRecord> egvRecords = warnLowEgvList();
        alarmResults = strategy.analyze(egvRecords, GlucoseUnit.MGDL);
        AlarmResults expectedResults = new AlarmResults();
        expectedResults.setSeverityAtHighest(AlarmSeverity.WARNING);
        expectedResults.setTitle(getContext().getString(R.string.alarm_notification_warning_title, getContext().getString(R.string.app_name)));
        expectedResults.appendMessage(getContext().getString(R.string.alarm_notification_standard_body,
                68, TrendArrow.FLAT.symbol()));
        assertThat(alarmResults, is(expectedResults));
    }

    @Test
    public void testSimpleAlarmStrategyLowUrgentAlarm() {
        AlarmStrategy strategy = new SimpleAlarm(getContext());
        List<EGVRecord> egvRecords = urgentLowEgvList();
        alarmResults = strategy.analyze(egvRecords, GlucoseUnit.MGDL);
        AlarmResults expectedResults = new AlarmResults();
        expectedResults.setSeverityAtHighest(AlarmSeverity.URGENT);
        expectedResults.setTitle(getContext().getString(R.string.alarm_notification_urgent_title, getContext().getString(R.string.app_name)));
        expectedResults.appendMessage(getContext().getString(R.string.alarm_notification_standard_body,
                40, TrendArrow.FLAT.symbol()));
        assertThat(alarmResults, is(expectedResults));
    }

    @Test
    public void testSimpleAlarmStrategyHighWarnAlarm() {
        AlarmStrategy strategy = new SimpleAlarm(getContext());
        List<EGVRecord> egvRecords = warnHighEgvList();
        alarmResults = strategy.analyze(egvRecords, GlucoseUnit.MGDL);
        AlarmResults expectedResults = new AlarmResults();
        expectedResults.setSeverityAtHighest(AlarmSeverity.WARNING);
        expectedResults.setTitle(getContext().getString(R.string.alarm_notification_warning_title, getContext().getString(R.string.app_name)));
        expectedResults.appendMessage(getContext().getString(R.string.alarm_notification_standard_body,
                195, TrendArrow.FLAT.symbol()));
        assertThat(alarmResults, is(expectedResults));
    }

    @Test
    public void testSimpleHighUrgentAlarm() {
        AlarmStrategy strategy = new SimpleAlarm(getContext());
        List<EGVRecord> egvRecords = urgentHighEgvList();
        alarmResults = strategy.analyze(egvRecords, GlucoseUnit.MGDL);
        AlarmResults expectedResults = new AlarmResults();
        expectedResults.setSeverityAtHighest(AlarmSeverity.URGENT);
        expectedResults.setTitle(getContext().getString(R.string.alarm_notification_urgent_title, getContext().getString(R.string.app_name)));
        expectedResults.appendMessage(getContext().getString(R.string.alarm_notification_standard_body,
                265, TrendArrow.FLAT.symbol()));
        assertThat(alarmResults, is(expectedResults));
    }

    @Test
    public void testAr2AlarmStrategyNoAlarm() {
        AlarmStrategy strategy = new Ar2(getContext());
        List<EGVRecord> egvRecords = normalEgvList();
        alarmResults = strategy.analyze(egvRecords, GlucoseUnit.MGDL);
        AlarmResults expectedResults = new AlarmResults();
        expectedResults.setSeverityAtHighest(AlarmSeverity.NONE);
        GlucoseReading glucoseReading = new GlucoseReading(100, GlucoseUnit.MGDL);
        expectedResults.setTitle(getContext().getString(R.string.alarm_notification_normal_title,
                getContext().getString(R.string.app_name)));
        expectedResults.appendMessage(getContext().getString(R.string.alarm_notification_standard_body,
                100, TrendArrow.FLAT.symbol()));

        assertThat(alarmResults, is(expectedResults));
    }

    @Test
    public void testAr2AlarmStrategyLowWarnAlarm() {
        AlarmStrategy strategy = new Ar2(getContext());
        List<EGVRecord> egvRecords = warnLowEgvList();
        alarmResults = strategy.analyze(egvRecords, GlucoseUnit.MGDL);
        AlarmResults expectedResults = new AlarmResults();
        expectedResults.setSeverityAtHighest(AlarmSeverity.WARNING);
        expectedResults.setTitle(getContext().getString(R.string.alarm_notification_warning_title, getContext().getString(R.string.app_name)));
        expectedResults.appendMessage(getContext().getString(R.string.alarm_notification_standard_body,
                68, TrendArrow.FLAT.symbol()));
        assertThat(alarmResults, is(expectedResults));
    }

    @Test
    public void testAr2AlarmStrategyLowUrgentAlarm() {
        AlarmStrategy strategy = new Ar2(getContext());
        List<EGVRecord> egvRecords = urgentLowEgvList();
        alarmResults = strategy.analyze(egvRecords, GlucoseUnit.MGDL);
        AlarmResults expectedResults = new AlarmResults();
        expectedResults.setSeverityAtHighest(AlarmSeverity.URGENT);
        expectedResults.setTitle(getContext().getString(R.string.alarm_notification_urgent_title, getContext().getString(R.string.app_name)));
        expectedResults.appendMessage(getContext().getString(R.string.alarm_notification_standard_body,
                40, TrendArrow.FLAT.symbol()));
        assertThat(alarmResults, is(expectedResults));
    }

    @Test
    public void testAr2AlarmStrategyHighWarnAlarm() {
        AlarmStrategy strategy = new Ar2(getContext());
        List<EGVRecord> egvRecords = warnHighEgvList();
        alarmResults = strategy.analyze(egvRecords, GlucoseUnit.MGDL);
        AlarmResults expectedResults = new AlarmResults();
        expectedResults.setSeverityAtHighest(AlarmSeverity.WARNING);
        expectedResults.setTitle(getContext().getString(R.string.alarm_notification_warning_title, getContext().getString(R.string.app_name)));
        expectedResults.appendMessage(getContext().getString(R.string.alarm_notification_standard_body,
                195, TrendArrow.FLAT.symbol()));
        assertThat(alarmResults, is(expectedResults));
    }

    @Test
    public void testAr2HighUrgentAlarm() {
        AlarmStrategy strategy = new Ar2(getContext());
        List<EGVRecord> egvRecords = urgentHighEgvList();
        alarmResults = strategy.analyze(egvRecords, GlucoseUnit.MGDL);
        AlarmResults expectedResults = new AlarmResults();
        expectedResults.setSeverityAtHighest(AlarmSeverity.URGENT);
        expectedResults.setTitle(getContext().getString(R.string.alarm_notification_urgent_title, getContext().getString(R.string.app_name)));
        expectedResults.appendMessage(getContext().getString(R.string.alarm_notification_standard_body,
                265, TrendArrow.FLAT.symbol()));
        assertThat(alarmResults, is(expectedResults));
    }

    @Test
    public void testIsSnoozed() {
        Alarm alarm = new Alarm(getContext());
        preferences.edit().putLong("snooze_" + AlarmSeverity.NONE.name(), new DateTime().plus(Minutes.minutes(5)).getMillis()).apply();
        assertThat(alarm.isSnoozed(), is(true));
    }

    @Test
    public void testSnooze() {
        Alarm alarm = new Alarm(getContext());
        // Test with the AR strategy
        preferences.edit().putString(getContext().getString(R.string.alarm_model), "3").apply();
        alarm.alarmSnooze(Minutes.minutes(5).toStandardDuration());
//        preferences.edit().putLong("snooze_" + AlarmSeverity.NONE.name(), new DateTime().plus(Minutes.minutes(5)).getMillis()).apply();
        assertThat(preferences.getLong("snooze_" + AlarmSeverity.NONE.name(), 0) > new DateTime().getMillis(), is(true));
    }

    @Test
    public void testExpiredSnooze() {
        preferences.edit().putLong("snooze_" + AlarmSeverity.NONE.name(), new DateTime().minus(Minutes.minutes(10)).getMillis()).apply();
        Alarm alarm = new Alarm(getContext());
        assertThat(alarm.isSnoozed(), is(false));
    }

    @Test
    public void testAlarmWarningCreatesNotificationWithNormalNotificationsDisabled() {
        List<EGVRecord> egvRecords = warnHighEgvList();
        preferences.edit().putString(getContext().getString(R.string.alarm_model), "3").apply();
        preferences.edit().putBoolean(getContext().getString(R.string.show_all_notifications_enabled), false).apply();
        preferences.edit().putString(getContext().getString(R.string.contact_phone), "5558675309").apply();
        Alarm alarm = new Alarm(getContext());
        alarm.analyze(egvRecords, GlucoseUnit.MGDL, Optional.of(100), new DateTime());
        // FIXME - have to disable the ringer due to Robolectric NPE with mediaplayer
        shadowAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        Log.d("MediaState", "State: " + shadowMediaPlayer.getState().name());
        alarm.alarm();
        assertThat(shadowNotificationManager.size(), is(1));
    }

    @Test
    public void testAlarmClearsNotificationWhenTransitioningFromNotNoneToNone() {
        List<EGVRecord> egvRecords = warnHighEgvList();
        Alarm alarm = new Alarm(getContext());
        alarm.analyze(egvRecords, GlucoseUnit.MGDL, Optional.of(100), new DateTime());
        preferences.edit().putBoolean(getContext().getString(R.string.show_all_notifications_enabled), false).apply();
        preferences.edit().putString(getContext().getString(R.string.contact_phone), "5558675309").apply();
        // FIXME - have to disable the ringer due to Robolectric NPE with mediaplayer
        shadowAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        alarm.alarm();
        egvRecords = normalEgvList();
        alarm.analyze(egvRecords, GlucoseUnit.MGDL, Optional.of(100), new DateTime());
        alarm.alarm();
        assertThat(shadowNotificationManager.size(), is(0));
    }

    public void testNoopStrategyUrgentHighDoesNotAlert() {
        alarmResults = new AlarmResults();
        preferences.edit().putString(getContext().getString(R.string.alarm_model), "1").apply();
        shadowAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        Alarm alarm = new Alarm(getContext());
        alarm.analyze(urgentHighEgvList(), GlucoseUnit.MGDL, Optional.of(100), new DateTime());
        assertThat(alarm.getAlarmResults(), is(alarmResults));
    }

    public void testNoopStrategyWarningHighDoesNotAlert() {
        alarmResults = new AlarmResults();
        preferences.edit().putString(getContext().getString(R.string.alarm_model), "1").apply();
        shadowAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        Alarm alarm = new Alarm(getContext());
        alarm.analyze(warnHighEgvList(), GlucoseUnit.MGDL, Optional.of(100), new DateTime());
        assertThat(alarm.getAlarmResults(), is(alarmResults));
    }

    public void testNoopStrategyUrgentLowDoesNotAlert() {
        alarmResults = new AlarmResults();
        preferences.edit().putString(getContext().getString(R.string.alarm_model), "1").apply();
        shadowAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        Alarm alarm = new Alarm(getContext());
        alarm.analyze(urgentLowEgvList(), GlucoseUnit.MGDL, Optional.of(100), new DateTime());
        assertThat(alarm.getAlarmResults(), is(alarmResults));
    }

    public void testNoopStrategyWarningLowDoesNotAlert() {
        alarmResults = new AlarmResults();
        preferences.edit().putString(getContext().getString(R.string.alarm_model), "1").apply();
        shadowAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        Alarm alarm = new Alarm(getContext());
        alarm.analyze(warnLowEgvList(), GlucoseUnit.MGDL, Optional.of(100), new DateTime());
        assertThat(alarm.getAlarmResults(), is(alarmResults));
    }

    public void testNoopStrategyLowBatteryDoesNotAlert() {
        alarmResults = new AlarmResults();
        preferences.edit().putString(getContext().getString(R.string.alarm_model), "1").apply();
        shadowAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        Alarm alarm = new Alarm(getContext());
        alarm.analyze(normalEgvList(), GlucoseUnit.MGDL, Optional.of(11), new DateTime());
        assertThat(alarm.getAlarmResults(), is(alarmResults));
    }

    public void testNoopStrategyUrgentLowBatteryDoesNotAlert() {
        setSilentMode();
        alarmResults = new AlarmResults();
        preferences.edit().putString(getContext().getString(R.string.alarm_model), "1").apply();
        Alarm alarm = new Alarm(getContext());
        alarm.analyze(normalEgvList(), GlucoseUnit.MGDL, Optional.of(5), new DateTime());
        assertThat(alarm.getAlarmResults(), is(alarmResults));
    }

    public void testNoopStrategyStaleDataDoesNotAlert() {
        setSilentMode();
        alarmResults = new AlarmResults();
        preferences.edit().putString(getContext().getString(R.string.alarm_model), "1").apply();
        Alarm alarm = new Alarm(getContext());
        alarm.analyze(generateStaleEgvRecordList(16), GlucoseUnit.MGDL, Optional.of(100), new DateTime());
        assertThat(alarm.getAlarmResults(), is(alarmResults));
    }

    public void testNoopStrategyUrgentStaleDoesNotAlert() {
        setSilentMode();
        alarmResults = new AlarmResults();
        preferences.edit().putString(getContext().getString(R.string.alarm_model), "1").apply();
        Alarm alarm = new Alarm(getContext());
        alarm.analyze(generateStaleEgvRecordList(46), GlucoseUnit.MGDL, Optional.of(100), new DateTime());
        assertThat(alarm.getAlarmResults(), is(alarmResults));
    }

    @Test
    public void testGenerateAlarm() throws Exception {
        setSilentMode();
        JSONObject json = new JSONObject("{\"level\":2, \"title\":\"Test\", \"message\":\"Test\"}");
        Alarm alarm = new Alarm(getContext());
        alarm.generateAlarm(json);
        AlarmResults expectedResults = new AlarmResults("Test", AlarmSeverity.URGENT, "Test");
        assertThat(alarm.getAlarmResults(), is(expectedResults));
    }

    private void setSilentMode() {
        shadowAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
    }


    // TODO - test that phone vibrates
    // TODO - test that media plays
    // TODO - test that media snoozes properly
    // TODO - test that media stops playing when transitioning from ! None alarm to None
    // TODO - verify priorities react as expected


    public Context getContext() {
        return getShadowApplication().getApplicationContext();
    }

    public ShadowApplication getShadowApplication() {
        return ShadowApplication.getInstance();
    }
}

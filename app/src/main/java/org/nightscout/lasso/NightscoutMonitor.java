package org.nightscout.lasso;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.RemoteInput;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.SmsManager;
import android.util.Log;

import com.nightscout.core.dexcom.records.CalRecord;
import com.nightscout.core.dexcom.records.EGVRecord;
import com.nightscout.core.dexcom.records.InsertionRecord;
import com.nightscout.core.dexcom.records.MeterRecord;
import com.nightscout.core.dexcom.records.SensorRecord;
import com.nightscout.core.events.EventSeverity;
import com.nightscout.core.events.EventType;
import com.nightscout.core.model.CalibrationEntry;
import com.nightscout.core.model.Download;
import com.nightscout.core.model.G4Noise;
import com.nightscout.core.model.GlucoseUnit;
import com.nightscout.core.model.InsertionEntry;
import com.nightscout.core.model.MeterEntry;
import com.nightscout.core.model.SensorEntry;
import com.nightscout.core.model.SensorGlucoseValueEntry;
import com.nightscout.core.mqtt.MqttEventMgr;
import com.nightscout.core.mqtt.MqttMgrObserver;
import com.nightscout.core.mqtt.MqttPinger;
import com.nightscout.core.mqtt.MqttTimer;
import com.nightscout.core.utils.GlucoseReading;
import com.nightscout.core.utils.IsigReading;
import com.nightscout.core.utils.RestUriUtils;
import com.squareup.wire.Wire;

import net.tribe7.common.base.Optional;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Minutes;
import org.joda.time.Seconds;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;
import org.nightscout.lasso.alarm.Alarm;
import org.nightscout.lasso.alarm.AlarmResults;
import org.nightscout.lasso.alarm.AlarmSeverity;
import org.nightscout.lasso.events.AndroidEventReporter;
import org.nightscout.lasso.model.CalibrationDbEntry;
import org.nightscout.lasso.model.InsertionDbEntry;
import org.nightscout.lasso.model.MeterDbEntry;
import org.nightscout.lasso.model.SensorDbEntry;
import org.nightscout.lasso.model.SgvDbEntry;
import org.nightscout.lasso.mqtt.AndroidMqttPinger;
import org.nightscout.lasso.mqtt.AndroidMqttTimer;
import org.nightscout.lasso.preferences.AndroidPreferences;
import org.nightscout.lasso.wearables.Pebble;
import org.nightscout.lasso.wearables.WatchMaker;

import java.io.IOException;
import java.net.URI;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

public class NightscoutMonitor extends Service implements MqttMgrObserver {
    public static final String RECEIVER_STATE_INTENT = "org.nightscout.scout.RECEIVER_STATE";
    public static final String SNOOZE_INTENT = "org.nightscout.scout.SNOOZE";
    public static final String MQTT_QUERY_STATUS_INTENT = "org.nightscout.scout.MQTT_QUERY_STATUS";
    public static final String MQTT_RESPONSE_STATUS_INTENT = "org.nightscout.scout.MQTT_RESPONSE_STATUS";
    public static final String NEW_READING_ACTION = "org.nightscout.NEW_READING";
    public static final String MISSED_READING_ACTION = "org.nightscout.MISSED_READING";
    public static final String WEAR_MSG_RELAY = "org.nightscout.WEAR_RELAY";
    public static final String MQTT_STATUS_EXTRA_FIELD = "MqttConnected";
    public static final Duration MISSED_DATA_WARNING_AGE = Seconds.seconds(20).toStandardDuration().plus(Minutes.minutes(15).toStandardDuration());
    public static final Duration MISSED_DATA_URGENT_AGE = Seconds.seconds(20).toStandardDuration().plus(Minutes.minutes(30).toStandardDuration());
    public static final Duration ALARM_SNOOZE_DEFAULT_DURATION = Minutes.minutes(30).toStandardDuration().plus(Seconds.seconds(10).toStandardDuration());
    public static final Duration ANALYSIS_DURATION = Minutes.minutes(90).toStandardDuration();
    private static final String MQTT_PROTO_DOWNLOAD_TOPIC = "/downloads/%s/protobuf";
    private static final String MQTT_JSON_NOTIFICATIONS_TOPIC = "/notifications/json";
    private static final String MISSED_DOWNLOAD_SEVERITY_EXTRA = "severity";
    private static final String MISSED_DOWNLOAD_AGE_EXTRA = "dataage";
    private String TAG = this.getClass().getSimpleName();
    private AndroidEventReporter reporter;
    private AndroidPreferences preferences;
    private MqttEventMgr mqttManager;
    private Alarm alarm;
    private Optional<Integer> uploaderBattery = Optional.absent();
    private Optional<Integer> receiverBattery = Optional.absent();
    private int messageCount = 0;
    private AlarmManager alarmManager;
    private PendingIntent pendingMissedReading;
    private Optional<EGVRecord> lastEgv = Optional.absent();
    private Optional<IsigReading> lastIsig = Optional.absent();
    private Optional<String> lastDeltaString = Optional.absent();
    private Optional<G4Noise> lastNoise = Optional.absent();
    private SharedPreferences prefs;
    private Pebble pebble;

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(SNOOZE_INTENT)) {
                if (preferences.getAlarmStrategy() == 0) {
                    for (String url : preferences.getRestApiBaseUris()) {
                        ackAlarm(url);
                    }
                } else {
                    Log.w(TAG, "Snoozing per request");
                    alarm.alarmSnooze(ALARM_SNOOZE_DEFAULT_DURATION);
                    // Everything in this block below this line manages the missed data alarm
                    // Prevent the snooze from missing the missed data alarm
                    Duration when = MISSED_DATA_WARNING_AGE;
                    if (alarm.getAlarmResults().getSeverity() == AlarmSeverity.URGENT) {
                        when = MISSED_DATA_URGENT_AGE.minus(MISSED_DATA_WARNING_AGE);
                    }
                    if (ALARM_SNOOZE_DEFAULT_DURATION.isLongerThan(when)) {
                        when = ALARM_SNOOZE_DEFAULT_DURATION.plus(Seconds.seconds(10).toStandardDuration());
                    }
                    // At minimum we want the severity to be warning.
                    AlarmSeverity mySeverity = (alarm.getAlarmResults().getSeverity().ordinal() < AlarmSeverity.WARNING.ordinal()) ? AlarmSeverity.WARNING : alarm.getAlarmResults().getSeverity();
                    setMissedReadingAlarm(when, mySeverity);
                }
            } else if (intent.getAction().equals(MQTT_QUERY_STATUS_INTENT)) {
                Log.d("QUERY", "Received query request");
                sendMqttStatus();
            } else if (intent.getAction().equals(MISSED_READING_ACTION)) {
                AlarmSeverity severity = AlarmSeverity.values()[intent.getExtras().getInt(MISSED_DOWNLOAD_SEVERITY_EXTRA)];
                Log.d(TAG, "Received " + severity.name() + "(" + intent.getExtras().getInt(MISSED_DOWNLOAD_SEVERITY_EXTRA) + ") severity missed alarm");
                Duration age = Duration.millis(intent.getExtras().getLong(MISSED_DOWNLOAD_AGE_EXTRA));
                AlarmResults alarmResults = new AlarmResults(getString(R.string.no_data_title, severity.name()),
                        severity,
                        getString(R.string.no_data_message, age.getStandardMinutes()));
                alarmResults.mergeAlarmResults(alarm.getAlarmResults());
                if (severity == AlarmSeverity.WARNING) {
                    Duration urgentFromNow = MISSED_DATA_URGENT_AGE.minus(MISSED_DATA_WARNING_AGE);
                    setMissedReadingAlarm(urgentFromNow, AlarmSeverity.URGENT);
                }
                alarm.alarm(alarmResults);
            } else if (intent.getAction().equals(WEAR_MSG_RELAY)) {
                Log.d("VoiceResults", "Got this far");
                Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
                if (remoteInput != null) {
                    Log.d("VoiceResults", "This is so cool: " + String.valueOf(remoteInput.getCharSequence(Alarm.EXTRA_VOICE_REPLY)));
                    if (preferences.getContactPhone().isPresent()) {
                        SmsManager smsManager = SmsManager.getDefault();
                        smsManager.sendTextMessage(preferences.getContactPhone().get(), null, String.valueOf(remoteInput.getCharSequence(Alarm.EXTRA_VOICE_REPLY)), null, null);
                    }
                }
            }
        }
    };
    private SharedPreferences.OnSharedPreferenceChangeListener spChanged = new
            SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                                      String key) {
                    List<String> mqttKeys = Arrays.asList(getString(R.string.mqtt_endpoint), getString(R.string.mqtt_user), getString(R.string.mqtt_pass));
                    if (key.equals(getApplication().getString(R.string.preferred_units))) {
                        // EGVRecord reading, G4Noise noise, IsigReading isigReading, GlucoseUnit unit, String delta, int uploaderBattery, int receiverBattery, Context context
                        if (lastEgv.isPresent() && lastNoise.isPresent() && lastIsig.isPresent() &&
                                lastDeltaString.isPresent() && uploaderBattery.isPresent() && receiverBattery.isPresent()) {
                            WatchMaker.sendReadings(lastEgv.get(), lastNoise.get(), lastIsig.get(), preferences.getPreferredUnits(), lastDeltaString.get(), uploaderBattery.get(), receiverBattery.get(), getApplicationContext());
                        }
                    } else if (key.equals(getString(R.string.alarm_model))) {
                        Log.d("PrefChange", "Changing alarm model");
                        alarm = new Alarm(getApplicationContext());
                    } else if (mqttKeys.contains(key)) {
                        Log.d("PrefChange", "Reconnecting due to mqtt change");
                        if (mqttManager == null) {
                            setupMqtt();
                        }
                        mqttManager.delayedReconnect(Seconds.seconds(15).toStandardDuration().getMillis());
                    }
                }
            };

    public NightscoutMonitor() {
    }
    // TODO handle mqtt configuration changes

    public static void queryMqttStatusAsync(Context context) {
        Log.d("QUERY", "Querying MQTT status");
        Intent intent = new Intent(MQTT_QUERY_STATUS_INTENT);
        context.sendBroadcast(intent);
//        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "service created");
        alarm = new Alarm(getApplicationContext());
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        preferences = new AndroidPreferences(getApplicationContext());
        reporter = AndroidEventReporter.getReporter(getApplicationContext());
        // FIXME - why am I doing this?
        preferences.setMqttUploadEnabled(true);
        IntentFilter intentFilter = new IntentFilter(SNOOZE_INTENT);
        intentFilter.addAction(MQTT_QUERY_STATUS_INTENT);
        intentFilter.addAction(MISSED_READING_ACTION);
        intentFilter.addAction(WEAR_MSG_RELAY);
        getApplicationContext().registerReceiver(broadcastReceiver, intentFilter);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(spChanged);
        setupMqtt();
        // FIXME - hack to get the app to start up
        if (mqttManager != null) {
            mqttManager.registerObserver(this);
            mqttManager.subscribe(2, String.format(MQTT_PROTO_DOWNLOAD_TOPIC, preferences.getMqttUser()));
            if (preferences.getAlarmStrategy() == 0) {
                Log.d(TAG, "Subscribing to notifications");
                mqttManager.subscribe(0, MQTT_JSON_NOTIFICATIONS_TOPIC);
            } else {
                Log.d(TAG, "Unsubscribing to notifications");
                mqttManager.unSubscribe(MQTT_JSON_NOTIFICATIONS_TOPIC);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "service started");
        ((Lasso) getApplication()).setServiceStarted(true);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void setupMqtt() {
        if (preferences.isMqttEnabled()) {
            mqttManager = setupMqttConnection(preferences.getMqttUser(), preferences.getMqttPass(), preferences.getMqttEndpoint());
            if (mqttManager != null) {
                Log.d(TAG, "Attempt to connect to MQTT");
                mqttManager.setShouldReconnect(true);
                mqttManager.connect();
            } else {
                Log.d(TAG, "MQTT is NULL");
            }
        }
    }

    public MqttEventMgr setupMqttConnection(String user, String pass, String endpoint) {
        if (user.equals("") || pass.equals("") || endpoint.equals("")) {
            reporter.report(EventType.UPLOADER, EventSeverity.ERROR, "Unable to setup MQTT. Please check settings");
            return null;
        }
        try {
            MqttConnectOptions mqttOptions = new MqttConnectOptions();
            mqttOptions.setCleanSession(false);
            mqttOptions.setKeepAliveInterval(150000);
            mqttOptions.setUserName(user);
            mqttOptions.setPassword(pass.toCharArray());
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            MemoryPersistence dataStore = new MemoryPersistence();
            MqttClient client = new MqttClient(endpoint, androidId, dataStore);
            MqttPinger pinger = new AndroidMqttPinger(getApplicationContext(), 0, client, 150000);
            MqttTimer timer = new AndroidMqttTimer(getApplicationContext(), 0);
            return new MqttEventMgr(client, mqttOptions, pinger, timer, reporter);
        } catch (MqttException e) {
            reporter.report(EventType.UPLOADER, EventSeverity.ERROR, "Unable to setup MQTT. Please check settings");
            return null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getApplicationContext().unregisterReceiver(broadcastReceiver);
        mqttManager.setShouldReconnect(false);
        mqttManager.disconnect();
        mqttManager.close();
        pebble.close();
        ((Lasso) getApplication()).setServiceStarted(false);
        Log.d(TAG, "service destroyed");
    }

    /**
     * @param topic   name of the topic that the message came in on
     * @param message this is the message
     */
    @Override
    public void onMessage(String topic, MqttMessage message) {
        if (topic.equals(MQTT_JSON_NOTIFICATIONS_TOPIC)) {
            JSONObject reader = null;
            try {
                reader = new JSONObject(new String(message.getPayload()));
                if (reader.has("clear") && reader.getBoolean("clear")) {
                    alarm.clear();
                }
                if (reader.has("level") && reader.has("title") && reader.has("message")) {
                    alarm.generateAlarm(reader);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (topic.equals(String.format(MQTT_PROTO_DOWNLOAD_TOPIC, preferences.getMqttUser()))) {
//            cancelMissedAlarm();
            messageCount += 1;
            Wire wire = new Wire();
            try {
                Download download = wire.parseFrom(message.getPayload(), Download.class);
                if (download.receiver_state != null) {
                    Intent intent = new Intent(RECEIVER_STATE_INTENT);
                    intent.putExtra("state", download.receiver_state.event.name());
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                }

                Log.d(TAG, "Msg #" + messageCount);

                saveToDb(download);

                List<EGVRecord> egvRecords = SgvDbEntry.getLastEgvRecords(new DateTime().minus(ANALYSIS_DURATION));
                Log.d(TAG, "Analyzing message #" + messageCount);
                DateTime dlTimestamp = ISODateTimeFormat.dateTime().parseDateTime(download.download_timestamp);
                alarm.analyze(egvRecords, preferences.getPreferredUnits(), Optional.fromNullable(download.uploader_battery), dlTimestamp);
                alarm.alarm();


                String delta = "None";
                lastEgv = Optional.absent();
                if (egvRecords.size() > 2) {
                    lastEgv = Optional.of(egvRecords.get(egvRecords.size() - 1));
                    Log.e(TAG, "New reading came in - EGV: " + lastEgv.get().getBgMgdl() + ". Reading taken at: " + lastEgv.get().getWallTime());
                    GlucoseReading glucoseDelta = lastEgv.get().getReading().subtract(egvRecords.get(egvRecords.size() - 2).getReading());
                    DecimalFormat fmt;
                    if (preferences.getPreferredUnits() == GlucoseUnit.MGDL) {
                        fmt = new DecimalFormat("+#,##0;-#");
                    } else {
                        fmt = new DecimalFormat("+#,##0.0;-#");
                    }
                    if (egvRecords.get(egvRecords.size() - 2).getReading().asMgdl() > 38 && egvRecords.get(egvRecords.size() - 2).getReading().asMgdl() > 38) {
                        delta = fmt.format(glucoseDelta.as(preferences.getPreferredUnits()));
                    }
                }

                Optional<CalRecord> lastCal = CalibrationDbEntry.getLastCal();
                Optional<SensorRecord> lastSensor = SensorDbEntry.getLastSensor();
                IsigReading isigReading = new IsigReading();
                if (lastEgv.isPresent()) {
                    if (lastCal.isPresent() && lastSensor.isPresent() && Math.abs(lastEgv.get().getSystemTime().getMillis() - lastSensor.get().getSystemTime().getMillis()) < Seconds.seconds(10).toStandardDuration().getMillis()) {
                        isigReading = new IsigReading(lastSensor.get(), lastCal.get(), egvRecords.get(egvRecords.size() - 1));
                        Log.e("isig", "iSig reading: " + isigReading.asMgdlStr());
                    } else {
                        Log.w("isig", "Problem matching sensor to egv for isig calculation");
                        Log.w("isig", "Last egv: " + lastEgv.get().getSystemTime().getMillis());
                        Log.w("isig", "Last sensor: " + lastSensor.get().getSystemTime().getMillis());
                    }
                    uploaderBattery = Optional.fromNullable(download.uploader_battery);
                    receiverBattery = Optional.fromNullable(download.receiver_battery);
                    lastNoise = Optional.of(egvRecords.get(egvRecords.size() - 1).getNoiseMode());
                    lastIsig = Optional.of(isigReading);
                    lastDeltaString = Optional.of(delta);
                    WatchMaker.sendReadings(egvRecords.get(egvRecords.size() - 1), egvRecords.get(egvRecords.size() - 1).getNoiseMode(), isigReading, preferences.getPreferredUnits(), delta, uploaderBattery.or(-1), Optional.fromNullable(download.receiver_battery).or(-1), getApplicationContext());
                    pebble = new Pebble(getApplicationContext());
                    pebble.sendDownload(egvRecords.get(egvRecords.size() - 1).getReading(), egvRecords.get(egvRecords.size() - 1).getTrend(), egvRecords.get(egvRecords.size() - 1).getWallTime().getMillis(), uploaderBattery.or(0), receiverBattery.or(0), getApplicationContext());
                    // sendDownload(GlucoseReading reading, TrendArrow trend, long recordTime, int uploaderBattery, int receiverBattery, Context cntx) {
                }

                Intent intent = new Intent(NEW_READING_ACTION);
                Log.d(TAG, "Msg #" + messageCount + " - Battery: " + download.uploader_battery);
                intent.putExtra("uploaderBattery", download.uploader_battery);
                Log.d(TAG, "Msg #" + messageCount + " - Receiver: " + download.receiver_battery);

                setMissedReadingAlarm(MISSED_DATA_WARNING_AGE, AlarmSeverity.WARNING);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void setMissedReadingAlarm(Duration when, AlarmSeverity severity) {
        Log.d(TAG, "Canceling missed reading alarm");
        alarmManager.cancel(pendingMissedReading);
        Intent missedReading = new Intent(MISSED_READING_ACTION);
        missedReading.putExtra(MISSED_DOWNLOAD_SEVERITY_EXTRA, severity.ordinal());
        missedReading.putExtra(MISSED_DOWNLOAD_AGE_EXTRA, when.getMillis());
        pendingMissedReading = PendingIntent.getBroadcast(getApplicationContext(), 1, missedReading, PendingIntent.FLAG_UPDATE_CURRENT);
        Log.d(TAG, "Setting missed reading alarm for ~" + when.getStandardMinutes() + " minutes from now with a severity of " + severity.name() + "(" + severity.ordinal() + ")");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + when.getMillis(), pendingMissedReading);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + when.getMillis(), pendingMissedReading);
        }
    }

    private void saveToDb(Download download) {
        long dlTimestampMs = ISODateTimeFormat.dateTime().parseDateTime(download.download_timestamp).getMillis();
        EGVRecord egvRecord = null;
        SensorRecord sensorRecord = null;
        CalRecord calRecord = null;
        InsertionRecord insertionRecord = null;
        MeterRecord meterRecord = null;

        for (SensorGlucoseValueEntry sensorGlucoseValueEntry : download.sgv) {
            egvRecord = new EGVRecord(sensorGlucoseValueEntry, download.receiver_system_time_sec, dlTimestampMs);
            SgvDbEntry sgvEntry = new SgvDbEntry(egvRecord, download.receiver_id, download.transmitter_id);
            Log.d(TAG, "SGV: " + egvRecord.getBgMgdl() + " Trend: " + egvRecord.getTrend().friendlyTrendName());
            sgvEntry.save();
        }

        for (CalibrationEntry calibrationEntry : download.cal) {
            calRecord = new CalRecord(calibrationEntry, download.receiver_system_time_sec, dlTimestampMs);
            CalibrationDbEntry calEntry = new CalibrationDbEntry(calRecord, download.receiver_id, download.transmitter_id);
            calEntry.save();
        }

        for (SensorEntry sensorEntry : download.sensor) {
            sensorRecord = new SensorRecord(sensorEntry, download.receiver_system_time_sec, dlTimestampMs);
            SensorDbEntry sensorDbEntry = new SensorDbEntry(sensorRecord, download.receiver_id, download.transmitter_id);
            sensorDbEntry.save();
        }

        for (InsertionEntry insertionEntry : download.insert) {
            insertionRecord = new InsertionRecord(insertionEntry, download.receiver_system_time_sec, dlTimestampMs);
            InsertionDbEntry insertionDbEntry = new InsertionDbEntry(insertionRecord, download.receiver_id, download.transmitter_id);
            insertionDbEntry.save();
        }

        for (MeterEntry meterEntry : download.meter) {
            meterRecord = new MeterRecord(meterEntry, download.receiver_system_time_sec, dlTimestampMs);
            MeterDbEntry meterDbEntry = new MeterDbEntry(meterRecord, download.receiver_id, download.transmitter_id);
            meterDbEntry.save();
        }
    }

    @Override
    public void onDisconnect() {
        sendMqttStatus();
    }

    @Override
    public void onConnect() {
        sendMqttStatus();
    }

    public void sendMqttStatus() {
        Intent intent = new Intent(MQTT_RESPONSE_STATUS_INTENT);
        intent.putExtra(MQTT_STATUS_EXTRA_FIELD, mqttManager.isConnected());
        Log.d("QUERY", "Responding with mqtt connected: " + mqttManager.isConnected());
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    /**
     * Acknowledges a remote alarm by hitting the Alarm api
     *
     * @param uriStr Base URL for the CRM.
     */
    public void ackAlarm(final String uriStr) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Integer level = alarm.getAlarmResults().getSeverity().ordinal() - 3;
                URI uri = URI.create(uriStr + "/notifications/ack?level=" + level);
                String url = RestUriUtils.removeToken(uri).toString();
                Log.w(TAG, "URL: " + url);
                String secret = RestUriUtils.generateSecret(uri.getUserInfo());
                HttpGet httpGet = new HttpGet(url);
                httpGet.addHeader("Content-Type", "application/json");
                httpGet.addHeader("Accept", "application/json");
                Log.d(TAG, "Secret: " + secret);
                httpGet.setHeader("api-secret", secret);
                HttpClient client = new DefaultHttpClient();
                HttpResponse response = null;
                try {
                    response = client.execute(httpGet);
                    Log.d(TAG, "Response code: " + response.getStatusLine().getStatusCode());
                    Log.d(TAG, "Response: " + response.getEntity().getContent().read());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}

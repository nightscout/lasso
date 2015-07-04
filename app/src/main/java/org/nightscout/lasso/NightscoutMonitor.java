package org.nightscout.lasso;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
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
import com.nightscout.core.model.GlucoseUnit;
import com.nightscout.core.model.InsertionEntry;
import com.nightscout.core.model.MeterEntry;
import com.nightscout.core.model.SensorEntry;
import com.nightscout.core.model.SensorGlucoseValueEntry;
import com.nightscout.core.mqtt.MqttEventMgr;
import com.nightscout.core.mqtt.MqttMgrObserver;
import com.nightscout.core.mqtt.MqttPinger;
import com.nightscout.core.mqtt.MqttTimer;
import com.nightscout.core.preferences.NightscoutPreferences;
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
import org.joda.time.Minutes;
import org.joda.time.Seconds;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;
import org.nightscout.lasso.alarm.Alarm;
import org.nightscout.lasso.events.AndroidEventReporter;
import org.nightscout.lasso.model.CalibrationDbEntry;
import org.nightscout.lasso.model.InsertionDbEntry;
import org.nightscout.lasso.model.MeterDbEntry;
import org.nightscout.lasso.model.SensorDbEntry;
import org.nightscout.lasso.model.SgvDbEntry;
import org.nightscout.lasso.mqtt.AndroidMqttPinger;
import org.nightscout.lasso.mqtt.AndroidMqttTimer;
import org.nightscout.lasso.preferences.AndroidPreferences;

import java.io.IOException;
import java.net.URI;
import java.text.DecimalFormat;
import java.util.List;

public class NightscoutMonitor extends Service implements MqttMgrObserver {
    public static final String RECEIVER_STATE_INTENT = "org.nightscout.scout.RECEIVER_STATE";
    public static final String SNOOZE_INTENT = "org.nightscout.scout.SNOOZE";
    public static final String MQTT_QUERY_STATUS_INTENT = "org.nightscout.scout.MQTT_QUERY_STATUS";
    public static final String MQTT_RESPONSE_STATUS_INTENT = "org.nightscout.scout.MQTT_RESPONSE_STATUS";
    public static final String NEW_READING_ACTION = "org.nightscout.NEW_READING";
    public static final String MQTT_STATUS_EXTRA_FIELD = "MqttConnected";
    private String TAG = this.getClass().getSimpleName();
    private AndroidEventReporter reporter;
    private AndroidPreferences preferences;
    private MqttEventMgr mqttManager;
    private Alarm alarm;
    private Optional<Integer> uploaderBattery = Optional.absent();
    private int messageCount = 0;
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
                    alarm.alarmSnooze(Minutes.minutes(30).toStandardDuration().getMillis());
                }
            } else if (intent.getAction().equals(MQTT_QUERY_STATUS_INTENT)) {
                Intent responseIntent = new Intent(MQTT_RESPONSE_STATUS_INTENT);
                responseIntent.putExtra(MQTT_STATUS_EXTRA_FIELD, mqttManager.isConnected());
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(responseIntent);
            }
        }
    };

    public NightscoutMonitor() {
    }

    public void ackAlarm(final String uriStr) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Integer level = alarm.getAlarmResults().severity.ordinal() - 3;
                URI uri = URI.create(uriStr + "/notifications/ack?level=" + level);
                Log.w(TAG, "Sending snooze command to " + uri.toString() + "/notifications/ack?level=" + level + "&time=" + 15000);
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

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "service created");
        preferences = new AndroidPreferences(getApplicationContext());
        reporter = AndroidEventReporter.getReporter(getApplicationContext());
        preferences.setMqttUploadEnabled(true);
        setupMqtt();
        // FIXME - hack to get the app to start up
        if (mqttManager != null) {
            mqttManager.registerObserver(this);
            mqttManager.subscribe(2, "/downloads/protobuf");
            if (preferences.getAlarmStrategy() == 0) {
                Log.d(TAG, "Subscribing to notifications");
                mqttManager.subscribe(2, "/notifications/json");
            }
        }
        getApplicationContext().registerReceiver(broadcastReceiver, new IntentFilter(SNOOZE_INTENT));
        alarm = new Alarm(getApplicationContext());
    }
    // TODO handle mqtt configuration changes


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "service started");
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
        Log.d(TAG, "service destroyed");
    }

    @Override
    public void onMessage(String topic, MqttMessage message) {
        if (topic.equals("/notifications/json")) {
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
        } else if (topic.equals("/downloads/protobuf")) {
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

                NightscoutPreferences preferences = new AndroidPreferences(getApplicationContext());
                List<EGVRecord> egvRecords = SgvDbEntry.getLastEgvRecords(new DateTime().minus(Minutes.minutes(90)));
                Log.d(TAG, "Analyzing message #" + messageCount);
                DateTime dlTimestamp = ISODateTimeFormat.dateTime().parseDateTime(download.download_timestamp);
                alarm.analyze(egvRecords, preferences.getPreferredUnits(), Optional.fromNullable(download.uploader_battery), dlTimestamp);
                alarm.alarm();

                Log.e(TAG, "New reading came in - EGV: " + egvRecords.get(egvRecords.size() - 1).getBgMgdl() + ". Reading taken at: " + egvRecords.get(egvRecords.size() - 1).getWallTime());

                String delta = "None";
                if (egvRecords.size() > 2) {
                    GlucoseReading glucoseDelta = egvRecords.get(egvRecords.size() - 1).getReading().subtract(egvRecords.get(egvRecords.size() - 2).getReading());
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
                EGVRecord lastEgv = egvRecords.get(egvRecords.size() - 1);
                IsigReading isigReading = new IsigReading();
                if (lastCal.isPresent() && lastSensor.isPresent() && Math.abs(lastEgv.getSystemTime().getMillis() - lastSensor.get().getSystemTime().getMillis()) < Seconds.seconds(10).toStandardDuration().getMillis()) {
                    isigReading = new IsigReading(lastSensor.get(), lastCal.get(), egvRecords.get(egvRecords.size() - 1));
                    Log.e("isig", "iSig reading: " + isigReading.asMgdlStr());
                } else {
                    Log.w("isig", "Problem matching sensor to egv for isig calculation");
                    Log.w("isig", "Last egv: " + lastEgv.getSystemTime().getMillis());
                    Log.w("isig", "Last sensor: " + lastSensor.get().getSystemTime().getMillis());
                }
                WatchMaker.sendReadings(egvRecords.get(egvRecords.size() - 1), egvRecords.get(egvRecords.size() - 1).getNoiseMode(), isigReading, preferences.getPreferredUnits(), delta, uploaderBattery.or(-1), Optional.fromNullable(download.receiver_battery).or(-1), getApplicationContext());


                Intent intent = new Intent(NEW_READING_ACTION);
                Log.d(TAG, "Msg #" + messageCount + " - Battery: " + download.uploader_battery);
                intent.putExtra("uploaderBattery", download.uploader_battery);
                Log.d(TAG, "Msg #" + messageCount + " - Receiver: " + download.receiver_battery);

                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            } catch (IOException e) {
                e.printStackTrace();
            }
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
        Intent intent = new Intent(MQTT_RESPONSE_STATUS_INTENT);
        intent.putExtra(MQTT_STATUS_EXTRA_FIELD, false);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    @Override
    public void onConnect() {
        Intent intent = new Intent(MQTT_RESPONSE_STATUS_INTENT);
        intent.putExtra(MQTT_STATUS_EXTRA_FIELD, true);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }
}

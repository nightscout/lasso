package org.nightscout.lasso;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.TextView;

import com.nightscout.core.dexcom.Utils;
import com.nightscout.core.dexcom.records.CalRecord;
import com.nightscout.core.dexcom.records.EGVRecord;
import com.nightscout.core.dexcom.records.SensorRecord;
import com.nightscout.core.model.GlucoseUnit;
import com.nightscout.core.model.ReceiverStatus;
import com.nightscout.core.preferences.NightscoutPreferences;
import com.nightscout.core.utils.GlucoseReading;
import com.nightscout.core.utils.IsigReading;

import net.tribe7.common.base.Optional;

import org.joda.time.DateTime;
import org.joda.time.Hours;
import org.joda.time.Instant;
import org.joda.time.Minutes;
import org.joda.time.Seconds;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.nightscout.lasso.alarm.Alarm;
import org.nightscout.lasso.model.CalibrationDbEntry;
import org.nightscout.lasso.model.SensorDbEntry;
import org.nightscout.lasso.model.SgvDbEntry;
import org.nightscout.lasso.preferences.AndroidPreferences;
import org.nightscout.lasso.settings.SettingsActivity;

import java.text.DecimalFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import butterknife.ButterKnife;
import butterknife.InjectView;

import static com.nightscout.core.dexcom.SpecialValue.getEGVSpecialValue;
import static com.nightscout.core.dexcom.SpecialValue.isSpecialValue;
import static org.joda.time.Duration.standardMinutes;

public class MainActivity extends AppCompatActivity {

    @InjectView(R.id.webView)
    WebView mWebView;
    @InjectView(R.id.sgValue)
    TextView sgvText;
    @InjectView(R.id.trendView)
    TextView trendView;
    @InjectView(R.id.syncButton)
    ImageButton uploadButton;
    @InjectView(R.id.usbButton)
    ImageButton receiverButton;

    private PillBoxWidget dPill;
    private PillBoxWidget rawPill;
    private PillBoxWidget receiverBatteryPill;
    private PillBoxWidget uploaderBatteryPill;
    private PillBoxWidget agoPill;
    private NightscoutPreferences preferences;
    private Menu menu;


    private Optional<EGVRecord> lastEgv = Optional.absent();
    private Handler mHandler = new Handler();

    public Runnable updateTimeAgo = new Runnable() {
        @Override
        public void run() {
            long delay = Seconds.seconds(30).toStandardDuration().getMillis();
            if (lastEgv.isPresent()) {
                setTimeAgoPill(lastEgv.get().getWallTime());
                mHandler.removeCallbacks(updateTimeAgo);
                long delta = Instant.now().getMillis() - lastEgv.get().getWallTime().getMillis();
                delay = delta % standardMinutes(1).getMillis();
                Log.d("Delay", "Delay: " + delay);
                Log.d("Delay", "Delta: " + delta);
            }
            mHandler.postDelayed(updateTimeAgo, delay);
        }
    };

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(NightscoutMonitor.NEW_READING_ACTION)) {
                int uploaderBattery = Optional.fromNullable(intent.getExtras().getInt("uploaderBattery")).or(-1);
                int receiverBattery = Optional.fromNullable(intent.getExtras().getInt("receiverBattery")).or(-1);
                // Fill the graph with data
                List<EGVRecord> egvRecords = SgvDbEntry.getLastEgvRecords(new DateTime().minus(Hours.hours(4)));
                if (egvRecords.size() > 0){
                    updateView(egvRecords, uploaderBattery, receiverBattery);
                }
            } else if (intent.getAction().equals(NightscoutMonitor.RECEIVER_STATE_INTENT)) {
                Optional<String> receiverStatus = Optional.fromNullable(intent.getExtras().getString("state"));
                if (receiverStatus.isPresent() & receiverStatus.get().equals(ReceiverStatus.RECEIVER_CONNECTED.name())) {
                    receiverButton.setBackgroundResource(R.drawable.ic_usb);
                } else {
                    receiverButton.setBackgroundResource(R.drawable.ic_nousb);
                }
            } else if (intent.getAction().equals(NightscoutMonitor.MQTT_RESPONSE_STATUS_INTENT)) {
                int res = (intent.getExtras().getBoolean(NightscoutMonitor.MQTT_STATUS_EXTRA_FIELD)) ? R.drawable.ic_cloud : R.drawable.ic_nocloud;
                uploadButton.setImageResource(res);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("MainActivity", "Created");
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);
        Intent intent = new Intent(this, NightscoutMonitor.class);
        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter(NightscoutMonitor.NEW_READING_ACTION);
        intentFilter.addAction(NightscoutMonitor.RECEIVER_STATE_INTENT);
        intentFilter.addAction(NightscoutMonitor.MQTT_RESPONSE_STATUS_INTENT);
        broadcastManager.registerReceiver(broadcastReceiver, intentFilter);

        setupChart();

        if (SgvDbEntry.getLastEgv(Alarm.ALARM_TIMEAGO_WARN_MINS).isPresent()) {
            lastEgv = Optional.of(SgvDbEntry.getLastEgv().get());
        }
        preferences = new AndroidPreferences(getApplicationContext());

        receiverBatteryPill = new PillBoxWidget(R.id.rcbat);
        receiverBatteryPill.update("RB", "??");
        uploaderBatteryPill = new PillBoxWidget(R.id.ulbat);
        uploaderBatteryPill.update("UB", "??");
        agoPill = new PillBoxWidget(R.id.minago);
        agoPill.updateHeader("ago");
        agoPill.updateValue("?");
        rawPill = new PillBoxWidget(R.id.rawIsig);
        rawPill.updateHeader("Noise");
        rawPill.updateValue("???");
//        GlucoseReading initialReading = new GlucoseReading(0,GlucoseUnit.MGDL);
//        rawPill.updateValue(initialReading, preferences.getPreferredUnits());
        dPill = new PillBoxWidget(R.id.deltapill);
        dPill.updateValue("??");
        dPill.updateHeader(Utils.unitString(preferences.getPreferredUnits()));
//        dPill.updateValue(initialReading, preferences.getPreferredUnits());
        startService(intent);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void setupChart(){
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAppCacheEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webSettings.setUseWideViewPort(false);
        mWebView.setVerticalScrollBarEnabled(false);
        mWebView.setHorizontalScrollBarEnabled(false);
        mWebView.setBackgroundColor(0);
        mWebView.loadUrl("file:///android_asset/index.html");
        // disable scroll on touch
        mWebView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return (event.getAction() == MotionEvent.ACTION_MOVE);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        this.menu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.service_start) {
            Intent serviceIntent = new Intent(getApplicationContext(), NightscoutMonitor.class);
            if (!((Lasso) getApplication()).isServiceStarted()){
                Log.d("ServiceControl", "Starting service");
                menu.findItem(R.id.service_start).setVisible(false);
                menu.findItem(R.id.service_stop).setVisible(true);
                startService(serviceIntent);
            } else {
                menu.findItem(R.id.service_start).setVisible(false);
                menu.findItem(R.id.service_stop).setVisible(true);
            }
        } else if (id == R.id.service_stop) {
            Intent serviceIntent = new Intent(getApplicationContext(), NightscoutMonitor.class);
            if (((Lasso) getApplication()).isServiceStarted()) {
                Log.d("ServiceControl", "Starting service");
                menu.findItem(R.id.service_start).setVisible(true);
                menu.findItem(R.id.service_stop).setVisible(false);
                stopService(serviceIntent);
            } else {
                menu.findItem(R.id.service_start).setVisible(true);
                menu.findItem(R.id.service_stop).setVisible(false);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void setTimeAgoPill(DateTime start) {
        if (start == null) {
            return;
        }
        Minutes minutes = Minutes.minutesBetween(start, Instant.now());
        PeriodFormatterBuilder periodFormatterBuilder = new PeriodFormatterBuilder();
        if (minutes.toStandardWeeks().getWeeks() > 1) {
            periodFormatterBuilder.appendWeeks().appendSuffix(getApplicationContext().getString(R.string.week), getApplicationContext().getString(R.string.weeks));
        } else if (minutes.toStandardDays().getDays() > 1) {
            periodFormatterBuilder.appendDays().appendSuffix(getApplicationContext().getString(R.string.day), getApplicationContext().getString(R.string.days));
        } else if (minutes.toStandardHours().getHours() > 1) {
            periodFormatterBuilder.appendHours().appendSuffix(getApplicationContext().getString(R.string.hour), getApplicationContext().getString(R.string.hours));
        } else {
            periodFormatterBuilder.appendMinutes().appendSuffix(getApplicationContext().getString(R.string.minute), getApplicationContext().getString(R.string.minutes));
        }
        periodFormatterBuilder.appendLiteral(" ago");
        PeriodFormatter formatter = periodFormatterBuilder.toFormatter();
        String pattern = "(\\d+)(.*)";
        Pattern r = Pattern.compile(pattern);
        Log.d("minutes", "Minutes: " + formatter.print(minutes));
        Matcher m = r.matcher(formatter.print(minutes));
        if (m.find()) {
            String number = m.group(1);
            String ago = m.group(2);
            agoPill.update(ago, number);
        }
    }

    @Override
    public void onPause() {
        Log.d("MainActivity", "Paused");
        mWebView.pauseTimers();
        mWebView.onPause();
        mHandler.removeCallbacks(updateTimeAgo);
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.d("MainActivity", "Resumed");
        mWebView.onResume();
        mWebView.resumeTimers();
        NightscoutMonitor.queryMqttStatusAsync(getApplicationContext());
        GlucoseUnit unit = preferences.getPreferredUnits();
        dPill.restoreView(unit);
        rawPill.restoreView(unit, false);
        receiverBatteryPill.restoreView();
        uploaderBatteryPill.restoreView();
        restoreSgvText();
        mHandler.post(updateTimeAgo);
        super.onResume();
    }

    @Override
    protected void onStart() {
        Log.d("MainActivity", "Started");
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("MainActivity", "Stopped");
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        Log.d("MainActivity", "Destroyed");
        super.onDestroy();
    }


    private void updateView(List<EGVRecord> egvRecords, int uploaderBattery, int receiverBattery) {
        updateBatteries(uploaderBattery, receiverBattery);
        updateSgv(egvRecords);
        updateChart(egvRecords);
        updateDeltaPill(lastEgv.get(), egvRecords.get(egvRecords.size() - 2));
        updateRawPill(egvRecords);
    }

    private void updateSgv(List<EGVRecord> egvRecords){
        if (egvRecords.size() > 0) {
            lastEgv = Optional.of(egvRecords.get(egvRecords.size() - 1));
            if (lastEgv.isPresent()) {
                setTimeAgoPill(lastEgv.get().getWallTime());
            }
            setSgvText(lastEgv.get().getReading());
            this.trendView.setText(lastEgv.get().getTrend().symbol());
            if (new DateTime().minus(Alarm.ALARM_TIMEAGO_WARN_MINS).isAfter(lastEgv.get().getWallTime())) {
                this.sgvText.setPaintFlags(this.sgvText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                this.sgvText.setPaintFlags(this.sgvText.getPaintFlags() & (~ Paint.STRIKE_THRU_TEXT_FLAG));
            }
        }
    }

    private void updateBatteries(int uploaderBattery, int receiverBattery){
        receiverBatteryPill.updateValue(String.valueOf(receiverBattery));
        Log.d("Battery", "Receiver Battery: " + receiverBattery);
        uploaderBatteryPill.updateValue(String.valueOf(uploaderBattery));
        Log.d("Battery", "Uploader Battery: " + uploaderBattery);
    }

    private void updateChart(List<EGVRecord> egvRecords){
        JSONArray array = new JSONArray();
        for (EGVRecord record : egvRecords) {
            try {
                array.put(record.toJSON());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        Log.e("MainActivity", "Json array " + array);
        mWebView.loadUrl("javascript:updateData(" + array + ")");
    }

    private void updateDeltaPill(EGVRecord record1, EGVRecord record2){
        String delta = "None";
            GlucoseReading glucoseDelta = record1.getReading().subtract(record2.getReading());
            DecimalFormat fmt;
            if (preferences.getPreferredUnits() == GlucoseUnit.MGDL) {
                fmt = new DecimalFormat("+#,##0;-#");
            } else {
                fmt = new DecimalFormat("+#,##0.0;-#");
            }
            if (record1.getReading().asMgdl() > 38 && record2.getReading().asMgdl() > 38) {
                delta = fmt.format(glucoseDelta.as(preferences.getPreferredUnits()));
            }
        Log.d("DELTA", "Delta: "+delta);
        dPill.updateValue(glucoseDelta, preferences.getPreferredUnits());
    }

    private void updateRawPill(List<EGVRecord> egvRecords){
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
            rawPill.update(lastEgv.get().getNoiseMode().name(), isigReading, preferences.getPreferredUnits());
        }
    }

    private void setSgvText(GlucoseReading reading){
        sgvText.setText(getSgvOrMessage(reading));
        sgvText.setTag(R.id.sgv_value, reading.asMgdl());
    }

    private void restoreSgvText() {
        if (sgvText.getTag(R.id.sgv_value) != null) {
            GlucoseReading reading = new GlucoseReading((int) sgvText.getTag(R.id.sgv_value), GlucoseUnit.MGDL);
            sgvText.setText(getSgvOrMessage(reading));
        }
    }

    private String preferredUnitsString(GlucoseUnit units) {
        return (units == GlucoseUnit.MGDL ? "mg/dL" : "mmoL");
    }

    private String getSgvOrMessage(GlucoseReading sgv) {
        String sgvStr = sgv.asStr(preferences.getPreferredUnits());
        return (isSpecialValue(sgv)) ? getEGVSpecialValue(sgv).get().toString() : sgvStr;
    }


    public class PillBoxWidget {
        private View view;

        public PillBoxWidget(int res) {
            this.view = findViewById(res);
        }

        public void update(String header, String value) {
            updateHeader(header);
            updateValue(value);
        }

        public void updateValue(String value) {
            ((TextView) view.findViewById(R.id.sgv_value)).setText(value);
            view.setTag(R.string.pill_text, value);
        }

        public void update(String header, GlucoseReading reading, GlucoseUnit unit) {
            updateValue(reading, unit);
            updateHeader(header);
        }

        public void updateValue(GlucoseReading reading, GlucoseUnit unit) {
            ((TextView) view.findViewById(R.id.sgv_value)).setText(reading.asStr(unit));
            view.setTag(R.id.sgv_value, reading.asMgdl());
        }

        public void updateHeader(String header) {
            ((TextView) view.findViewById(R.id.heading)).setText(header);
            view.setTag(R.id.heading, header);
        }

        public void restoreView() {
            restoreHeader();
            restoreValue();
        }

        public void restoreView(GlucoseUnit units) {
            restoreView(units, true);
        }

        public void restoreView(GlucoseUnit units, boolean setHeaderToUnits) {
            if (setHeaderToUnits) {
                updateHeader(Utils.unitString(units));
            }
            restoreValue(units);
        }

        private void restoreHeader() {
            updateHeader((String) view.getTag(R.id.heading));
        }

        public void restoreValue() {
            updateValue((String) view.getTag(R.string.pill_text));
        }

        public void restoreValue(GlucoseUnit unit) {
            if (view.getTag(R.id.sgv_value) != null) {
                String reading = new GlucoseReading((int) view.getTag(R.id.sgv_value), GlucoseUnit.MGDL).asStr(unit);
                ((TextView) view.findViewById(R.id.sgv_value)).setText(reading);
            }
        }
    }
}

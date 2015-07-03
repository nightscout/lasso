package org.nightscout.lasso;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import com.nightscout.core.dexcom.TrendArrow;
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
    TextView mTextSGV;
    @InjectView(R.id.syncButton)
    ImageButton uploadButton;
    @InjectView(R.id.usbButton)
    ImageButton receiverButton;

    private PillBoxWidget dPill;
    private PillBoxWidget rawPill;
    private PillBoxWidget receiverBatteryPill;
    private PillBoxWidget uploaderBatteryPill;
    private PillBoxWidget minago;
    private NightscoutPreferences preferences;


    private DateTime lastEgv;
    private Handler mHandler = new Handler();

    public Runnable updateTimeAgo = new Runnable() {
        @Override
        public void run() {

            setTimeAgoPill(lastEgv);
            mHandler.removeCallbacks(updateTimeAgo);
            long delay = Seconds.seconds(30).toStandardDuration().getMillis();
            if (lastEgv != null) {
                long delta = Instant.now().getMillis() - lastEgv.getMillis();
                delay = delta % standardMinutes(1).getMillis();
                Log.w("Delay", "Delay: " + delay);
                Log.w("Delay", "Delta: " + delta);
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
                List<EGVRecord> egvRecords = SgvDbEntry.getLastEgvRecords(new DateTime().minus(Hours.hours(4)));
                updateView(egvRecords, uploaderBattery, receiverBattery);
            } else if (intent.getAction().equals(NightscoutMonitor.RECEIVER_STATE_INTENT)) {
                Optional<String> receiverStatus = Optional.fromNullable(intent.getExtras().getString("state"));
                if (receiverStatus.isPresent()) {
                    Log.d("StateReceiver", "Received state: " + receiverStatus.get());
                    if (receiverStatus.get().equals(ReceiverStatus.RECEIVER_CONNECTED.name())) {
                        receiverButton.setBackgroundResource(R.drawable.ic_usb);
                    } else {
                        receiverButton.setBackgroundResource(R.drawable.ic_nousb);
                    }
                }
            } else if (intent.getAction().equals(NightscoutMonitor.MQTT_RESPONSE_STATUS_INTENT)) {
                int res = (intent.getExtras().getBoolean(NightscoutMonitor.MQTT_STATUS_EXTRA_FIELD)) ? R.drawable.ic_cloud : R.drawable.ic_nocloud;
                uploadButton.setImageResource(res);
            }
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("onCreate", "Created");
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);
        Intent intent = new Intent(this, NightscoutMonitor.class);
        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter(NightscoutMonitor.NEW_READING_ACTION);
        intentFilter.addAction(NightscoutMonitor.RECEIVER_STATE_INTENT);
        intentFilter.addAction(NightscoutMonitor.MQTT_RESPONSE_STATUS_INTENT);
        broadcastManager.registerReceiver(broadcastReceiver, intentFilter);

        if (SgvDbEntry.getLastEgv().isPresent()) {
            lastEgv = SgvDbEntry.getLastEgv().get().getWallTime();
        }
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
        receiverBatteryPill = new PillBoxWidget(R.id.rcbat);
        receiverBatteryPill.update("RB", "??");
        uploaderBatteryPill = new PillBoxWidget(R.id.ulbat);
        uploaderBatteryPill.update("UB", "??");
        minago = new PillBoxWidget(R.id.minago);
        rawPill = new PillBoxWidget(R.id.rawIsig);
        rawPill.update("Raw", "Noise");
        preferences = new AndroidPreferences(getApplicationContext());
        dPill = new PillBoxWidget(R.id.deltapill);
        dPill.update(preferredUnitsString(preferences.getPreferredUnits()), "??");
        startService(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
            minago.update(ago, number);
        }
    }

    @Override
    public void onPause() {
        mWebView.pauseTimers();
        mWebView.onPause();
        mHandler.removeCallbacks(updateTimeAgo);
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.d("onResume", "Resumed");
        mWebView.onResume();
        mWebView.resumeTimers();
//        List<EGVRecord> egvRecords = SgvDbEntry.getLastEgvRecords(new DateTime().minus(Hours.hours(4)));
//        Log.d("onResume", "Number of records => " + egvRecords.size());
        dPill.restoreView();
        rawPill.restoreView();
        receiverBatteryPill.restoreView();
        uploaderBatteryPill.restoreView();
        rawPill.restoreView();
        mHandler.post(updateTimeAgo);
        super.onResume();
    }

    @Override
    protected void onStart() {
        Log.d("onStart", "Started");
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("onStop", "Stopped");
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        super.onDestroy();
    }


    private void updateView(List<EGVRecord> egvRecords, int uploaderBattery, int receiverBattery) {
        receiverBatteryPill.update(String.valueOf(receiverBattery));
        uploaderBatteryPill.update(String.valueOf(uploaderBattery));

        Log.d("message receiver", "Battery: " + uploaderBattery);

        lastEgv = egvRecords.get(egvRecords.size() - 1).getWallTime();
        setTimeAgoPill(lastEgv);

        String sgvText = getSGVStringByUnit(egvRecords.get(egvRecords.size() - 1).getReading(), egvRecords.get(egvRecords.size() - 1).getTrend());
        mTextSGV.setText(sgvText);

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

        // TODO - Moved watchmaker integration from this activity to the service to ensure that new updates will be pushed regardless of whether
        // or not the activity is running so long as the service is active.
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
        dPill.update(preferredUnitsString(preferences.getPreferredUnits()), String.valueOf(delta));

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
        rawPill.update(egvRecords.get(egvRecords.size() - 1).getNoiseMode().name(), isigReading.asStr(preferences.getPreferredUnits()));
    }

    private String preferredUnitsString(GlucoseUnit units) {
        return (units == GlucoseUnit.MGDL ? "mg/dL" : "mmoL");
    }

    private String getSGVStringByUnit(GlucoseReading sgv, TrendArrow trend) {
        String sgvStr = sgv.asStr(preferences.getPreferredUnits());
        return (sgv.asMgdl() != -1) ?
                (isSpecialValue(sgv)) ?
                        getEGVSpecialValue(sgv).get().toString() : sgvStr + " " + trend.symbol() : "---";
    }


    public class PillBoxWidget {
        private View view;

        public PillBoxWidget(int res) {
            this.view = findViewById(res);
        }

        public void update(String header, String value) {
            updateHeader(header);
            update(value);
        }

        public void update(String value) {
            ((TextView) view.findViewById(R.id.pillvalue)).setText(value);
            view.setTag(R.id.pillvalue, value);
        }

        public void updateHeader(String header) {
            ((TextView) view.findViewById(R.id.heading)).setText(header);
            view.setTag(R.id.heading, header);
        }

        public void restoreView() {
            ((TextView) view.findViewById(R.id.pillvalue)).setText((String) view.getTag(R.id.pillvalue));
            ((TextView) view.findViewById(R.id.heading)).setText((String) view.getTag(R.id.heading));
        }

        public void restoreViewWithConversion() {
            Integer val = Integer.valueOf((String) view.getTag(R.id.pillvalue));
            GlucoseReading glucoseReading = new GlucoseReading(val, GlucoseUnit.MGDL);
            ((TextView) view.findViewById(R.id.pillvalue)).setText(glucoseReading.asStr(preferences.getPreferredUnits()));
            ((TextView) view.findViewById(R.id.heading)).setText((String) view.getTag(R.id.heading));
        }

    }
}

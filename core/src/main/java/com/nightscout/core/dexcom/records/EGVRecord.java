package com.nightscout.core.dexcom.records;

import com.nightscout.core.dexcom.Constants;
import com.nightscout.core.dexcom.InvalidRecordLengthException;
import com.nightscout.core.dexcom.TrendArrow;
import com.nightscout.core.dexcom.Utils;
import com.nightscout.core.model.G4Noise;
import com.nightscout.core.model.GlucoseUnit;
import com.nightscout.core.model.SensorGlucoseValueEntry;
import com.nightscout.core.utils.GlucoseReading;

import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public class EGVRecord extends GenericTimestampRecord {
    public final static int RECORD_SIZE = 12;
    private GlucoseReading reading;
    private TrendArrow trend;
    private G4Noise noiseMode;

    public EGVRecord(byte[] packet, long rcvrTime, long refTime) {
        super(packet, rcvrTime, refTime);
        if (packet.length != RECORD_SIZE) {
            throw new InvalidRecordLengthException("Unexpected record size: " + packet.length +
                    ". Expected size: " + RECORD_SIZE + ". Unparsed record: " + Utils.bytesToHex(packet));
        }
        int bGValue = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getShort(8) & Constants.EGV_VALUE_MASK;
        this.reading = new GlucoseReading(bGValue, GlucoseUnit.MGDL);
        byte trendAndNoise = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).get(10);
        int trendValue = trendAndNoise & Constants.EGV_TREND_ARROW_MASK;
        byte noiseValue = (byte) ((trendAndNoise & Constants.EGV_NOISE_MASK) >> 4);
        this.trend = TrendArrow.values()[trendValue];
        this.noiseMode = G4Noise.values()[noiseValue];
        setRecordType();
    }

    public EGVRecord(int bGValueMgdl, TrendArrow trend, DateTime displayTime, DateTime systemTime, G4Noise noise, DateTime wallTime) {
        super(displayTime, systemTime, wallTime);
        this.reading = new GlucoseReading(bGValueMgdl, GlucoseUnit.MGDL);
        this.trend = trend;
        this.noiseMode = noise;
        setRecordType();
    }

    public EGVRecord(int bGValueMgdl, TrendArrow trend, long displayTime, long systemTime, G4Noise noise, long rcvrTime, long refTime) {
        super(displayTime, systemTime, rcvrTime, refTime);
        this.reading = new GlucoseReading(bGValueMgdl, GlucoseUnit.MGDL);
        this.trend = trend;
        this.noiseMode = noise;
        setRecordType();
    }

    public EGVRecord(SensorGlucoseValueEntry sgv, long rcvrTime, long refTime) {
        super(sgv.disp_timestamp_sec, sgv.sys_timestamp_sec, rcvrTime, refTime);
        this.reading = new GlucoseReading(sgv.sgv_mgdl, GlucoseUnit.MGDL);
        this.trend = TrendArrow.values()[sgv.trend.ordinal()];
        this.noiseMode = sgv.noise;
        setRecordType();
    }

    public static List<SensorGlucoseValueEntry> toProtobufList(List<EGVRecord> list) {
        return toProtobufList(list, SensorGlucoseValueEntry.class);
    }

    protected void setRecordType() {
        this.recordType = "sgv";
    }

    public int getBgMgdl() {
        return reading.asMgdl();
    }

    public TrendArrow getTrend() {
        return trend;
    }

    public G4Noise getNoiseMode() {
        return noiseMode;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("sgv", getBgMgdl());
        obj.put("date", getWallTime());
        return obj;
    }

    @Override
    public SensorGlucoseValueEntry toProtobuf() {
        SensorGlucoseValueEntry.Builder builder = new SensorGlucoseValueEntry.Builder();
        return builder.sys_timestamp_sec(rawSystemTimeSeconds)
                .disp_timestamp_sec(rawDisplayTimeSeconds)
                .sgv_mgdl(reading.asMgdl())
                .trend(trend.toProtobuf())
                .noise(noiseMode)
                .build();
    }

    public GlucoseReading getReading() {
        return reading;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        EGVRecord egvRecord = (EGVRecord) o;

        if (noiseMode != egvRecord.noiseMode) return false;
        if (!reading.equals(egvRecord.reading)) return false;
        return trend == egvRecord.trend;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + reading.hashCode();
        result = 31 * result + trend.hashCode();
        result = 31 * result + noiseMode.hashCode();
        return result;
    }
}

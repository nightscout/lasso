package com.nightscout.core.dexcom.records;

import com.nightscout.core.dexcom.InvalidRecordLengthException;
import com.nightscout.core.dexcom.Utils;
import com.nightscout.core.model.CalibrationEntry;

import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class CalRecord extends GenericTimestampRecord {
    public final static int RECORD_SIZE = 147;
    public final static int RECORD_V2_SIZE = 248;
    private static final Logger LOG = LoggerFactory.getLogger(CalRecord.class);
    private double slope;
    private double intercept;
    private double scale;
    private int[] unk = new int[3];
    private double decay;
    private int numRecords;
    private List<CalSubrecord> calSubrecords;
    private int SUB_LEN = 17;

    public CalRecord(byte[] packet, long rcvrTime, long refTime) {
        super(packet, rcvrTime, refTime);
        if (packet.length != RECORD_SIZE && packet.length != RECORD_V2_SIZE) {
            throw new InvalidRecordLengthException("Unexpected record size: " + packet.length +
                    ". Expected size: " + RECORD_SIZE + ". Unparsed record: " + Utils.bytesToHex(packet));
        }
        slope = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getDouble(8);
        intercept = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getDouble(16);
        scale = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getDouble(24);
        unk[0] = packet[32];
        unk[1] = packet[33];
        unk[2] = packet[34];
        decay = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getDouble(35);
        numRecords = packet[43];
        calSubrecords = new ArrayList<>();

        long displayTimeOffset = Seconds.secondsBetween(
                new DateTime(getSystemTime()),
                new DateTime(getDisplayTime())).toStandardDuration().getMillis();
        int start = 44;
        for (int i = 0; i < numRecords; i++) {
            byte[] temp = new byte[SUB_LEN];
            System.arraycopy(packet, start, temp, 0, temp.length);
            calSubrecords.add(new CalSubrecord(temp, displayTimeOffset));
            start += SUB_LEN;
        }
        setRecordType();
    }

    public CalRecord(double intercept, double slope, double scale, double decay, DateTime displayTime, DateTime systemTime, List<CalSubrecord> subrecord, DateTime wallTime) {
        super(displayTime, systemTime, wallTime);
        this.intercept = intercept;
        this.slope = slope;
        this.scale = scale;
        this.decay = decay;
        this.numRecords = subrecord.size();
        this.calSubrecords = subrecord;
        setRecordType();
    }

    public CalRecord(double intercept, double slope, double scale, double decay, long displayTime, long systemTime, List<CalSubrecord> subrecord, long rcvrTime, long refTime) {
        super(displayTime, systemTime, rcvrTime, refTime);
        this.intercept = intercept;
        this.slope = slope;
        this.scale = scale;
        this.decay = decay;
        this.numRecords = subrecord.size();
        this.calSubrecords = subrecord;
        setRecordType();
    }

    public CalRecord(CalibrationEntry cal, long rcvrTime, long refTime) {
        super(cal.disp_timestamp_sec, cal.sys_timestamp_sec, rcvrTime, refTime);
        this.intercept = cal.intercept;
        this.slope = cal.slope;
        this.scale = cal.scale;
        this.decay = cal.decay;
        this.numRecords = 0;
        this.calSubrecords = new ArrayList<>();
        setRecordType();
    }

    public static List<CalibrationEntry> toProtobufList(List<CalRecord> list) {
        return toProtobufList(list, CalibrationEntry.class);
    }

    @Override
    public CalibrationEntry toProtobuf() {
        CalibrationEntry.Builder builder = new CalibrationEntry.Builder();
        return builder.sys_timestamp_sec(rawSystemTimeSeconds)
                .disp_timestamp_sec(rawDisplayTimeSeconds)
                .intercept(intercept)
                .scale(scale)
                .slope(slope)
                .decay(decay)
                .build();
    }

    public double getSlope() {
        return slope;
    }

    public double getIntercept() {
        return intercept;
    }

    public double getScale() {
        return scale;
    }

    public int[] getUnk() {
        return unk;
    }

    public double getDecay() {
        return decay;
    }

    public int getNumRecords() {
        return numRecords;
    }

    public List<CalSubrecord> getCalSubrecords() {
        return calSubrecords;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        CalRecord calRecord = (CalRecord) o;

        if (Double.compare(calRecord.decay, decay) != 0) return false;
        if (Double.compare(calRecord.intercept, intercept) != 0) return false;
        if (numRecords != calRecord.numRecords) return false;
        if (Double.compare(calRecord.scale, scale) != 0) return false;
        if (Double.compare(calRecord.slope, slope) != 0) return false;
        return !(calSubrecords != null ? !calSubrecords.equals(calRecord.calSubrecords) : calRecord.calSubrecords != null);

    }

    @Override
    protected void setRecordType() {
        this.recordType = "cal";
    }

    public CalibrationEntry toProtoBuf() {
        return new CalibrationEntry.Builder()
                .sys_timestamp_sec(rawSystemTimeSeconds)
                .disp_timestamp_sec(rawDisplayTimeSeconds)
                .intercept(intercept)
                .slope(slope)
                .scale(scale)
                .build();
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        long temp;
        temp = Double.doubleToLongBits(slope);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(intercept);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(scale);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(decay);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + numRecords;
        result = 31 * result + (calSubrecords != null ? calSubrecords.hashCode() : 0);
        return result;
    }

}

package com.nightscout.core.dexcom;

import com.nightscout.core.dexcom.records.GlucoseDataSet;
import com.nightscout.core.model.Download;
import com.nightscout.core.model.SensorEntry;
import com.nightscout.core.model.SensorGlucoseValueEntry;
import com.squareup.wire.Message;

import net.tribe7.common.base.Strings;
import net.tribe7.common.hash.HashCode;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static net.tribe7.common.base.Preconditions.checkNotNull;
import static org.joda.time.Duration.standardSeconds;

public final class Utils {
    public static final DateTime DEXCOM_EPOCH = new DateTime(2009, 1, 1, 0, 0, 0, 0).withZone(DateTimeZone.UTC);
    protected static final Logger log = LoggerFactory.getLogger(Utils.class);
    private static final String PRIMARY_SEPARATOR = ", ";
    private static final String SECONDARY_SEPARATOR = ", and ";
    private static final PeriodFormatter FORMATTER = new PeriodFormatterBuilder()
            .appendSeconds().appendSuffix(" seconds").appendSeparator(PRIMARY_SEPARATOR, SECONDARY_SEPARATOR)
            .appendMinutes().appendSuffix(" minutes").appendSeparator(PRIMARY_SEPARATOR, SECONDARY_SEPARATOR)
            .appendHours().appendSuffix(" hours").appendSeparator(PRIMARY_SEPARATOR, SECONDARY_SEPARATOR)
            .appendDays().appendSuffix(" days").appendSeparator(PRIMARY_SEPARATOR, SECONDARY_SEPARATOR)
            .appendWeeks().appendSuffix(" weeks").appendSeparator(PRIMARY_SEPARATOR, SECONDARY_SEPARATOR)
            .appendMonths().appendSuffix(" months").appendSeparator(PRIMARY_SEPARATOR, SECONDARY_SEPARATOR)
            .appendYears().appendSuffix(" years").appendLiteral(" ago")
            .printZeroNever()
            .toFormatter();

    // TODO: probably not the right way to do this but it seems to do the trick. Need to revisit this to fully understand what is going on during DST change
    public static DateTime receiverTimeToDateTime(long deltaInSeconds) {
        int offset = DateTimeZone.getDefault().getOffset(DEXCOM_EPOCH) - DateTimeZone.getDefault().getOffset(Instant.now());
        return DEXCOM_EPOCH.plus(offset).plus(standardSeconds(deltaInSeconds));
    }

    public static DateTime receiverTimeToDate(long delta) {
        return receiverTimeToDateTime(delta);
    }

    public static DateTime systemTimeToWallTime(long recordTimeSec, long receiverTimeSec, long referenceTimeMs) {
        long recordOffset = Duration.standardSeconds(receiverTimeSec - recordTimeSec).getMillis();
        return new DateTime(referenceTimeMs - recordOffset);
    }

    /**
     * Returns human-friendly string for the length of this duration, e.g. 4 seconds ago
     * or 4 days ago.
     *
     * @param period Non-null Period instance.
     * @return String human-friendly Period string, e.g. 4 seconds ago.
     */
    public static String getTimeAgoString(Period period) {
        checkNotNull(period);
        String output = FORMATTER.print(period);
        if (Strings.isNullOrEmpty(output)) {
            return "--";
        }
        return output;
    }

    public static String getTimeString(long timeDeltaMS) {
        long minutes = (timeDeltaMS / 1000) / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;
        minutes = minutes - hours * 60;
        hours = hours - days * 24;
        days = days - weeks * 7;

        String timeAgoString = "";
        if (weeks > 0) {
            timeAgoString += weeks + " weeks ";
        }
        if (days > 0) {
            timeAgoString += days + " days ";
        }
        if (hours > 0) {
            timeAgoString += hours + " hours ";
        }
        if (minutes >= 0) {
            timeAgoString += minutes + " min ";
        }

        return (timeAgoString.equals("") ? "--" : timeAgoString + "ago");
    }

    public static List<GlucoseDataSet> mergeGlucoseDataRecords(Download download, int numRecords) {
        List<SensorGlucoseValueEntry> sgvList = filterRecords(numRecords, download.sgv);
//        List<CalibrationEntry> calList = filterRecords(numRecords, download.cal);
//        List<MeterEntry> meterList = filterRecords(numRecords, download.meter);
        List<SensorEntry> sensorList = filterRecords(numRecords, download.sensor);
        long downloadTimestamp = DateTime.parse(download.download_timestamp).getMillis();
        return mergeGlucoseDataRecords(sgvList, sensorList, download.receiver_system_time_sec, downloadTimestamp);
    }

    public static <T extends Message> List<T> filterRecords(int numRecords, List<T> records) {
        int recordIndexToStop = Math.max(records.size() - numRecords, 0);
        List<T> results = new ArrayList<>();
        for (int i = records.size(); i > recordIndexToStop; i--) {
            results.add(records.get(i - 1));
        }
        return results;
    }


    public static List<GlucoseDataSet> mergeGlucoseDataRecords(List<SensorGlucoseValueEntry> egvRecords,
                                                               List<SensorEntry> sensorRecords,
                                                               long receiverTimestamp, long downloadTimestamp) {
        int egvLength = egvRecords.size();
        int sensorLength = sensorRecords.size();
        List<GlucoseDataSet> glucoseDataSets = new ArrayList<>();
        if (egvLength >= 0 && sensorLength == 0) {
            for (int i = 1; i <= egvLength; i++) {
                glucoseDataSets.add(new GlucoseDataSet(egvRecords.get(egvLength - i), receiverTimestamp, downloadTimestamp));
            }
            return glucoseDataSets;
        }
        int smallerLength = egvLength < sensorLength ? egvLength : sensorLength;
        for (int i = 1; i <= smallerLength; i++) {
            glucoseDataSets.add(new GlucoseDataSet(egvRecords.get(egvLength - i),
                    sensorRecords.get(sensorLength - i), receiverTimestamp, downloadTimestamp));
        }
        return glucoseDataSets;
    }


    public static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        return HashCode.fromBytes(bytes).toString().toUpperCase();
    }
}

package org.nightscout.lasso.model;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Select;
import com.nightscout.core.dexcom.records.CalRecord;
import com.nightscout.core.dexcom.records.CalSubrecord;

import net.tribe7.common.base.Optional;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

@Table(name = "calibrations")
public class CalibrationDbEntry extends Model {
    @Column(name = "systime", unique = true, onUniqueConflict = Column.ConflictAction.REPLACE)
    public long systime;

    @Column(name = "walltime")
    public long walltime;

    @Column(name = "slope")
    public double slope;

    @Column(name = "intercept")
    public double intercept;

    @Column(name = "scale")
    public double scale;

    @Column(name = "decay")
    public double decay;

    @Column(name = "transmitter_id")
    public DexcomTransmitterEntry transmitterId;

    @Column(name = "receiver_id")
    public DexcomReceiverEntry receiverId;

    public CalibrationDbEntry() {
        super();
    }

    public CalibrationDbEntry(CalRecord calRecord, String receiverId, String transmitterId) {
        super();
        this.systime = calRecord.getSystemTime().getMillis();
        this.walltime = calRecord.getWallTime().getMillis();
        this.slope = calRecord.getSlope();
        this.intercept = calRecord.getIntercept();
        this.scale = calRecord.getScale();
        this.decay = calRecord.getDecay();
        this.receiverId = new DexcomReceiverEntry(receiverId);
        this.transmitterId = new DexcomTransmitterEntry(transmitterId);
    }

    public static Optional<CalRecord> getLastCal() {
        CalibrationDbEntry calDbEntry = new Select().from(CalibrationDbEntry.class).orderBy("systime DESC").limit(1).executeSingle();
        CalRecord calRecord = null;
        if (calDbEntry != null) {
            DateTime unused1 = new DateTime(calDbEntry.systime);
            DateTime sysTime = new DateTime(calDbEntry.systime);
            DateTime wallTime = new DateTime(calDbEntry.walltime);
            List<CalSubrecord> subcalRecords = new ArrayList<>();
            calRecord = new CalRecord(calDbEntry.intercept, calDbEntry.slope, calDbEntry.scale, calDbEntry.decay, new DateTime(calDbEntry.systime), new DateTime(calDbEntry.systime), subcalRecords, new DateTime(calDbEntry.walltime));
        }
//        return new CalRecord(calDbEntry.intercept, calDbEntry.slope, calDbEntry.scale, calDbEntry.decay, new DateTime(calDbEntry.systime), new DateTime(calDbEntry.systime), subcalRecords, new DateTime(calDbEntry.walltime));
        return Optional.fromNullable(calRecord);
    }


}

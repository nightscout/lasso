package org.nightscout.lasso.model;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Select;
import com.nightscout.core.dexcom.TrendArrow;
import com.nightscout.core.dexcom.records.EGVRecord;
import com.nightscout.core.model.G4Noise;

import net.tribe7.common.base.Optional;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.List;

@Table(name = "sgvs")
public class SgvDbEntry extends Model {
    @Column(name = "systime", unique = true, onUniqueConflict = Column.ConflictAction.REPLACE)
    public long systime;

    @Column(name = "walltime")
    public long walltime;

    @Column(name = "sgv_mgdl")
    public int sgvMgdl;

    @Column(name = "trend")
    public int trend;

    @Column(name = "noise")
    public int noise;

    @Column(name = "transmitter_id")
    public DexcomTransmitterEntry transmitterId;

    @Column(name = "receiver_id")
    public DexcomReceiverEntry receiverId;

    public SgvDbEntry() {
        super();
    }

    public SgvDbEntry(EGVRecord sgvRecord, String receiverId, String transmitterId) {
        super();
        this.systime = sgvRecord.getSystemTime().getMillis();
        this.walltime = sgvRecord.getWallTime().getMillis();
        this.sgvMgdl = sgvRecord.getBgMgdl();
        this.trend = sgvRecord.getTrend().ordinal();
        this.noise = sgvRecord.getNoiseMode().ordinal();
        this.receiverId = new DexcomReceiverEntry(receiverId);
        this.transmitterId = new DexcomTransmitterEntry(transmitterId);
    }

    public static Optional<EGVRecord> getLastEgv() {
        SgvDbEntry sgvDbEntry = new Select().from(SgvDbEntry.class).orderBy("systime DESC").limit(1).executeSingle();
        EGVRecord record = null;
        if (sgvDbEntry != null) {
            DateTime unused1 = new DateTime(sgvDbEntry.systime);
            DateTime sysTime = new DateTime(sgvDbEntry.systime);
            DateTime wallTime = new DateTime(sgvDbEntry.walltime);
            record = new EGVRecord(sgvDbEntry.sgvMgdl, TrendArrow.values()[sgvDbEntry.trend], unused1, sysTime, G4Noise.values()[sgvDbEntry.noise], wallTime);
        }
        return Optional.fromNullable(record);
    }

    public static Optional<EGVRecord> getLastEgv(Duration ago) {
        SgvDbEntry sgvDbEntry = new Select().from(SgvDbEntry.class).where("walltime > ?", new DateTime().minus(ago)).orderBy("systime DESC").limit(1).executeSingle();
        EGVRecord record = null;
        if (sgvDbEntry != null) {
            DateTime unused1 = new DateTime(sgvDbEntry.systime);
            DateTime sysTime = new DateTime(sgvDbEntry.systime);
            DateTime wallTime = new DateTime(sgvDbEntry.walltime);
            record = new EGVRecord(sgvDbEntry.sgvMgdl, TrendArrow.values()[sgvDbEntry.trend], unused1, sysTime, G4Noise.values()[sgvDbEntry.noise], wallTime);
        }
//        return new EGVRecord(sgvDbEntry.sgvMgdl, TrendArrow.values()[sgvDbEntry.trend], unused1, sysTime, G4Noise.values()[sgvDbEntry.noise], wallTime);
        return Optional.fromNullable(record);
    }


    public static List<EGVRecord> getLastEgvRecords(DateTime since) {
        List<SgvDbEntry> sgvDbEntryList = new Select().from(SgvDbEntry.class).where("walltime > ?", since.getMillis()).orderBy("systime ASC").execute();
        List<EGVRecord> results = new ArrayList<>();
        for (SgvDbEntry sgvDbEntry : sgvDbEntryList) {
            DateTime unused1 = new DateTime(sgvDbEntry.systime);
            DateTime sysTime = new DateTime(sgvDbEntry.systime);
            DateTime wallTime = new DateTime(sgvDbEntry.walltime);
            results.add(new EGVRecord(sgvDbEntry.sgvMgdl, TrendArrow.values()[sgvDbEntry.trend], unused1, sysTime, G4Noise.values()[sgvDbEntry.noise], wallTime));
        }
        return results;
    }

}

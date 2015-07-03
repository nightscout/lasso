package org.nightscout.lasso.model;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.nightscout.core.dexcom.records.MeterRecord;

@Table(name = "mbgs")
public class MeterDbEntry extends Model {
    @Column(name = "systime", unique = true, onUniqueConflict = Column.ConflictAction.REPLACE)
    public long systime;

    @Column(name = "walltime")
    public long walltime;

    @Column(name = "mbg")
    public int mbg;

    @Column(name = "transmitter_id")
    public DexcomTransmitterEntry transmitterId;

    @Column(name = "receiver_id")
    public DexcomReceiverEntry receiverId;

    public MeterDbEntry() {
        super();
    }

    public MeterDbEntry(MeterRecord meterRecord, String receiverId, String transmitterId) {
        super();
        this.systime = meterRecord.getSystemTime().getMillis();
        this.walltime = meterRecord.getWallTime().getMillis();
        this.mbg = meterRecord.getBgMgdl();
        this.receiverId = new DexcomReceiverEntry(receiverId);
        this.transmitterId = new DexcomTransmitterEntry(transmitterId);
    }
}

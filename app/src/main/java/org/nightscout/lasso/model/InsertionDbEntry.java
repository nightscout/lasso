package org.nightscout.lasso.model;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.nightscout.core.dexcom.records.InsertionRecord;

@Table(name = "insertions")
public class InsertionDbEntry extends Model {
    @Column(name = "systime", unique = true, onUniqueConflict = Column.ConflictAction.REPLACE)
    public long systime;

    @Column(name = "walltime")
    public long walltime;

    @Column(name = "state")
    public int state;

    @Column(name = "transmitter_id")
    public DexcomTransmitterEntry transmitterId;

    @Column(name = "receiver_id")
    public DexcomReceiverEntry receiverId;

    public InsertionDbEntry() {
        super();
    }

    public InsertionDbEntry(InsertionRecord insertionRecord, String receiverId, String transmitterId) {
        super();
        this.systime = insertionRecord.getSystemTime().getMillis();
        this.walltime = insertionRecord.getWallTime().getMillis();
        this.state = insertionRecord.getState().getValue();
        this.receiverId = new DexcomReceiverEntry(receiverId);
        this.transmitterId = new DexcomTransmitterEntry(transmitterId);
    }
}

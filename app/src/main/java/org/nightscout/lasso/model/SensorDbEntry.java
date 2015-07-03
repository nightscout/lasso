package org.nightscout.lasso.model;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Select;
import com.nightscout.core.dexcom.records.SensorRecord;

import net.tribe7.common.base.Optional;

import org.joda.time.DateTime;

@Table(name = "SGVEntry")
public class SensorDbEntry extends Model {
    @Column(name = "systime", unique = true, onUniqueConflict = Column.ConflictAction.REPLACE)
    public long systime;

    @Column(name = "walltime")
    public long walltime;

    @Column(name = "filtered")
    public long filtered;

    @Column(name = "unfiltered")
    public long unfiltered;

    @Column(name = "rssi")
    public int rssi;

    @Column(name = "transmitter_id")
    public DexcomTransmitterEntry transmitterId;

    @Column(name = "receiver_id")
    public DexcomReceiverEntry receiverId;

    public SensorDbEntry() {
        super();
    }

    public SensorDbEntry(SensorRecord sensorRecord, String receiverId, String transmitterId) {
        super();
        this.systime = sensorRecord.getSystemTime().getMillis();
        this.walltime = sensorRecord.getWallTime().getMillis();
        this.filtered = sensorRecord.getFiltered();
        this.unfiltered = sensorRecord.getUnfiltered();
        this.rssi = sensorRecord.getRssi();
        this.receiverId = new DexcomReceiverEntry(receiverId);
        this.transmitterId = new DexcomTransmitterEntry(transmitterId);
    }

    public static Optional<SensorRecord> getLastSensor() {
        SensorDbEntry sensorDbEntry = new Select().from(SensorDbEntry.class).orderBy("systime DESC").limit(1).executeSingle();
        SensorRecord sensorRecord = null;
        if (sensorDbEntry != null) {
            sensorRecord = new SensorRecord(sensorDbEntry.filtered, sensorDbEntry.unfiltered, sensorDbEntry.rssi, new DateTime(sensorDbEntry.systime), new DateTime(sensorDbEntry.systime), new DateTime(sensorDbEntry.walltime));
        }
        return Optional.fromNullable(sensorRecord);
//        return new SensorRecord(sensorDbEntry.filtered, sensorDbEntry.unfiltered, sensorDbEntry.rssi, new DateTime(sensorDbEntry.systime), new DateTime(sensorDbEntry.systime), new DateTime(sensorDbEntry.walltime));
        //     public SensorRecord(int filtered, int unfiltered, int rssi, DateTime displayTime, DateTime systemTime, DateTime walltime) {

    }

}

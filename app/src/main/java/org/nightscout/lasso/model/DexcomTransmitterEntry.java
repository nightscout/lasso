package org.nightscout.lasso.model;

import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;

@Table(name = "Transmitters")
public class DexcomTransmitterEntry {
    @Column(name = "serial_number", unique = true, onUniqueConflict = Column.ConflictAction.IGNORE)
    public String transmitterId;

    public DexcomTransmitterEntry() {
        super();
    }

    public DexcomTransmitterEntry(String transmitterId) {
        super();
        this.transmitterId = transmitterId;
    }
}

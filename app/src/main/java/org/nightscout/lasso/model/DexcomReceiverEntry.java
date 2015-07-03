package org.nightscout.lasso.model;

import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;

@Table(name = "receivers")
public class DexcomReceiverEntry {
    @Column(name = "serial_number", unique = true, onUniqueConflict = Column.ConflictAction.IGNORE)
    public String receiverId;

    public DexcomReceiverEntry() {
        super();
    }

    public DexcomReceiverEntry(String receiverId) {
        super();
        this.receiverId = receiverId;
    }
}

package org.nightscout.lasso.model;

import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;

@Table(name = "battery")
public class BatteryDbEntry {
    @Column(name = "walltime")
    public long walltime;

}

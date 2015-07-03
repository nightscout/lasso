package org.nightscout.lasso.alarm.ar2;

import org.joda.time.DateTime;

public class BGPoint {
    public DateTime x;
    public Long y;

    public BGPoint(DateTime x, Long y) {
        this.x = x;
        this.y = y;
    }
}

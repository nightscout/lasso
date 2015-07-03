package com.nightscout.core.utils;

import com.nightscout.core.dexcom.records.CalRecord;
import com.nightscout.core.dexcom.records.EGVRecord;
import com.nightscout.core.dexcom.records.SensorRecord;
import com.nightscout.core.model.G4Noise;
import com.nightscout.core.model.GlucoseUnit;

public class IsigReading extends GlucoseReading {
    protected SensorRecord sensorRecord;
    protected CalRecord calRecord;
    protected EGVRecord egvRecord;

    public IsigReading(SensorRecord sensorRecord, CalRecord calRecord, EGVRecord egvRecord) {
        this.sensorRecord = sensorRecord;
        this.calRecord = calRecord;
        this.egvRecord = egvRecord;
        if (calRecord.getSlope() == 0 || calRecord.getScale() == 0 || sensorRecord.getUnfiltered() == 0) {
            this.valueMgdl = 0;
        } else if (sensorRecord.getFiltered() == 0 || egvRecord.getBgMgdl() < 40) {
            valueMgdl = (int) (calRecord.getScale() * (sensorRecord.getUnfiltered() - calRecord.getIntercept()) / calRecord.getSlope());
        } else {
            double ratio = calRecord.getScale() * (sensorRecord.getFiltered() - calRecord.getIntercept()) / calRecord.getSlope() / egvRecord.getBgMgdl();
            valueMgdl = (int) (calRecord.getScale() * (sensorRecord.getUnfiltered() - calRecord.getIntercept()) / calRecord.getSlope() / ratio);
        }
    }

    public IsigReading() {
        this.valueMgdl = 0;
    }

    protected IsigReading(int valueMgdl, GlucoseUnit unit) {
        super(valueMgdl, unit);
    }

    public G4Noise getNoise() {
        return egvRecord.getNoiseMode();
    }

}

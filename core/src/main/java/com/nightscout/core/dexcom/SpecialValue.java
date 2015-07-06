package com.nightscout.core.dexcom;

import com.nightscout.core.utils.GlucoseReading;

import net.tribe7.common.base.Optional;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public enum SpecialValue {
    NONE("??0", 0),
    SENSOR_NOT_ACTIVE("SN", 1),
    MINIMALLY_EGV_AB("??2", 2),
    NO_ANTENNA("NA", 3),
    SENSOR_OUT_OF_CAL("NC", 5),
    COUNTS_AB("CD", 6),
    ABSOLUTE_AB("AD", 9),
    POWER_AB("??", 10),
    RF_BAD_STATUS("RF", 12),
    HIGH("HIGH", Constants.MAX_EGV),
    LOW("LOW", Constants.MIN_EGV);


    private String name;
    private int val;

    SpecialValue(String s, int i) {
        name = s;
        val = i;
    }

    public static Optional<SpecialValue> getEGVSpecialValue(int val) {
        for (SpecialValue e : values()) {
            if (e.getValue() == val)
                return Optional.of(e);
        }
        return Optional.absent();
    }

    public static Optional<SpecialValue> getEGVSpecialValue(GlucoseReading reading) {
        return getEGVSpecialValue(reading.asMgdl());
    }

    public static boolean isSpecialValue(GlucoseReading reading) {
        return isSpecialValue(reading.asMgdl());
    }

    public static boolean isSpecialValue(int value) {
        return getEGVSpecialValue(value).isPresent();
    }

    public static Optional<String> getSpecialValueDescr(GlucoseReading reading) {
        return getSpecialValueDescr(reading.asMgdl());
    }

    public static Optional<String> getSpecialValueDescr(int val) {
        Optional<SpecialValue> specialValueOptional = getEGVSpecialValue(val);
        ResourceBundle messages = ResourceBundle.getBundle("MessagesBundle",
                Locale.getDefault());

        String result = null;
        if (specialValueOptional.isPresent()) {
            SpecialValue sv = specialValueOptional.get();
            try {
                result = messages.getString("special_value_" + sv.name().toLowerCase());
            } catch (MissingResourceException e) {
                e.printStackTrace();
            }
        }
        return Optional.fromNullable(result);
    }

    public int getValue() {
        return val;
    }

    public String toString() {
        return name;
    }

}

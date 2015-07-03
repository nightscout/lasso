package com.nightscout.core.preferences;

import com.nightscout.core.drivers.SupportedDevices;
import com.nightscout.core.model.GlucoseUnit;

import java.util.List;

public interface NightscoutPreferences {
    boolean isRestApiEnabled();

    void setRestApiEnabled(boolean restApiEnabled);

    List<String> getRestApiBaseUris();

    void setRestApiBaseUris(List<String> restApis);

    boolean isCalibrationUploadEnabled();

    void setCalibrationUploadEnabled(boolean calibrationUploadEnabled);

    boolean isSensorUploadEnabled();

    void setSensorUploadEnabled(boolean sensorUploadEnabled);

    boolean isRawEnabled();

    void setRawEnabled(boolean rawUploadEnabled);

    boolean isMongoUploadEnabled();

    void setMongoUploadEnabled(boolean mongoUploadEnabled);

    boolean isDataDonateEnabled();

    void setDataDonateEnabled(boolean toDonate);

    String getMongoClientUri();

    void setMongoClientUri(String client);

    String getMongoCollection();

    void setMongoCollection(String mongoCollection);

    String getMongoDeviceStatusCollection();

    void setMongoDeviceStatusCollection(String deviceStatusCollection);

    boolean isMqttEnabled();

    void setMqttUploadEnabled(boolean mqttEnabled);

    String getMqttEndpoint();

    void setMqttEndpoint(String endpoint);

    String getMqttUser();

    // TODO: (klee) look into how to securely store this information
    String getMqttPass();

    boolean getIUnderstand();

    void setIUnderstand(boolean bool);

    GlucoseUnit getPreferredUnits();

    void setPreferredUnits(GlucoseUnit units);

    String getPwdName();

    void setPwdName(String pwdName);

    boolean hasAskedForData();

    void setAskedForData(boolean askedForData);

    SupportedDevices getDeviceType();

    void setBluetoothDevice(String btDeviceName, String btAddress);

    String getBtAddress();

    String getShareSerial();

    void setShareSerial(String serialNumber);

    boolean isMeterUploadEnabled();

    void setMeterUploadEnabled(boolean enabled);

    boolean isInsertionUploadEnabled();

    void setInsertionUploadEnabled(boolean enabled);

    long getLastMeterSysTime();

    void setLastMeterSysTime(long meterSysTime);

    long getLastEgvSysTime();

    void setLastEgvSysTime(long egvSysTime);

    long getLastEgvMqttUpload();

    void setLastEgvMqttUpload(long timestamp);

    long getLastSensorMqttUpload();

    void setLastSensorMqttUpload(long timestamp);

    long getLastCalMqttUpload();

    void setLastCalMqttUpload(long timestamp);

    long getLastMeterMqttUpload();

    void setLastMeterMqttUpload(long timestamp);

    long getLastInsMqttUpload();

    void setLastInsMqttUpload(long timestamp);

    void setLastEgvBaseUpload(long timestamp, String postfix);

    void setLastSensorBaseUpload(long timestamp, String postfix);

    void setLastCalBaseUpload(long timestamp, String postfix);

    void setLastMeterBaseUpload(long timestamp, String postfix);

    void setLastInsBaseUpload(long timestamp, String postfix);

    long getLastEgvBaseUpload(String postfix);

    long getLastSensorBaseUpload(String postfix);

    long getLastCalBaseUpload(String postfix);

    long getLastMeterBaseUpload(String postfix);

    long getLastInsBaseUpload(String postfix);

    int getUrgentHighThreshold();

    int getWarningHighThreshold();

    int getWarningLowThreshold();

    int getUrgentLowThreshold();

    String getAlarmStrategy();
}

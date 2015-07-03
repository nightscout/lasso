package com.nightscout.core.mqtt;

public interface MqttPingerObserver {
    void onFailedPing();
}
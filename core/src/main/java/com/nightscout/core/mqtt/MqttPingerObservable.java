package com.nightscout.core.mqtt;

public interface MqttPingerObservable {
    void registerObserver(MqttPingerObserver observer);

    void unregisterObserver(MqttPingerObserver observer);
}

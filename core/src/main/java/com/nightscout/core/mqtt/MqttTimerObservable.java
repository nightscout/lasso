package com.nightscout.core.mqtt;


public interface MqttTimerObservable {
    void registerObserver(MqttTimerObserver observer);

    void unregisterObserver(MqttTimerObserver observer);
}

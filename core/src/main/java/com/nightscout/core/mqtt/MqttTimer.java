package com.nightscout.core.mqtt;

public interface MqttTimer {
    void setTimer(long delayMs);

    void cancel();

    void activate();

    void deactivate();

    boolean isActive();

    void registerObserver(MqttTimerObserver observer);

    void unregisterObserver(MqttTimerObserver observer);
}

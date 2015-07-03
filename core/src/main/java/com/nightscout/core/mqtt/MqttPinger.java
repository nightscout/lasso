package com.nightscout.core.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;

public interface MqttPinger extends MqttPingerObservable {
    void ping();

    void start();

    void stop();

    boolean isActive();

    void reset();

    boolean isNetworkActive();

    void setKeepAliveInterval(int ms);

    void setMqttClient(MqttClient mqttClient);
}

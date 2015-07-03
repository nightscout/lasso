package com.nightscout.core.mqtt;

import org.eclipse.paho.client.mqttv3.MqttMessage;

public interface MqttMgrObserver {
    void onMessage(String topic, MqttMessage message);

    void onDisconnect();

    void onConnect();
}
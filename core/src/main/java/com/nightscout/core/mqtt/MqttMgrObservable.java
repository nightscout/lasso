package com.nightscout.core.mqtt;

import org.eclipse.paho.client.mqttv3.MqttMessage;

public interface MqttMgrObservable {
    void registerObserver(MqttMgrObserver observer);

    void unregisterObserver(MqttMgrObserver observer);

    void notifyOnMessage(String topic, MqttMessage message);

    void notifyOnDisconnect();

    void notifyOnConnect();
}
package com.nightscout.core.mqtt;

import org.eclipse.paho.client.mqttv3.MqttMessage;

public interface MqttMgrObserver {
    /**
     * Actions that are taken when a new message comes in on a subscribed topic
     *
     * @param topic   name of the topic that the message came in on
     * @param message this is the message
     */
    void onMessage(String topic, MqttMessage message);

    /**
     * Actions that are taken when MQTT disconnects
     */
    void onDisconnect();

    /**
     * Actions that are taken when MQTT connects
     */
    void onConnect();
}
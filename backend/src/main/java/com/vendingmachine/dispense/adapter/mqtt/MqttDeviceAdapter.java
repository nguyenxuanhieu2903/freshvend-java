package com.vendingmachine.dispense.adapter.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.dispense.adapter.DeviceAdapter;
import com.vendingmachine.dispense.dto.DispenseCommandMessage;
import com.vendingmachine.dispense.dto.EventAckMessage;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production implementation of DeviceAdapter communicating with ESP32 devices via MQTT.
 */
@Component
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true", matchIfMissing = true)
public class MqttDeviceAdapter implements DeviceAdapter {

    private static final Logger log = LoggerFactory.getLogger(MqttDeviceAdapter.class);

    private final MqttClient mqttClient;
    private final MqttProperties mqttProperties;
    private final ObjectMapper objectMapper;
    private final Map<String, Long> lastHeartbeatMap = new ConcurrentHashMap<>();

    public MqttDeviceAdapter(MqttClient mqttClient, MqttProperties mqttProperties, ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.mqttProperties = mqttProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isDeviceOnline(String machineId) {
        Long lastHeartbeat = lastHeartbeatMap.get(machineId);
        if (lastHeartbeat == null) {
            return false;
        }
        // Heartbeat considered offline after 35 seconds (ESP32 sends every 15s)
        return (System.currentTimeMillis() - lastHeartbeat) < 35000;
    }

    @Override
    public void sendDispenseCommand(DispenseCommandMessage command) {
        try {
            String topic = mqttProperties.getTopics().getCommandsForMachine(command.getMachineId());
            String jsonPayload = objectMapper.writeValueAsString(command);

            MqttMessage message = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));
            message.setQos(mqttProperties.getQos());
            message.setRetained(false); // Retained is strictly forbidden for commands

            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.publish(topic, message);
                log.info("Dispatched DISPENSE command [{}] to topic [{}]", command.getCommandId(), topic);
            } else {
                log.warn("MQTT client not connected, command [{}] buffered or failed", command.getCommandId());
            }
        } catch (Exception e) {
            log.error("Failed to send dispense command via MQTT: {}", e.getMessage(), e);
            throw new RuntimeException("MQTT dispense dispatch failed", e);
        }
    }

    @Override
    public void sendEventAck(EventAckMessage ack) {
        try {
            String topic = mqttProperties.getTopics().getAckForMachine(ack.getMachineId());
            String jsonPayload = objectMapper.writeValueAsString(ack);

            MqttMessage message = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));
            message.setQos(mqttProperties.getQos());
            message.setRetained(false);

            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.publish(topic, message);
                log.info("Dispatched ACK [{}] to topic [{}]", ack.getEventId(), topic);
            }
        } catch (Exception e) {
            log.error("Failed to send event ACK: {}", e.getMessage(), e);
        }
    }

    @Override
    public String getAdapterType() {
        return "MQTT";
    }

    public void updateDeviceHeartbeat(String machineId) {
        lastHeartbeatMap.put(machineId, System.currentTimeMillis());
    }
}

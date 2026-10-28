package com.vendingmachine.dispense.adapter.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.device.entity.DeviceEvent;
import com.vendingmachine.device.repository.DeviceEventRepository;
import com.vendingmachine.dispense.adapter.DeviceEventListener;
import com.vendingmachine.dispense.dto.DeviceEventMessage;
import jakarta.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Inbound MQTT message subscriber for ESP32 hardware events:
 * 1. Dispense results, command acks, and error signals (vending/{machineId}/events)
 * 2. Periodic device heartbeat (vending/{machineId}/heartbeat)
 */
@Component
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true", matchIfMissing = true)
public class MqttEventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(MqttEventSubscriber.class);

    private final MqttClient mqttClient;
    private final MqttProperties mqttProperties;
    private final MqttDeviceAdapter mqttDeviceAdapter;
    private final DeviceEventRepository deviceEventRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private DeviceEventListener deviceEventListener;

    public MqttEventSubscriber(
            MqttClient mqttClient,
            MqttProperties mqttProperties,
            MqttDeviceAdapter mqttDeviceAdapter,
            DeviceEventRepository deviceEventRepository,
            ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.mqttProperties = mqttProperties;
        this.mqttDeviceAdapter = mqttDeviceAdapter;
        this.deviceEventRepository = deviceEventRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void subscribeToTopics() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                String eventsTopic = mqttProperties.getTopics().getEvents().replace("{machineId}", "+");
                String heartbeatTopic = mqttProperties.getTopics().getHeartbeat().replace("{machineId}", "+");

                mqttClient.subscribe(eventsTopic, mqttProperties.getQos(), this::handleDeviceEvent);
                mqttClient.subscribe(heartbeatTopic, 0, this::handleHeartbeat);

                log.info("Subscribed to MQTT topics: [{}] and [{}]", eventsTopic, heartbeatTopic);
            }
        } catch (MqttException e) {
            log.error("Failed to subscribe to MQTT topics: {}", e.getMessage(), e);
        }
    }

    private void handleDeviceEvent(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            log.info("Received MQTT event on [{}]: {}", topic, payload);

            DeviceEventMessage eventMessage = objectMapper.readValue(payload, DeviceEventMessage.class);

            // Persist raw audit record in device_events table
            persistDeviceEvent(eventMessage, payload);

            // Forward event to core dispense business logic listener
            if (deviceEventListener != null) {
                deviceEventListener.onDeviceEvent(eventMessage);
            }
        } catch (Exception e) {
            log.error("Error processing MQTT device event from topic [{}]: {}", topic, e.getMessage(), e);
        }
    }

    private void handleHeartbeat(String topic, MqttMessage message) {
        try {
            // Extract machineId from topic "vending/{machineId}/heartbeat"
            String[] parts = topic.split("/");
            if (parts.length >= 2) {
                String machineId = parts[1];
                mqttDeviceAdapter.updateDeviceHeartbeat(machineId);
                log.debug("Heartbeat received from machine [{}]", machineId);
            }
        } catch (Exception e) {
            log.error("Error processing heartbeat on topic [{}]: {}", topic, e.getMessage());
        }
    }

    private void persistDeviceEvent(DeviceEventMessage event, String rawPayload) {
        try {
            DeviceEvent entity = new DeviceEvent();
            entity.setEventId(event.getEventId());
            entity.setCommandId(event.getCommandId());
            entity.setEventType(event.getEventType() != null ? event.getEventType() : "UNKNOWN");
            entity.setSlotNumber(event.getSlotNumber());
            entity.setPayload(rawPayload);
            deviceEventRepository.save(entity);
        } catch (Exception e) {
            log.warn("Could not persist DeviceEvent to DB: {}", e.getMessage());
        }
    }
}

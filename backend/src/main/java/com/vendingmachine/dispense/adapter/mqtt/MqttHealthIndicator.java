package com.vendingmachine.dispense.adapter.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Health indicator for MQTT Broker connection monitoring in Spring Boot Actuator.
 */
@Component
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true", matchIfMissing = true)
public class MqttHealthIndicator implements HealthIndicator {

    private final MqttClient mqttClient;
    private final MqttProperties mqttProperties;

    public MqttHealthIndicator(MqttClient mqttClient, MqttProperties mqttProperties) {
        this.mqttClient = mqttClient;
        this.mqttProperties = mqttProperties;
    }

    @Override
    public Health health() {
        if (mqttClient != null && mqttClient.isConnected()) {
            return Health.up()
                    .withDetail("broker", mqttProperties.getBrokerUrl())
                    .withDetail("clientId", mqttProperties.getClientId())
                    .withDetail("status", "CONNECTED")
                    .build();
        }
        return Health.down()
                .withDetail("broker", mqttProperties.getBrokerUrl())
                .withDetail("status", "DISCONNECTED")
                .build();
    }
}

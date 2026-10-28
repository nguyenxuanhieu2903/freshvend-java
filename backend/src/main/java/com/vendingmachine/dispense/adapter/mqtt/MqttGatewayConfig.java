package com.vendingmachine.dispense.adapter.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration creating TLS connection bean for MQTT Broker communication.
 */
@Configuration
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true", matchIfMissing = true)
public class MqttGatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttGatewayConfig.class);

    @Bean
    public MqttConnectOptions mqttConnectOptions(MqttProperties properties) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false); // Persistent session for Backend to not lose QoS 1 events
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);

        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            options.setUserName(properties.getUsername());
        }
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            options.setPassword(properties.getPassword().toCharArray());
        }

        return options;
    }

    @Bean(destroyMethod = "disconnect")
    public MqttClient mqttClient(MqttProperties properties, MqttConnectOptions connectOptions) {
        try {
            log.info("Initializing MQTT client connecting to: {}", properties.getBrokerUrl());
            MqttClient client = new MqttClient(
                    properties.getBrokerUrl(),
                    properties.getClientId(),
                    new MemoryPersistence()
            );
            return client;
        } catch (MqttException e) {
            log.error("Failed to initialize MQTT Client: {}", e.getMessage());
            throw new IllegalStateException("Cannot create MQTT client bean", e);
        }
    }
}

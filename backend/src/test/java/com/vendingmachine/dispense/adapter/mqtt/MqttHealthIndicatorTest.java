package com.vendingmachine.dispense.adapter.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class MqttHealthIndicatorTest {

    private MqttClient mockMqttClient;
    private MqttProperties mqttProperties;
    private MqttHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        mockMqttClient = Mockito.mock(MqttClient.class);
        mqttProperties = new MqttProperties();
        healthIndicator = new MqttHealthIndicator(mockMqttClient, mqttProperties);
    }

    @Test
    @DisplayName("Should report UP status when MQTT client is connected")
    void testHealth_WhenConnected() {
        when(mockMqttClient.isConnected()).thenReturn(true);

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("CONNECTED", health.getDetails().get("status"));
    }

    @Test
    @DisplayName("Should report DOWN status when MQTT client is disconnected")
    void testHealth_WhenDisconnected() {
        when(mockMqttClient.isConnected()).thenReturn(false);

        Health health = healthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("DISCONNECTED", health.getDetails().get("status"));
    }
}

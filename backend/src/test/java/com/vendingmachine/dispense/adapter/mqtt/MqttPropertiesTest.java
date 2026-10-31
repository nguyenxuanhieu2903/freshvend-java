package com.vendingmachine.dispense.adapter.mqtt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MqttPropertiesTest {

    private MqttProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MqttProperties();
    }

    @Test
    @DisplayName("Should have valid default configuration properties")
    void testDefaultValues() {
        assertTrue(properties.isEnabled());
        assertEquals("ssl://broker.emqx.io:8883", properties.getBrokerUrl());
        assertEquals("freshvend-backend-vm001", properties.getClientId());
        assertEquals("backend-vm001", properties.getUsername());
        assertEquals("", properties.getPassword());
        assertEquals(1, properties.getQos());
        assertEquals(5000, properties.getCompletionTimeoutMs());
        assertNotNull(properties.getTopics());
    }

    @Test
    @DisplayName("Should correctly format topic strings for a specific machine ID")
    void testTopicResolutionForMachine() {
        String machineId = "VM001";
        MqttProperties.Topics topics = properties.getTopics();

        assertEquals("vending/VM001/commands", topics.getCommandsForMachine(machineId));
        assertEquals("vending/VM001/events", topics.getEventsForMachine(machineId));
        assertEquals("vending/VM001/events/ack", topics.getAckForMachine(machineId));
        assertEquals("vending/VM001/heartbeat", topics.getHeartbeatForMachine(machineId));
    }

    @Test
    @DisplayName("Should allow custom property values via setters")
    void testCustomValues() {
        properties.setEnabled(false);
        properties.setBrokerUrl("tcp://localhost:1883");
        properties.setClientId("custom-client-id");
        properties.setUsername("admin");
        properties.setPassword("secret123");
        properties.setQos(0);
        properties.setCompletionTimeoutMs(10000);

        assertFalse(properties.isEnabled());
        assertEquals("tcp://localhost:1883", properties.getBrokerUrl());
        assertEquals("custom-client-id", properties.getClientId());
        assertEquals("admin", properties.getUsername());
        assertEquals("secret123", properties.getPassword());
        assertEquals(0, properties.getQos());
        assertEquals(10000, properties.getCompletionTimeoutMs());
    }
}

package com.vendingmachine.dispense.adapter.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.dispense.dto.DispenseCommandMessage;
import com.vendingmachine.dispense.dto.EventAckMessage;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MqttDeviceAdapterTest {

    private MqttClient mockMqttClient;
    private MqttProperties mqttProperties;
    private ObjectMapper objectMapper;
    private MqttDeviceAdapter adapter;

    @BeforeEach
    void setUp() {
        mockMqttClient = Mockito.mock(MqttClient.class);
        mqttProperties = new MqttProperties();
        objectMapper = new ObjectMapper();
        adapter = new MqttDeviceAdapter(mockMqttClient, mqttProperties, objectMapper);
    }

    @Test
    @DisplayName("Should return MQTT adapter type")
    void testGetAdapterType() {
        assertEquals("MQTT", adapter.getAdapterType());
    }

    @Test
    @DisplayName("Device should be offline when no heartbeat received")
    void testIsDeviceOnline_WhenNoHeartbeat() {
        assertFalse(adapter.isDeviceOnline("VM001"));
    }

    @Test
    @DisplayName("Device should be online after recent heartbeat")
    void testIsDeviceOnline_WhenRecentHeartbeat() {
        adapter.updateDeviceHeartbeat("VM001");
        assertTrue(adapter.isDeviceOnline("VM001"));
    }

    @Test
    @DisplayName("Should dispatch dispense command when client is connected")
    void testSendDispenseCommand_Success() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(true);

        DispenseCommandMessage command = new DispenseCommandMessage();
        command.setCommandId(UUID.randomUUID());
        command.setMachineId("VM001");
        command.setOrderId(UUID.randomUUID());
        command.setSlotNumber(2);
        command.setAction("DISPENSE");
        command.setCreatedAt(Instant.now());
        command.setExpiresAt(Instant.now().plusSeconds(60));

        assertDoesNotThrow(() -> adapter.sendDispenseCommand(command));
        verify(mockMqttClient, times(1)).publish(eq("vending/VM001/commands"), any());
    }

    @Test
    @DisplayName("Should dispatch event ACK when client is connected")
    void testSendEventAck_Success() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(true);

        EventAckMessage ack = new EventAckMessage(UUID.randomUUID(), "VM001", Instant.now());
        assertDoesNotThrow(() -> adapter.sendEventAck(ack));
        verify(mockMqttClient, times(1)).publish(eq("vending/VM001/events/ack"), any());
    }
}

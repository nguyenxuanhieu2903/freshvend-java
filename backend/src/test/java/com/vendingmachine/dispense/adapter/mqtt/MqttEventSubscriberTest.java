package com.vendingmachine.dispense.adapter.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.device.repository.DeviceEventRepository;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class MqttEventSubscriberTest {

    private MqttClient mockMqttClient;
    private MqttProperties mqttProperties;
    private MqttDeviceAdapter mockMqttDeviceAdapter;
    private DeviceEventRepository mockDeviceEventRepository;
    private ObjectMapper objectMapper;
    private MqttEventSubscriber subscriber;

    @BeforeEach
    void setUp() {
        mockMqttClient = Mockito.mock(MqttClient.class);
        mqttProperties = new MqttProperties();
        mockMqttDeviceAdapter = Mockito.mock(MqttDeviceAdapter.class);
        mockDeviceEventRepository = Mockito.mock(DeviceEventRepository.class);
        objectMapper = new ObjectMapper();

        subscriber = new MqttEventSubscriber(
                mockMqttClient,
                mqttProperties,
                mockMqttDeviceAdapter,
                mockDeviceEventRepository,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should subscribe to events and heartbeat topics on startup when connected")
    void testSubscribeToTopics_WhenConnected() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(true);

        assertDoesNotThrow(() -> subscriber.subscribeToTopics());

        verify(mockMqttClient, times(1)).subscribe(eq("vending/+/events"), eq(1), any());
        verify(mockMqttClient, times(1)).subscribe(eq("vending/+/heartbeat"), eq(0), any());
    }

    @Test
    @DisplayName("Should not throw exception when MQTT client is disconnected during subscription")
    void testSubscribeToTopics_WhenDisconnected() {
        when(mockMqttClient.isConnected()).thenReturn(false);

        assertDoesNotThrow(() -> subscriber.subscribeToTopics());
        verifyNoInteractions(mockDeviceEventRepository);
    }
}

package com.vendingmachine.dispense.adapter.mqtt;

import com.vendingmachine.dispense.dto.DeviceEventMessage;
import com.vendingmachine.dispense.dto.DispenseCommandMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MqttPayloadValidatorTest {

    private MqttPayloadValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MqttPayloadValidator();
    }

    @Test
    @DisplayName("Valid outbound command should pass validation")
    void testValidateOutboundCommand_Valid() {
        DispenseCommandMessage command = new DispenseCommandMessage();
        command.setCommandId(UUID.randomUUID());
        command.setMachineId("VM001");
        command.setSlotNumber(2);

        assertTrue(validator.validateOutboundCommand(command));
    }

    @Test
    @DisplayName("Outbound command with null commandId should fail validation")
    void testValidateOutboundCommand_NullCommandId() {
        DispenseCommandMessage command = new DispenseCommandMessage();
        command.setMachineId("VM001");
        command.setSlotNumber(1);

        assertFalse(validator.validateOutboundCommand(command));
    }

    @Test
    @DisplayName("Outbound command with invalid slot number should fail validation")
    void testValidateOutboundCommand_InvalidSlot() {
        DispenseCommandMessage command = new DispenseCommandMessage();
        command.setCommandId(UUID.randomUUID());
        command.setMachineId("VM001");
        command.setSlotNumber(5); // Only slots 1-3 allowed

        assertFalse(validator.validateOutboundCommand(command));
    }

    @Test
    @DisplayName("Valid inbound event should pass validation")
    void testValidateInboundEvent_Valid() {
        DeviceEventMessage event = new DeviceEventMessage();
        event.setEventId(UUID.randomUUID());
        event.setEventType("DISPENSE_RESULT");
        event.setTimestamp(Instant.now());

        assertTrue(validator.validateInboundEvent(event));
    }

    @Test
    @DisplayName("Inbound event with missing eventId should fail validation")
    void testValidateInboundEvent_NullEventId() {
        DeviceEventMessage event = new DeviceEventMessage();
        event.setEventType("DISPENSE_RESULT");

        assertFalse(validator.validateInboundEvent(event));
    }
}

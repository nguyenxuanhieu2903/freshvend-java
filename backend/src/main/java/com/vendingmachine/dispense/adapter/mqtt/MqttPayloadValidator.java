package com.vendingmachine.dispense.adapter.mqtt;

import com.vendingmachine.dispense.dto.DeviceEventMessage;
import com.vendingmachine.dispense.dto.DispenseCommandMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Validates outbound commands and inbound events against the MQTT contract specifications.
 */
@Component
public class MqttPayloadValidator {

    private static final Logger log = LoggerFactory.getLogger(MqttPayloadValidator.class);

    /**
     * Validates an outgoing dispense command before publishing to ESP32.
     */
    public boolean validateOutboundCommand(DispenseCommandMessage command) {
        if (command == null) {
            log.warn("Dispense command is null");
            return false;
        }
        if (command.getCommandId() == null) {
            log.warn("Command rejected: commandId is missing");
            return false;
        }
        if (command.getMachineId() == null || command.getMachineId().isBlank()) {
            log.warn("Command rejected: machineId is missing");
            return false;
        }
        if (command.getSlotNumber() == null || command.getSlotNumber() < 1 || command.getSlotNumber() > 3) {
            log.warn("Command rejected: slotNumber must be between 1 and 3, got: {}", command.getSlotNumber());
            return false;
        }
        return true;
    }

    /**
     * Validates an inbound device event received from ESP32.
     */
    public boolean validateInboundEvent(DeviceEventMessage event) {
        if (event == null) {
            log.warn("Inbound device event is null");
            return false;
        }
        if (event.getEventId() == null) {
            log.warn("Inbound event rejected: eventId is missing");
            return false;
        }
        if (event.getEventType() == null || event.getEventType().isBlank()) {
            log.warn("Inbound event rejected: eventType is missing");
            return false;
        }
        if (event.getTimestamp() != null && event.getTimestamp().isAfter(Instant.now().plusSeconds(300))) {
            log.warn("Inbound event rejected: timestamp is in the future");
            return false;
        }
        return true;
    }
}

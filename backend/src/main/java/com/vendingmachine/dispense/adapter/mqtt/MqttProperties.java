package com.vendingmachine.dispense.adapter.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for MQTT communication with ESP32 device gateway.
 */
@Component
@ConfigurationProperties(prefix = "app.mqtt")
public class MqttProperties {

    private boolean enabled = true;
    private String brokerUrl = "ssl://broker.emqx.io:8883";
    private String clientId = "freshvend-backend-vm001";
    private String username = "backend-vm001";
    private String password = "";
    private int qos = 1;
    private int completionTimeoutMs = 5000;
    private Topics topics = new Topics();

    public static class Topics {
        private String commands = "vending/{machineId}/commands";
        private String events = "vending/{machineId}/events";
        private String ack = "vending/{machineId}/events/ack";
        private String heartbeat = "vending/{machineId}/heartbeat";

        public String getCommands() { return commands; }
        public void setCommands(String commands) { this.commands = commands; }

        public String getEvents() { return events; }
        public void setEvents(String events) { this.events = events; }

        public String getAck() { return ack; }
        public void setAck(String ack) { this.ack = ack; }

        public String getHeartbeat() { return heartbeat; }
        public void setHeartbeat(String heartbeat) { this.heartbeat = heartbeat; }

        public String getCommandsForMachine(String machineId) {
            return commands.replace("{machineId}", machineId);
        }

        public String getEventsForMachine(String machineId) {
            return events.replace("{machineId}", machineId);
        }

        public String getAckForMachine(String machineId) {
            return ack.replace("{machineId}", machineId);
        }

        public String getHeartbeatForMachine(String machineId) {
            return heartbeat.replace("{machineId}", machineId);
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getBrokerUrl() { return brokerUrl; }
    public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getQos() { return qos; }
    public void setQos(int qos) { this.qos = qos; }

    public int getCompletionTimeoutMs() { return completionTimeoutMs; }
    public void setCompletionTimeoutMs(int completionTimeoutMs) { this.completionTimeoutMs = completionTimeoutMs; }

    public Topics getTopics() { return topics; }
    public void setTopics(Topics topics) { this.topics = topics; }
}

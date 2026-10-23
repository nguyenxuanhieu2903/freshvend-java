# MQTT contract

Broker URL is configured with `MQTT_BROKER_URL`.

Planned topic conventions:

- `vending/{machineId}/telemetry` — device status and inventory updates.
- `vending/{machineId}/commands` — commands sent to a device.
- `vending/{machineId}/events` — device acknowledgements and events.

Payloads are UTF-8 JSON and should include a message ID and event timestamp.

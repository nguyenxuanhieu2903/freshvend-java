#!/usr/bin/env python3
"""
Test ESP32 Physical Hardware via MQTT over TLS
File: scripts/test_esp32_dispense.py
"""

import json
import ssl
import sys
import time
import uuid
from datetime import datetime, timezone
import paho.mqtt.client as mqtt

BROKER_HOST = "f18d138e.ala.asia-southeast1.emqxsl.com"
BROKER_PORT = 8883
USERNAME    = "esp32_vm001"
PASSWORD    = "abcxyz123"
MACHINE_ID  = "VM001"

TOPIC_COMMANDS  = f"vending/{MACHINE_ID}/commands"
TOPIC_EVENTS    = f"vending/{MACHINE_ID}/events"
TOPIC_HEARTBEAT = f"vending/{MACHINE_ID}/heartbeat"

def on_connect(client, userdata, flags, rc, properties=None):
    if rc == 0:
        print("\n[OK] KET NOI THANH CONG TOI MQTT BROKER (EMQX Cloud)!")
        client.subscribe(TOPIC_EVENTS, qos=1)
        client.subscribe(TOPIC_HEARTBEAT, qos=0)
        print(f"[SUB] Da lang nghe topic:")
        print(f"      - {TOPIC_EVENTS}")
        print(f"      - {TOPIC_HEARTBEAT}")
    else:
        print(f"[LOI] Ket noi that bai, ma loi: {rc}")

def on_message(client, userdata, msg):
    try:
        data = json.loads(msg.payload.decode("utf-8"))
        if msg.topic == TOPIC_HEARTBEAT:
            print(f"\n[💓 HEARTBEAT TU ESP32] Uptime: {data.get('uptimeSeconds')}s | RSSI: {data.get('rssi')} dBm")
        elif msg.topic == TOPIC_EVENTS:
            event_type = data.get("eventType")
            if event_type == "COMMAND_ACCEPTED":
                print(f"\n[✅ ESP32 XAC NHAN] Da chap nhan lenh! Bat dau quay servo Khay {data.get('slotNumber')}...")
            elif event_type == "DISPENSE_RESULT":
                res = data.get("result")
                print(f"\n[🎉 ESP32 HOAN THANH] Ket qua nha hang: {res} (Khay {data.get('slotNumber')})!")
                print("---------------------------------------------------------------")
            elif event_type == "COMMAND_REJECTED":
                print(f"\n[❌ ESP32 TU CHOI] Ly do: {data.get('rejectionReason')}")
    except Exception as e:
        print(f"[RECV] {msg.topic}: {msg.payload.decode('utf-8')}")

def main():
    print("===============================================================")
    print("  FRESHVEND - TEST XUAT HANG THUC TE TREN ESP32 QUA CLOUD MQTT ")
    print("===============================================================")

    slot = 1
    if len(sys.argv) > 1:
        slot = int(sys.argv[1])
    else:
        try:
            val = input("Chon khay ban muon test nha hang (1, 2 hoac 3) [Mac dinh 1]: ").strip()
            if val:
                slot = int(val)
        except Exception:
            slot = 1

    if slot not in [1, 2, 3]:
        print("So khay phai la 1, 2 hoac 3!")
        return

    client = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"test-script-{uuid.uuid4().hex[:8]}",
        clean_session=True
    )
    client.username_pw_set(USERNAME, PASSWORD)
    client.tls_set(cert_reqs=ssl.CERT_NONE)
    client.tls_insecure_set(True)

    client.on_connect = on_connect
    client.on_message = on_message

    print(f"\n[CONNECT] Dang ket noi toi {BROKER_HOST}:{BROKER_PORT}...")
    client.connect(BROKER_HOST, BROKER_PORT, keepalive=60)
    client.loop_start()

    time.sleep(2)

    cmd_id = str(uuid.uuid4())
    order_id = str(uuid.uuid4())
    now_iso = datetime.now(timezone.utc).isoformat()

    command_payload = {
        "commandId": cmd_id,
        "machineId": MACHINE_ID,
        "orderId": order_id,
        "slotNumber": slot,
        "action": "DISPENSE",
        "createdAt": now_iso,
        "expiresAt": now_iso,
        "parameters": {
            "motorRunMs": 3000
        }
    }

    payload_str = json.dumps(command_payload)
    print(f"\n[SEND -> COMMAND] Ban lenh nha hang cho Khay {slot} (3000ms)...")
    client.publish(TOPIC_COMMANDS, payload_str, qos=1)

    print("[WAIT] Dang cho phan hoi tu ESP32...")
    # Cho doi ket qua trong 8 giay
    for _ in range(8):
        time.sleep(1)

    client.loop_stop()
    client.disconnect()
    print("\nKet thuc phien test.")

if __name__ == "__main__":
    main()

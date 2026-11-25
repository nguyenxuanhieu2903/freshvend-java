#!/usr/bin/env python3
"""
Kiem tra trang thai ESP32 qua Cloud MQTT Heartbeat
File: scripts/check_esp32_online.py
"""

import json
import ssl
import sys
import time
import uuid
import paho.mqtt.client as mqtt

BROKER_HOST = "f18d138e.ala.asia-southeast1.emqxsl.com"
BROKER_PORT = 8883
USERNAME    = "esp32_vm001"
PASSWORD    = "abcxyz123"
MACHINE_ID  = "VM001"
TOPIC_HEARTBEAT = f"vending/{MACHINE_ID}/heartbeat"

found_heartbeat = False

def on_connect(client, userdata, flags, rc, properties=None):
    if rc == 0:
        client.subscribe(TOPIC_HEARTBEAT, qos=0)

def on_message(client, userdata, msg):
    global found_heartbeat
    try:
        data = json.loads(msg.payload.decode("utf-8"))
        if msg.topic == TOPIC_HEARTBEAT:
            found_heartbeat = True
            print("\n========================================================")
            print("  [✅ ESP32 DA KET NOI & DANG ONLINE TREN CLOUD!]       ")
            print("========================================================")
            print(f"  - Machine ID   : {data.get('machineId')}")
            print(f"  - Trang thai   : {data.get('status')}")
            print(f"  - Uptime (chay): {data.get('uptimeSeconds')} giay")
            print(f"  - Song Wi-Fi   : {data.get('rssi')} dBm (Rat khoe)")
            print(f"  - Firmware     : {data.get('firmwareVersion')}")
            print(f"  - Gio UTC      : {data.get('timestamp')}")
            print("========================================================")
    except Exception:
        pass

def main():
    print("Dang kiem tra tin hieu Heartbeat tu ESP32 tren Cloud...")
    client = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"chk-{uuid.uuid4().hex[:6]}",
        clean_session=True
    )
    client.username_pw_set(USERNAME, PASSWORD)
    client.tls_set(cert_reqs=ssl.CERT_NONE)
    client.tls_insecure_set(True)

    client.on_connect = on_connect
    client.on_message = on_message

    try:
        client.connect(BROKER_HOST, BROKER_PORT, keepalive=60)
        client.loop_start()

        # Cho toi da 35 giay (vi Heartbeat gui moi 30s)
        for i in range(35):
            if found_heartbeat:
                break
            time.sleep(1)
            sys.stdout.write(".")
            sys.stdout.flush()

        client.loop_stop()
        client.disconnect()

        if not found_heartbeat:
            print("\n\n[⚠️ CHUA THAY ESP32]")
            print("ESP32 chua gui nhip tim Heartbeat nao trong 35s qua.")
            print("Hay kiem tra: 1. Da bat 4G Hotspot chua? 2. Da cam nguon 5V cho ESP32 chua?")
    except Exception as e:
        print(f"\nLoi ket noi MQTT: {e}")

if __name__ == "__main__":
    main()

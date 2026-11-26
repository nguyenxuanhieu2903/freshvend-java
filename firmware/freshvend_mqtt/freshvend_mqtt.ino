#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <ESP32Servo.h>
#include <Preferences.h>
#include <time.h>
#include <esp_wifi.h>

// ================================================================
// FRESHVEND - FIRMWARE ESP32-C3 SUPER MINI (MQTT OVER TLS)
// Tuân thủ 100% chuẩn hợp đồng docs/MQTT_CONTRACT.md
// ================================================================

// 1. Cấu hình định danh máy bán hàng
#define MACHINE_ID         "VM001"
#define FIRMWARE_VERSION   "1.0.0"

// 2. Cấu hình chân điều khiển Servo
#define SERVO1_PIN         4   // Khay 1 (Nước suối)
#define SERVO2_PIN         5   // Khay 2 (Bim bim)
#define SERVO3_PIN         6   // Khay 3 (Bánh / kẹo)

#define SERVO_STOP         90  // Dừng
#define SERVO_FWD          180 // Đẩy hàng
#define DEFAULT_RUN_MS     3000 // 3.0 giây cho 1 vòng nhả hàng

// 3. Cấu hình MQTT Broker (EMQX Cloud - TLS Port 8883)
#define MQTT_BROKER        "f18d138e.ala.asia-southeast1.emqxsl.com"
#define MQTT_PORT          8883
#define MQTT_CLIENT_ID     "esp32-vm001-hardware"
#define MQTT_USERNAME      "esp32_vm001"
#define MQTT_PASSWORD      "abcxyz123"

// Topics theo chuẩn hợp đồng MQTT_CONTRACT.md
#define TOPIC_COMMANDS     "vending/VM001/commands"
#define TOPIC_EVENTS       "vending/VM001/events"
#define TOPIC_HEARTBEAT    "vending/VM001/heartbeat"

// 4. Cấu hình mạng Wi-Fi 4G Hotspot điện thoại cố định
#define WIFI_SSID          "Quoc Vinh"
#define WIFI_PASS          "19122006"

// ================================================================
// ĐỐI TƯỢNG VÀ TRẠNG THÁI HỆ THỐNG
// ================================================================
Servo servo1;
Servo servo2;
Servo servo3;
Servo* servos[3] = { &servo1, &servo2, &servo3 };

WiFiClientSecure secureClient;
PubSubClient mqttClient(secureClient);
Preferences preferences;

// Trạng thái thực thi lệnh
bool isExecuting = false;
int executingSlot = -1;
unsigned long motorStopTime = 0;
String currentCommandId = "";
String currentOrderId = "";

// Định kỳ gửi Heartbeat (mỗi 30s)
unsigned long lastHeartbeatTime = 0;
const unsigned long HEARTBEAT_INTERVAL_MS = 30000;

// ================================================================
// HÀM TIỆN ÍCH: UUID V4 & ISO-8601 TIMESTAMP
// ================================================================

// Sinh mã Event ID chuẩn UUID v4 ngẫu nhiên
String generateUUID() {
  uint32_t r0 = esp_random();
  uint32_t r1 = esp_random();
  uint32_t r2 = esp_random();
  uint32_t r3 = esp_random();

  r1 = (r1 & 0xFFFF0FFF) | 0x00004000; // version 4
  r2 = (r2 & 0x3FFFFFFF) | 0x80000000; // variant RFC 4122

  char buf[37];
  snprintf(buf, sizeof(buf), "%08x-%04x-%04x-%04x-%04x%08x",
           r0, (uint16_t)(r1 >> 16), (uint16_t)r1,
           (uint16_t)(r2 >> 16), (uint16_t)r2, r3);
  return String(buf);
}

// Lấy thời gian UTC chuẩn ISO-8601: 2026-11-23T01:30:00.000Z
String getISO8601Timestamp() {
  time_t now = time(nullptr);
  struct tm timeinfo;
  gmtime_r(&now, &timeinfo);
  char buf[30];
  strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%S.000Z", &timeinfo);
  return String(buf);
}

// ================================================================
// ĐIỀU KHIỂN MOTOR SERVO
// ================================================================
void stopAllServos() {
  for (int i = 0; i < 3; i++) {
    servos[i]->write(SERVO_STOP);
  }
}

void startMotor(int slot) {
  if (slot < 1 || slot > 3) return;
  servos[slot - 1]->write(SERVO_FWD);
  Serial.printf("[MOTOR] Slot %d bat dau quay TIEN (180 deg)\n", slot);
}

void stopMotor(int slot) {
  if (slot < 1 || slot > 3) return;
  servos[slot - 1]->write(SERVO_STOP);
  Serial.printf("[MOTOR] Slot %d da DUNG (90 deg)\n", slot);
}

// ================================================================
// GỬI CÁC SỰ KIỆN MQTT THEO CHUẨN MQTT_CONTRACT.md
// ================================================================

// 1. Phát sự kiện xác nhận nhận lệnh hợp lệ (COMMAND_ACCEPTED)
void publishCommandAccepted(const String& cmdId, const String& orderId, int slot) {
  #if ARDUINOJSON_VERSION_MAJOR >= 7
    JsonDocument doc;
  #else
    StaticJsonDocument<512> doc;
  #endif

  doc["eventId"]    = generateUUID();
  doc["commandId"]  = cmdId;
  doc["machineId"]  = MACHINE_ID;
  doc["orderId"]    = orderId;
  doc["eventType"]  = "COMMAND_ACCEPTED";
  doc["slotNumber"] = slot;
  doc["timestamp"]  = getISO8601Timestamp();

  char payload[512];
  serializeJson(doc, payload);
  mqttClient.publish(TOPIC_EVENTS, payload, false);
  Serial.println("[MQTT -> EVENT] Da gui COMMAND_ACCEPTED:");
  Serial.println(payload);
}

// 2. Phát sự kiện từ chối lệnh (COMMAND_REJECTED)
void publishCommandRejected(const String& cmdId, const String& orderId, const String& reason) {
  #if ARDUINOJSON_VERSION_MAJOR >= 7
    JsonDocument doc;
  #else
    StaticJsonDocument<512> doc;
  #endif

  doc["eventId"]         = generateUUID();
  doc["commandId"]       = cmdId;
  doc["machineId"]       = MACHINE_ID;
  doc["orderId"]         = orderId;
  doc["eventType"]       = "COMMAND_REJECTED";
  doc["slotNumber"]      = nullptr;
  doc["rejectionReason"] = reason;
  doc["timestamp"]       = getISO8601Timestamp();

  char payload[512];
  serializeJson(doc, payload);
  mqttClient.publish(TOPIC_EVENTS, payload, false);
  Serial.println("[MQTT -> EVENT] Da gui COMMAND_REJECTED:");
  Serial.println(payload);
}

// 3. Phát sự kiện kết quả nhả hàng (DISPENSE_RESULT)
void publishDispenseResult(const String& cmdId, const String& orderId, int slot, const String& result) {
  #if ARDUINOJSON_VERSION_MAJOR >= 7
    JsonDocument doc;
  #else
    StaticJsonDocument<512> doc;
  #endif

  doc["eventId"]           = generateUUID();
  doc["commandId"]         = cmdId;
  doc["machineId"]         = MACHINE_ID;
  doc["orderId"]           = orderId;
  doc["eventType"]         = "DISPENSE_RESULT";
  doc["slotNumber"]        = slot;
  doc["result"]            = result;
  doc["verificationLevel"] = "MOTOR_CYCLE_ONLY";
  doc["failureCode"]       = nullptr;
  doc["failureMessage"]    = nullptr;
  doc["timestamp"]         = getISO8601Timestamp();

  char payload[512];
  serializeJson(doc, payload);
  mqttClient.publish(TOPIC_EVENTS, payload, false);
  Serial.println("[MQTT -> EVENT] Da gui DISPENSE_RESULT:");
  Serial.println(payload);
}

// 4. Phát sự kiện định kỳ báo máy sống (HEARTBEAT - 30 giây/lần)
void publishHeartbeat() {
  #if ARDUINOJSON_VERSION_MAJOR >= 7
    JsonDocument doc;
  #else
    StaticJsonDocument<256> doc;
  #endif

  doc["machineId"]       = MACHINE_ID;
  doc["status"]          = "ONLINE";
  doc["firmwareVersion"] = FIRMWARE_VERSION;
  doc["uptimeSeconds"]   = millis() / 1000;
  doc["rssi"]            = WiFi.RSSI();
  doc["timestamp"]       = getISO8601Timestamp();

  char payload[256];
  serializeJson(doc, payload);
  mqttClient.publish(TOPIC_HEARTBEAT, payload, false);
  Serial.printf("[MQTT -> HEARTBEAT] Uptime: %lu s | RSSI: %d dBm\n", millis() / 1000, WiFi.RSSI());
}

// ================================================================
// XỬ LÝ LỆNH TỪ BACKEND (MQTT CALLBACK)
// ================================================================
void onMqttMessage(char* topic, byte* payloadBytes, unsigned int length) {
  Serial.println("\n------------------------------------------------");
  Serial.printf("[MQTT RECV] Topic: %s (Do dai: %u bytes)\n", topic, length);

  char payload[1024];
  if (length >= sizeof(payload)) {
    Serial.println("[MQTT ERROR] Payload qua lon, vuot qua buffer!");
    return;
  }
  memcpy(payload, payloadBytes, length);
  payload[length] = '\0';
  Serial.println(payload);

  #if ARDUINOJSON_VERSION_MAJOR >= 7
    JsonDocument doc;
  #else
    StaticJsonDocument<1024> doc;
  #endif

  DeserializationError error = deserializeJson(doc, payload);
  if (error) {
    Serial.printf("[JSON ERROR] Parse that bai: %s\n", error.c_str());
    publishCommandRejected("", "", "INVALID_PAYLOAD");
    return;
  }

  String cmdId    = doc["commandId"] | "";
  String machine  = doc["machineId"] | "";
  String orderId  = doc["orderId"] | "";
  int slot        = doc["slotNumber"] | 0;
  String action   = doc["action"] | "";

  unsigned long runMs = DEFAULT_RUN_MS;
  if (doc.containsKey("parameters") && doc["parameters"].containsKey("motorRunMs")) {
    runMs = doc["parameters"]["motorRunMs"];
  }

  // 1. Kiểm tra mã máy
  if (machine != MACHINE_ID) {
    Serial.printf("[REJECT] Sai machineId: %s (Doi: %s)\n", machine.c_str(), MACHINE_ID);
    publishCommandRejected(cmdId, orderId, "INVALID_MACHINE");
    return;
  }

  // 2. Kiểm tra action
  if (action != "DISPENSE") {
    Serial.printf("[REJECT] Action khong ho tro: %s\n", action.c_str());
    publishCommandRejected(cmdId, orderId, "UNKNOWN_ACTION");
    return;
  }

  // 3. Kiểm tra số khay
  if (slot < 1 || slot > 3) {
    Serial.printf("[REJECT] Slot khong hop le: %d\n", slot);
    publishCommandRejected(cmdId, orderId, "INVALID_SLOT");
    return;
  }

  // 4. Kiểm tra xem có đang bận chạy lệnh khác không
  if (isExecuting) {
    Serial.println("[REJECT] He thong dang ban nha hang mot lenh khac!");
    publishCommandRejected(cmdId, orderId, "BUSY");
    return;
  }

  // 5. Kiểm tra chống trùng lặp (Idempotency qua NVS Flash)
  String lastHandledCmd = preferences.getString("lastCmd", "");
  if (lastHandledCmd == cmdId && cmdId.length() > 0) {
    Serial.printf("[REJECT] Lenh da xu ly truoc do: %s\n", cmdId.c_str());
    publishCommandRejected(cmdId, orderId, "DUPLICATE_COMMAND");
    return;
  }

  // LỆNH HỢP LỆ -> CHẤP NHẬN VÀ KÍCH HOẠT MOTOR
  Serial.printf("\n[ACCEPT] Chap nhan lenh cho Slot %d. Thoi gian chay: %lu ms\n", slot, runMs);
  publishCommandAccepted(cmdId, orderId, slot);

  // Bắt đầu quay motor
  isExecuting = true;
  executingSlot = slot;
  currentCommandId = cmdId;
  currentOrderId = orderId;
  motorStopTime = millis() + runMs;

  startMotor(slot);
}

// ================================================================
// QUẢN LÝ KẾT NỐI WI-FI VÀ MQTT
// ================================================================

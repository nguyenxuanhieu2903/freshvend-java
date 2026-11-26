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

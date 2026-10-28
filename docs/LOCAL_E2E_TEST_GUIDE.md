# Hướng Dẫn Kiểm Thử E2E Toàn Trình Trên Local (Local End-to-End Testing Guide)

Tài liệu này cung cấp các lệnh PowerShell và quy trình từng bước chính xác để kiểm thử tích hợp toàn trình (End-to-End) hệ thống **Vending Machine Prototype** trên môi trường cục bộ (Windows / PowerShell).

---

> [!IMPORTANT]
> **CẢNH BÁO PHẠM VI MÔ PHỎNG & BẢO MẬT:**
> 1. Toàn bộ quá trình xuất hàng trong tài liệu này sử dụng bộ điều hợp mô phỏng phần cứng **`SimulatedDeviceAdapter`** trên phần mềm; **chưa kết nối với vi điều khiển ESP32, rơ-le hoặc động cơ nhả hàng vật lý**.
> 2. Các sự kiện thanh toán SePay trong hướng dẫn này là **dữ liệu mô phỏng qua Webhook REST API**, tuyệt đối không thực hiện chuyển khoản tiền thật hoặc sử dụng thông tin tài khoản ngân hàng thực tế.
> 3. Tuyệt đối không commit file `.env` chứa mật khẩu hoặc bí mật môi trường lên Git repository.

---

## 1. Kiểm Tra Điều Kiện Tiên Quyết (Prerequisites Verification)

Mở **PowerShell** và chạy các lệnh sau để kiểm tra môi trường:

```powershell
# 1. Kiểm tra phiên bản Java (Yêu cầu Java 21)
java -version

# Nếu máy có nhiều phiên bản Java, thiết lập JAVA_HOME trỏ tới JDK 21:
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
java -version

# 2. Kiểm tra Node.js và npm (Yêu cầu Node 18+ hoặc 20+)
node -v
npm -v

# 3. Kiểm tra dịch vụ PostgreSQL 18
Get-Service -Name postgresql*
```

---

## 2. Khởi Tạo & Xác Minh Database PostgreSQL

### 2.1. Kiểm tra hoặc khởi tạo Database `vending_machine_dev`

Nếu database phát triển chưa được khởi tạo, chạy script đi kèm dự án [`scripts/setup_dev_db.sql`](file:///c:/Users/ASUS/Vending%20Machine%20Prototype/scripts/setup_dev_db.sql) bằng quyền quản trị `postgres`:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h localhost -f "scripts\setup_dev_db.sql"
```

### 2.2. Kiểm tra kết nối tài khoản ứng dụng `vending_user`

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U vending_user -d vending_machine_dev -h localhost -c "SELECT current_database(), current_user;"
```
*(Nhập mật khẩu `vending_dev_password` khi được hỏi. Kết quả trả về `vending_machine_dev` và `vending_user`).*

---

## 3. Cấu Hình Biến Môi Trường (Environment Configuration)

### 3.1. Cấu hình Backend (`backend/.env`)

Kiểm tra file `backend/.env`. Nếu chưa có, copy từ `backend/.env.example` (không ghi đè nếu file đã tồn tại):

```powershell
if (-not (Test-Path "backend\.env")) {
    Copy-Item "backend\.env.example" "backend\.env"
    Write-Host "Created backend/.env from template."
} else {
    Write-Host "backend/.env already exists. Preserving existing configuration."
}
```

Nội dung tối thiểu cần có trong `backend/.env` cho phiên chạy E2E local:

```env
PORT=8080
SPRING_PROFILES_ACTIVE=local
DATABASE_URL=jdbc:postgresql://localhost:5432/vending_machine_dev
DATABASE_USERNAME=vending_user
DATABASE_PASSWORD=vending_dev_password
MACHINE_ID=VM001
FRONTEND_ORIGIN=http://localhost:5173
ORDER_EXPIRATION_MINUTES=15

# Cấu hình thanh toán Demo SePay
PAYMENT_DEMO_MODE=true
SEPAY_API_KEY=test-sepay-secret-key
SEPAY_BANK_BIN=970422
SEPAY_BANK_NAME=MBBank
SEPAY_ACCOUNT_NUMBER=DEMO123456
SEPAY_ACCOUNT_NAME=DEMO SMART VENDING

# Cấu hình xuất hàng mô phỏng
DISPENSE_AUTO_TRIGGER=true
DISPENSE_COMMAND_TIMEOUT_SECONDS=45
DISPENSE_SIMULATOR_ENABLED=true
DISPENSE_SIMULATOR_ASYNC=false
DISPENSE_SIMULATOR_DEFAULT_SCENARIO=SUCCESS
```

### 3.2. Cấu hình Frontend (`frontend/.env`)

Kiểm tra file `frontend/.env` đảm bảo trỏ đúng REST API của backend:

```env
VITE_API_BASE_URL=http://localhost:8080
```

---

## 4. Khởi Chạy Hệ Thống Trên Hai Terminal Riêng Biệt

### Terminal 1: Khởi chạy Spring Boot Backend

```powershell
cd "c:\Users\ASUS\Vending Machine Prototype\backend"
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# Nạp các biến môi trường từ .env nếu cần hoặc chạy trực tiếp qua Maven Wrapper
./mvnw spring-boot:run
```

*Chờ cho đến khi terminal hiển thị:*
```text
Started VendingMachineApplication in X.XXX seconds
Registered DispenseService as listener to SimulatedDeviceAdapter
```

### Terminal 2: Khởi chạy Vite React Frontend

Mở cửa sổ PowerShell thứ hai:

```powershell
cd "c:\Users\ASUS\Vending Machine Prototype\frontend"
npm run dev
```

*Chờ cho đến khi terminal hiển thị:*
```text
  VITE v8.3.4  ready in XXX ms

  ➜  Local:   http://localhost:5173/
  ➜  Network: use --host to expose
```

---

## 5. Xác Minh Sức Khỏe Hệ Thống & Kết Nối Ban Đầu (Health Checks)

Mở một cửa sổ PowerShell thứ ba (dùng cho lệnh gọi API và kiểm tra database):

```powershell
# 1. Kiểm tra Actuator Health của Backend
Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -Method Get

# 2. Kiểm tra thông tin máy VM001 và danh mục sản phẩm (Flyway V2 Seed Data)
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/machines/VM001/products" -Method Get

# 3. Kiểm tra trạng thái Adapter Mô Phỏng
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/simulation/device/status" -Method Get
```

*Phản hồi mẫu của simulator:*
```json
{
  "success": true,
  "data": {
    "adapterType": "SIMULATED",
    "currentScenario": "SUCCESS",
    "asyncMode": false,
    "isOnline": true,
    "disclaimer": "SIMULATION ONLY - NOT PHYSICAL HARDWARE"
  }
}
```

---

## 6. Quy Trình Kiểm Thử E2E Từng Bước (Step-by-Step E2E Scenarios)

Dữ liệu khởi tạo mặc định trong PostgreSQL (từ Flyway `V2__seed_data.sql`):
- Máy: `VM001`
- Khe 1 (`550e8400-e29b-41d4-a716-446655440001`): Nước suối Aqua 500ml — Giá: `8,000 VND` — Tồn kho: `5`
- Khe 2 (`550e8400-e29b-41d4-a716-446655440002`): Snack Lay's — Giá: `15,000 VND` — Tồn kho: `3`
- Khe 3 (`550e8400-e29b-41d4-a716-446655440003`): Kẹo Doublemint — Giá: `5,000 VND` — Tồn kho: `0` (Hết hàng)

---

### Kịch Bản 1: Luồng Thành Công Hoàn Toàn (Happy Path — SUCCESS)

#### Bước 1.1: Tạo đơn hàng qua giao diện hoặc API
- **Cách 1 (Giao diện Web):**
  1. Mở trình duyệt tại `http://localhost:5173`.
  2. Bấm vào nút **"Chọn mua"** tại món *Nước suối Aqua 500ml*.
  3. Bấm **"Thanh toán ngay"** trên giỏ hàng, sau đó bấm **"Xác nhận tạo đơn"**.
  4. Giao diện hiển thị Modal với Mã đơn hàng, VietQR, và mã nội dung chuyển khoản (VD: `VM-ABC12345`).
- **Cách 2 (PowerShell):**
  ```powershell
  $orderReq = @{
      machineId = "VM001"
      productId = "550e8400-e29b-41d4-a716-446655440001"
      quantity = 1
  } | ConvertTo-Json

  $orderRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders" -Method Post -ContentType "application/json" -Body $orderReq
  $orderId = $orderRes.data.orderId
  $transferRef = $orderRes.data.transferReference
  $amount = $orderRes.data.totalAmountVnd
  Write-Host "Created Order: $orderId | Ref: $transferRef | Amount: $amount"
  ```

#### Bước 1.2: Kiểm tra Database sau khi tạo đơn
```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U vending_user -d vending_machine_dev -h localhost -c "
SELECT public_order_code, status, total_amount_vnd, transfer_reference, payment_expires_at 
FROM orders WHERE id = '$orderId';
SELECT name, slot_number, stock_quantity, reserved_quantity 
FROM products WHERE id = '550e8400-e29b-41d4-a716-446655440001';"
```
*Kết quả kỳ vọng:*
- `orders.status` = `PENDING_PAYMENT`
- `products.stock_quantity` = `5`, `products.reserved_quantity` = `1` (Đã giữ chỗ, chưa trừ kho vật lý).

#### Bước 1.3: Đảm bảo Simulator đang ở kịch bản SUCCESS
```powershell
$modeReq = @{ scenario = "SUCCESS"; async = $false } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/simulation/device/mode" -Method Post -ContentType "application/json" -Body $modeReq
```

#### Bước 1.4: Gửi Webhook Thanh Toán SePay Mô Phỏng
```powershell
$txId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$webhookPayload = @{
    id = $txId
    gateway = "MBBank"
    transactionDate = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    accountNumber = "DEMO123456"
    transferType = "in"
    transferAmount = $amount
    content = "Thanh toan $transferRef"
    code = $transferRef
} | ConvertTo-Json

$headers = @{ "Authorization" = "Apikey test-sepay-secret-key" }

$webhookRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/webhooks/payments/sepay" `
    -Method Post -Headers $headers -ContentType "application/json" -Body $webhookPayload

$webhookRes | ConvertTo-Json
```
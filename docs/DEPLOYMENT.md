# Hướng Dẫn Triển Khai Cloud Tối Thiểu (Minimal Cloud Deployment Guide)
# Vending Machine Prototype

Tài liệu này cung cấp hướng dẫn từng bước chính xác để đưa toàn bộ hệ thống **Vending Machine Prototype** (Frontend, Backend, Database) lên môi trường Cloud công khai với chi phí thấp nhất, ít thao tác cấu hình nhất và không yêu cầu cài đặt Docker trên máy phát triển cục bộ.

---

## 1. Kiến Trúc Triển Khai Được Lựa Chọn

```
[ Điện Thoại Khách Hàng ] (Quét mã QR cố định trên thân máy)
            │
            ▼ (HTTPS)
[ Frontend: Vercel / Cloudflare Pages ] (Static Hosting, CDN toàn cầu, có sẵn SSL)
            │
            ▼ (REST API qua HTTPS)
[ Backend: Render.com / Railway.app ] (Chạy Java 21 / Spring Boot, có sẵn HTTPS Webhook URL)
            │
            ▼ (TCP / SSL qua JDBC)
[ Database: Neon.tech / Supabase ] (PostgreSQL 16+, Flyway tự động khởi tạo V1-V4 khi Backend boot)
```

### Ưu điểm của kiến trúc này:
1. **Không cần cài đặt Docker local:** Mọi thứ được build và chạy trực tiếp trên nền tảng Cloud.
2. **Có sẵn chứng chỉ SSL / HTTPS:** Cần thiết để điện thoại quét mã QR không bị cảnh báo bảo mật và Webhook SePay hoạt động hợp lệ.
3. **Tự động khởi tạo dữ liệu:** Cơ chế Flyway Migration (V1 đến V4) của Backend sẽ tự động tạo bảng, tạo ràng buộc và nạp sẵn dữ liệu máy `VM001` cùng 3 vị trí khay sản phẩm ngay lần đầu Backend kết nối tới Database mới.

---

## 2. Thứ Tự Triển Khai Bắt Buộc

Việc triển khai **BẮT BUỘC** thực hiện theo đúng thứ tự 4 bước sau để đảm bảo các chuỗi kết nối và chính sách CORS khớp nhau:

```
[ BƯỚC 1: Database (Neon/Supabase) ] ── Lấy chuỗi JDBC URL ──┐
                                                            ▼
[ BƯỚC 2: Backend (Render/Railway) ] ◄──────────────────────┘
            │
            ├─ Lấy Backend URL (ví dụ: https://vending-api.onrender.com)
            ▼
[ BƯỚC 3: Frontend (Vercel/Cloudflare Pages) ] ── Build với VITE_API_BASE_URL
            │
            ├─ Lấy Frontend URL (ví dụ: https://vending-app.vercel.app)
            ▼
[ BƯỚC 4: Cập Nhật Backend CORS ] ── Đặt FRONTEND_ORIGIN = Frontend URL
```

---

## 3. Hướng Dẫn Từng Bước Chi Tiết

### BƯỚC 1: Khởi Tạo Database PostgreSQL (Neon hoặc Supabase)

1. Đăng ký/đăng nhập tài khoản miễn phí tại [Neon.tech](https://neon.tech) hoặc [Supabase.com](https://supabase.com).
2. Tạo một Project mới (đặt tên ví dụ: `vending-machine-db`), chọn khu vực gần Việt Nam (ví dụ: `Singapore` - `ap-southeast-1`).
3. Vào phần **Connection Details** và chọn tab **JDBC**:
   - URL mẫu có định dạng:
     `jdbc:postgresql://<ep-xyz>.ap-southeast-1.aws.neon.tech/neondb?sslmode=require`
   - Lưu lại:
     - `DATABASE_URL` (chuỗi JDBC ở trên)
     - `DATABASE_USERNAME` (thường là username hiển thị trên dashboard)
     - `DATABASE_PASSWORD` (mật khẩu đã tạo)
4. *Lưu ý:* Không cần chạy bất kỳ câu lệnh SQL thủ công nào. Backend sẽ tự động chạy migration khi khởi động.

---

### BƯỚC 2: Triển Khai Backend (Render.com hoặc Railway.app)

Dự án đã có sẵn [backend/Dockerfile](file:///c:/Users/ASUS/Vending%20Machine%20Prototype/backend/Dockerfile) chuẩn multi-stage Java 21 Temurin.

1. Đăng nhập [Render.com](https://render.com) -> Chọn **New +** -> **Web Service**.
2. Kết nối tới Git Repository của dự án.
3. Thiết lập thông số:
   - **Name:** `vending-backend`
   - **Root Directory:** `backend`
   - **Runtime:** `Docker` (Render sẽ tự động dùng `backend/Dockerfile`)
   - **Instance Type:** `Free`
4. Cấu hình **Environment Variables** (Runtime):

| Tên Biến | Giá trị khuyến nghị | Mục đích |
|---|---|---|
| `PORT` | `8080` | Cổng HTTP lắng nghe |
| `SPRING_PROFILES_ACTIVE` | `local` (hoặc `prod`) | Profile Spring Boot |
| `DATABASE_URL` | `jdbc:postgresql://<host>:5432/<db>?sslmode=require` | Chuỗi kết nối JDBC từ Bước 1 |
| `DATABASE_USERNAME` | `<db_user>` | Username database |
| `DATABASE_PASSWORD` | `<db_password>` | Password database |
| `FRONTEND_ORIGIN` | Tạm thời để `http://localhost:5173` (sẽ cập nhật ở Bước 4) | Nguồn cho phép gọi API CORS |
| `APP_ADMIN_JWT_SECRET` | Chuỗi ngẫu nhiên tối thiểu 32 ký tự (VD: `vending_jwt_secret_key_prod_2026_xyz123!`) | **Bắt buộc** để ký JWT token admin |
| `APP_ADMIN_BOOTSTRAP_USERNAME` | `admin` | Tên tài khoản admin khởi tạo |
| `APP_ADMIN_BOOTSTRAP_PASSWORD` | Mật khẩu admin an toàn (tối thiểu 8 ký tự) | Mật khẩu admin khởi tạo |
| `PAYMENT_DEMO_MODE` | `true` | Chế độ demo thanh toán (không trừ tiền thật) |
| `DISPENSE_SIMULATOR_ENABLED` | `true` | Kích hoạt bộ điều hợp mô phỏng nhả hàng |
| `DISPENSE_AUTO_TRIGGER` | `true` | Tự động kích hoạt nhả hàng sau khi xác minh đơn |

5. Nhấn **Create Web Service** và theo dõi log:
   - Đợi log xuất hiện: `Flyway Community Edition ... Current version of schema "public": 4`
   - Và: `Started VendingMachineApplication in ... seconds`
   - Kiểm tra endpoint health check trên trình duyệt:
     `https://<backend-app-name>.onrender.com/actuator/health` -> Nhận kết quả `{"status":"UP"}`.

---

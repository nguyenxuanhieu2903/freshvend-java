# ==============================================================================
# SCRIPT KÍCH HOẠT THANH TOÁN DEMO CHO ĐƠN HÀNG MỚI NHẤT TRÊN WEB
# File: scripts/pay_current_order.ps1
# ==============================================================================

$env:PGPASSWORD = "vending_dev_password"
$psqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe"

Write-Host "Dang tim don hang moi nhat o trang thai PENDING_PAYMENT..." -ForegroundColor Cyan

$query = @"
SELECT o.id, o.total_amount_vnd, o.transfer_reference, o.public_order_code, p.slot_number
FROM orders o
JOIN products p ON o.product_id = p.id
WHERE o.status = 'PENDING_PAYMENT'
ORDER BY o.created_at DESC
LIMIT 1;
"@

$raw = & $psqlPath -U vending_user -d vending_machine_dev -h localhost -t -A -F "|" -c $query

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($raw)) {
    Write-Host "[THONG BAO] Khong tim thay don hang nao dang cho thanh toan tren Web!" -ForegroundColor Yellow
    Write-Host "--> Ban hay len trinh duyet http://localhost:5173 va bam chon 1 mon roi bam 'Mua ngay' / 'Thanh toan' truoc nhe!" -ForegroundColor Yellow
    exit 0
}

$parts = $raw.Trim() -split "\|"
$orderId = $parts[0]
$amount = [long]$parts[1]
$transferRef = $parts[2]
$orderCode = $parts[3]
$slotNumber = $parts[4]

Write-Host "==============================================================" -ForegroundColor Green
Write-Host "TIM THAY DON HANG CHO THANH TOAN:" -ForegroundColor Green
Write-Host "  - Ma don hang: $orderCode"
Write-Host "  - Khay xuat hang: Slot $slotNumber"
Write-Host "  - So tien: $amount VND"
Write-Host "  - Noi dung chuyen khoan: $transferRef"
Write-Host "==============================================================" -ForegroundColor Green

$txId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()

$webhookPayload = @{
    id              = [long]$txId
    gateway         = "MBBank"
    transactionDate = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    accountNumber   = "DEMO123456"
    subAccount      = $null
    transferType    = "in"
    transferAmount  = $amount
    code            = $transferRef
    content         = "Thanh toan don $transferRef"
    description     = "Thanh toan don $transferRef"
    referenceCode   = "MB$txId"
} | ConvertTo-Json

$headers = @{
    "Authorization" = "Apikey test-sepay-secret-key"
}

Write-Host "`n[WEBHOOK] Dang gui xac nhan thanh toan (Demo SePay) toi Backend..." -ForegroundColor Cyan

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/webhooks/payments/sepay" `
        -Method Post `
        -Headers $headers `
        -ContentType "application/json; charset=utf-8" `
        -Body $webhookPayload `
        -TimeoutSec 10

    Write-Host "[OK] Backend da xac nhan thanh toan thanh cong!" -ForegroundColor Green
    Write-Host "  Response: $($response | ConvertTo-Json -Compress)"
    Write-Host "`n==> Backend dang ban lenh MQTT toi Cloud. Quan sat servo Khay $slotNumber ngoai doi!" -ForegroundColor Yellow
}
catch {
    Write-Host "[LOI] Gui webhook that bai: $($_.Exception.Message)" -ForegroundColor Red
}

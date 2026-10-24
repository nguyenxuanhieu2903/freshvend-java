package com.vendingmachine.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import com.vendingmachine.order.dto.OrderResponseDto;
import com.vendingmachine.order.entity.Order;
import com.vendingmachine.order.repository.OrderRepository;
import com.vendingmachine.payment.dto.SePayWebhookDto;
import com.vendingmachine.payment.dto.SePayWebhookResponseDto;
import com.vendingmachine.payment.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final OrderRepository orderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final VietQrService vietQrService;
    private final ObjectMapper objectMapper;

    @Value("${app.payment.demo-mode:false}")
    private boolean demoMode;

    @Value("${app.payment.sepay.api-key:}")
    private String configuredApiKey;

    @Value("${app.payment.sepay.bank-bin:970422}")
    private String bankBin;

    @Value("${app.payment.sepay.bank-name:MBBank}")
    private String bankName;

    @Value("${app.payment.sepay.account-number:0345678999}")
    private String accountNumber;

    @Value("${app.payment.sepay.account-name:SMART VENDING MACHINE}")
    private String accountName;

    public PaymentServiceImpl(OrderRepository orderRepository,
                              PaymentTransactionRepository paymentTransactionRepository,
                              VietQrService vietQrService,
                              ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.vietQrService = vietQrService;
        this.objectMapper = objectMapper;
    }

    @Override
    public OrderResponseDto.OrderPaymentDto createPaymentInstructions(Order order) {
        String qrData = vietQrService.generateVietQr(
                bankBin,
                accountNumber,
                order.getTotalAmountVnd(),
                order.getTransferReference()
        );

        return new OrderResponseDto.OrderPaymentDto(
                order.getTransferReference(),
                qrData,
                accountNumber,
                accountName,
                bankName,
                order.getTotalAmountVnd(),
                order.getPaymentExpiresAt(),
                order.getPaidAt()
        );
    }

    @Override
    public SePayWebhookResponseDto processSePayWebhook(String authHeader, SePayWebhookDto payload) {
        // 1. Verify Webhook Authentication
        authenticateWebhook(authHeader);

        if (payload == null || payload.getId() == null) {
            log.warn("Received empty or invalid SePay webhook payload");
            return SePayWebhookResponseDto.ok("Payload missing id, ignored");
        }

        return SePayWebhookResponseDto.ok();
    }

    private void authenticateWebhook(String authHeader) {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            if (demoMode) {
                log.warn("SePay API key not configured; accepting webhook in DEMO mode");
                return;
            }
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Webhook API key not configured on server", HttpStatus.UNAUTHORIZED);
        }

        if (authHeader == null || authHeader.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing Authorization header in webhook request", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.trim();
        if (token.startsWith("Apikey ")) {
            token = token.substring(7).trim();
        } else if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        if (!configuredApiKey.equals(token)) {
            log.warn("Invalid webhook credentials received");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid SePay webhook API key", HttpStatus.UNAUTHORIZED);
        }
    }
}

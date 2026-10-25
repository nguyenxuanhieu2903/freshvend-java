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
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    private static final Pattern REF_PATTERN = Pattern.compile("(?i)(VM[A-Z0-9_-]+)");

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

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    @Autowired(required = false)
    private TransactionTemplate transactionTemplate;

    public PaymentServiceImpl(OrderRepository orderRepository,
                              PaymentTransactionRepository paymentTransactionRepository,
                              VietQrService vietQrService,
                              ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.vietQrService = vietQrService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (this.transactionTemplate == null && this.transactionManager != null) {
            this.transactionTemplate = new TransactionTemplate(this.transactionManager);
        }
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

        String providerTxId = String.valueOf(payload.getId());

        // 2. Fast-path check: prevent duplicate processing if already persisted
        if (paymentTransactionRepository.existsByProviderAndProviderTransactionId("SEPAY", providerTxId)) {
            log.info("Duplicate SePay transaction ID {} received; returning idempotent success", providerTxId);
            return SePayWebhookResponseDto.ok("Duplicate transaction ignored");
        }

        // 3. Filter out non-incoming transactions
        if (payload.getTransferType() != null && !payload.getTransferType().equalsIgnoreCase("in")) {
            log.info("Ignored non-incoming transaction ID {} (type: {})", providerTxId, payload.getTransferType());
            return SePayWebhookResponseDto.ok("Non-incoming transaction ignored");
        }

        // Mutual exclusion per provider transaction ID to prevent concurrent duplicates from colliding
        synchronized (("SEPAY_TX_" + providerTxId).intern()) {
            if (paymentTransactionRepository.existsByProviderAndProviderTransactionId("SEPAY", providerTxId)) {
                log.info("Duplicate SePay transaction ID {} verified in sync block; returning idempotent success", providerTxId);
                return SePayWebhookResponseDto.ok("Duplicate transaction ignored");
            }

            try {
                if (transactionTemplate != null) {
                    return transactionTemplate.execute(status -> doProcessSePayWebhook(payload, providerTxId));
                } else {
                    return doProcessSePayWebhook(payload, providerTxId);
                }
            } catch (DataIntegrityViolationException ex) {
                log.info("Duplicate SePay transaction ID {} caught DataIntegrityViolationException; returning idempotent success", providerTxId);
                return SePayWebhookResponseDto.ok("Duplicate transaction ignored");
            }
        }
    }

    @Transactional
    public SePayWebhookResponseDto doProcessSePayWebhook(SePayWebhookDto payload, String providerTxId) {
        log.info("Processing SePay webhook transaction ID: {}", providerTxId);
        String transferRef = extractTransferReference(payload);
        log.debug("Extracted transfer reference: {}", transferRef);
        return SePayWebhookResponseDto.ok();
    }

    private String extractTransferReference(SePayWebhookDto payload) {
        if (payload.getCode() != null && !payload.getCode().isBlank()) {
            return payload.getCode().trim();
        }

        String content = payload.getContent();
        if (content != null && !content.isBlank()) {
            Matcher matcher = REF_PATTERN.matcher(content);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        String description = payload.getDescription();
        if (description != null && !description.isBlank()) {
            Matcher matcher = REF_PATTERN.matcher(description);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
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

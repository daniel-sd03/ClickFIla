package sodresoftwares.barbearia.services.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sodresoftwares.barbearia.model.billing.Payment;
import sodresoftwares.barbearia.model.billing.PaymentHistory;
import sodresoftwares.barbearia.model.billing.PaymentStatus;
import sodresoftwares.barbearia.repositories.billing.PaymentHistoryRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentHistoryService Tests")
class PaymentHistoryServiceTest {

    @Mock
    private PaymentHistoryRepository historyRepository;

    @InjectMocks
    private PaymentHistoryService historyService;

    @Test
    @DisplayName("Should create and save payment history successfully")
    void testLogStatusChange_Success() {
        // Arrange
        Payment mockPayment = Payment.builder()
                .status(PaymentStatus.PENDING)
                .build();

        PaymentStatus newStatus = PaymentStatus.OVERDUE;
        String gatewayEvent = "PAYMENT_OVERDUE";

        // Act
        historyService.logStatusChange(mockPayment, newStatus, gatewayEvent);

        // Assert
        ArgumentCaptor<PaymentHistory> historyCaptor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(historyRepository).save(historyCaptor.capture());

        PaymentHistory savedHistory = historyCaptor.getValue();

        assertThat(savedHistory.getPayment()).isEqualTo(mockPayment);
        assertThat(savedHistory.getPreviousStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(savedHistory.getNewStatus()).isEqualTo(PaymentStatus.OVERDUE);
        assertThat(savedHistory.getGatewayEvent()).isEqualTo("PAYMENT_OVERDUE");
    }
}
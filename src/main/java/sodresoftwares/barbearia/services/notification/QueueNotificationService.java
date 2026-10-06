package sodresoftwares.barbearia.services.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sodresoftwares.barbearia.dto.queue.QueueEntryResponseDTO;
import sodresoftwares.barbearia.mappers.QueueMapper;
import sodresoftwares.barbearia.services.queue.QueueCacheService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final QueueCacheService queueCacheService;
    private final QueueMapper queueMapper;

    public void notifyQueueUpdate(String sessionId) {

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executeBroadcast(sessionId);
                }
            });
        } else {
            executeBroadcast(sessionId);
        }
    }

    public void executeBroadcast(String sessionId) {
        String destination = "/topic/queue/" + sessionId;

        List<QueueEntryResponseDTO> payload = queueCacheService.getActiveEntriesDTO(sessionId);

        messagingTemplate.convertAndSend(destination, payload);

        log.info("Queue update notification sent to: {}", sessionId);
    }
}
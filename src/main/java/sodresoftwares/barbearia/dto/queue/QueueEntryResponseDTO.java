package sodresoftwares.barbearia.dto.queue;

import sodresoftwares.barbearia.model.queue.QueueEntryStatus;

import java.time.Instant;

public record QueueEntryResponseDTO(
        String id,
        Integer position,
        String userId,
        String clientName,
        String serviceName,
        QueueEntryStatus status,
        String servedByMemberId,
        String servedByMemberName,
        Instant calledAt,
        Instant serverTimeNow,
        Instant toleranceExpiresAt,
        Integer toleranceMinute
) {}
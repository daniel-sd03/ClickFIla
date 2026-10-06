package sodresoftwares.barbearia.mappers;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import sodresoftwares.barbearia.dto.queue.QueueEntryResponseDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.queue.QueueEntry;
import sodresoftwares.barbearia.model.queue.QueueEntryStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class QueueMapper {

    public List<QueueEntryResponseDTO> toDtoList(List<QueueEntry> activeEntries) {
        List<QueueEntryResponseDTO> dtos = new ArrayList<>();
        for (int i = 0; i < activeEntries.size(); i++) {
            QueueEntry entry = activeEntries.get(i);
            dtos.add(toDto(entry, i + 1));
        }
        return dtos;
    }

    public QueueEntryResponseDTO toSingleDto(QueueEntry targetEntry, List<QueueEntry> activeEntries) {
        for (int i = 0; i < activeEntries.size(); i++) {
            if (activeEntries.get(i).getId().equals(targetEntry.getId())) {
                return toDto(targetEntry, i + 1);
            }
        }
        throw new AppException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "ENTRY_NOT_IN_ACTIVE_QUEUE",
                "Queue entry was not found in the active queue."
        );
    }

    private QueueEntryResponseDTO toDto(QueueEntry entry, int position) {

        Integer toleranceMinute = entry.getQueueSession().getToleranceMinutes();
        Instant serverTimeNow = Instant.now();
        Instant toleranceExpiresAt = null;

        if (entry.getCalledAt() != null && entry.getStatus() == QueueEntryStatus.CALLED) {
            toleranceExpiresAt = entry.getCalledAt().plus(toleranceMinute, ChronoUnit.MINUTES);
        }

        String servedByMemberId = null;
        String servedByMemberName = null;

        if (entry.getServedByMember() != null) {
            servedByMemberId = entry.getServedByMember().getId();
            servedByMemberName = entry.getServedByMember().getName();
        }

        return new QueueEntryResponseDTO(
                entry.getId(),
                position,
                entry.getUser().getId(),
                entry.getUser().getName(),
                entry.getServiceName(),
                entry.getStatus(),
                servedByMemberId,
                servedByMemberName,
                entry.getCalledAt(),
                serverTimeNow,
                toleranceExpiresAt,
                toleranceMinute
        );
    }
}
package sodresoftwares.barbearia.services.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.dto.queue.QueueEntryResponseDTO;
import sodresoftwares.barbearia.mappers.QueueMapper;
import sodresoftwares.barbearia.model.queue.QueueEntry;
import sodresoftwares.barbearia.repositories.queue.QueueEntryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QueueCacheService {

    private final QueueEntryRepository queueEntryRepository;
    private final QueueMapper queueMapper;

    @Cacheable(value = "activeEntries", key = "#sessionId")
    public List<QueueEntryResponseDTO> getActiveEntriesDTO(String sessionId) {
        List<QueueEntry> activeEntries = queueEntryRepository.findActiveEntriesBySessionId(sessionId);
        return queueMapper.toDtoList(activeEntries);
    }

    @CacheEvict(value = "activeEntries", key = "#sessionId")
    public void evictSessionList(String sessionId) {
    }
}
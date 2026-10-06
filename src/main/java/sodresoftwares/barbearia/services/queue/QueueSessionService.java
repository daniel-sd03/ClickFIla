package sodresoftwares.barbearia.services.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.dto.queue.QueueSessionBusinessResponseDTO;
import sodresoftwares.barbearia.dto.queue.QueueSessionUserResponseDTO;
import sodresoftwares.barbearia.dto.queue.UpdateQueueSessionDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.queue.QueueSession;
import sodresoftwares.barbearia.model.team.TeamMember;
import sodresoftwares.barbearia.model.team.TeamRole;
import sodresoftwares.barbearia.repositories.queue.QueueSessionRepository;
import sodresoftwares.barbearia.repositories.team.TeamMemberRepository;

import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QueueSessionService {

    private final QueueSessionRepository queueSessionRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final QueueCacheService queueCacheService;

    @Transactional
    public QueueSessionBusinessResponseDTO createQueueSession(String loggedUserId) {
        Business business = getBusinessForOwner(loggedUserId);

        if (queueSessionRepository.existsByBusinessId(business.getId())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "QUEUE_ALREADY_EXISTS",
                    "This business already has a queue session.");
        }

        String initialPrefix = generateInitialPrefix(business.getName());
        String safeTicketCode = generateUniqueTicketCode(initialPrefix);

        QueueSession newSession = QueueSession.builder()
                .business(business)
                .prefix(initialPrefix)
                .ticketCode(safeTicketCode)
                .isActive(false)
                .build();

        QueueSession savedSession = queueSessionRepository.save(newSession);
        log.info("Queue session created");

        return mapToSessionDTO(savedSession);
    }

    @Transactional
    public QueueSessionBusinessResponseDTO updateQueueStatus(String loggedUserId, boolean activate) {
        QueueSession session = getSessionForOwner(loggedUserId);
        session.setIsActive(activate);
        QueueSession savedSession = queueSessionRepository.save(session);
        log.info("Queue session {}", activate ? "opened" : "closed");

        return mapToSessionDTO(savedSession);
    }

    @Transactional
    public QueueSessionBusinessResponseDTO updateSessionSettings(String loggedUserId, UpdateQueueSessionDTO dto) {
        QueueSession session = getSessionForOwner(loggedUserId);

        boolean wasUpdated = false;

        if (dto.toleranceMinutes() != null) {
            session.setToleranceMinutes(dto.toleranceMinutes());
            wasUpdated = true;
        }

        if (dto.prefix() != null && !dto.prefix().isBlank()) {
            String cleanPrefix = sanitize(dto.prefix());

            if (cleanPrefix.length() < 2) {
                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_PREFIX",
                        "Prefix must contain at least 2 valid alphanumeric characters."
                );
            }

            String newTicketCode = generateUniqueTicketCode(cleanPrefix);
            session.setTicketCode(newTicketCode);
            session.setPrefix(cleanPrefix);
            wasUpdated = true;
        }

        if (wasUpdated) {
            queueSessionRepository.save(session);
            log.info("Queue session settings updated");
        }

        return mapToSessionDTO(session);
    }

    @Transactional
    public QueueSessionBusinessResponseDTO refreshTicketCode(String loggedUserId) {
        QueueSession session = getSessionForOwner(loggedUserId);

        String newTicketCode = generateUniqueTicketCode(session.getPrefix());

        session.setTicketCode(newTicketCode);

        QueueSession savedSession = queueSessionRepository.save(session);

        log.info("Ticket code regenerated");

        return mapToSessionDTO(savedSession);
    }

    public QueueSessionUserResponseDTO getSessionInfoByCode(String ticketCode) {
        QueueSession session = queueSessionRepository.findByTicketCodeWithBusiness(ticketCode.toUpperCase())
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "SESSION_NOT_FOUND",
                        "Queue not found for the Ticket code."
                ));

        int peopleInQueue = queueCacheService.getActiveEntriesDTO(session.getId()).size();

        return new QueueSessionUserResponseDTO(
                session.getId(),
                session.getBusiness().getName(),
                peopleInQueue,
                session.getIsActive(),
                session.getToleranceMinutes()
        );
    }

    //------------ Auxiliary Methods ------------

    private QueueSession getSessionForOwner(String loggedUserId) {
        return queueSessionRepository.findByOwnerUserId(loggedUserId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "SESSION_NOT_FOUND",
                        "Queue not found or you are not the owner."
                ));
    }

    private Business getBusinessForOwner(String loggedUserId) {
        TeamMember member = teamMemberRepository.findActiveByUserIdWithBusiness(loggedUserId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "TEAM_MEMBER_NOT_FOUND",
                        "User is not associated with any team/business."));

        if (member.getRole() != TeamRole.OWNER) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "Only the business owner can perform this action.");
        }

        return member.getBusiness();
    }

    // Generates prefix based on Business name or defaults to "FILA"
    private String generateInitialPrefix(String businessName) {
        if (businessName != null && !businessName.isBlank()) {
            String sanitized = sanitize(businessName);
            if (sanitized.length() >= 2) {
                return sanitized.substring(0, Math.min(sanitized.length(), 4));
            }
        }
        return "FILA";
    }

    // Sanitizes special characters and spaces to prevent URL breaks
    private String sanitize(String text) {
        return text.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
    }

    // Security loop to prevent collision (duplicate codes in the database)
    private String generateUniqueTicketCode(String prefix) {
        String generatedCode;
        boolean codeExists;

        do {
            int randomNumber = ThreadLocalRandom.current().nextInt(1000, 10000);
            String shortCode = String.format("%04d", randomNumber);
            generatedCode = prefix + shortCode;

            codeExists = queueSessionRepository.existsByTicketCode(generatedCode);

            if (codeExists) {
                log.warn("Collision detected for code {}. Generating a new one...", generatedCode);
            }

        } while (codeExists);

        return generatedCode;
    }

    private QueueSessionBusinessResponseDTO mapToSessionDTO(QueueSession session) {
        return new QueueSessionBusinessResponseDTO(
                session.getId(),
                session.getTicketCode(),
                session.getIsActive()
        );
    }
}
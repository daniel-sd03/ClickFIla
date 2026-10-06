package sodresoftwares.barbearia.controllers.queue;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sodresoftwares.barbearia.dto.queue.QueueSessionBusinessResponseDTO;
import sodresoftwares.barbearia.dto.queue.QueueSessionUserResponseDTO;
import sodresoftwares.barbearia.dto.queue.UpdateQueueSessionDTO;
import sodresoftwares.barbearia.dto.queue.UpdateQueueStatusDTO;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.services.queue.QueueSessionService;

@RestController
@RequestMapping("/queue-sessions")
@RequiredArgsConstructor
public class QueueSessionController {

    private final QueueSessionService queueSessionService;

    @GetMapping("/tickets/{ticketCode}")
    public ResponseEntity<QueueSessionUserResponseDTO> getSessionByCode(
            @PathVariable String ticketCode) {
        QueueSessionUserResponseDTO response = queueSessionService.getSessionInfoByCode(ticketCode);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<QueueSessionBusinessResponseDTO> createSession(
            @AuthenticationPrincipal User loggedInUser) {
        QueueSessionBusinessResponseDTO session = queueSessionService.createQueueSession(loggedInUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @PostMapping("/me/ticket-code")
    public ResponseEntity<QueueSessionBusinessResponseDTO> refreshTicketCode(
            @AuthenticationPrincipal User loggedInUser) {
        QueueSessionBusinessResponseDTO updatedSession = queueSessionService.refreshTicketCode(loggedInUser.getId());
        return ResponseEntity.ok(updatedSession);
    }

    @PatchMapping("/me")
    public ResponseEntity<QueueSessionBusinessResponseDTO> updateSession(
            @AuthenticationPrincipal User loggedInUser,
            @Valid @RequestBody UpdateQueueSessionDTO request) {
        QueueSessionBusinessResponseDTO updatedSession =
                queueSessionService.updateSessionSettings(loggedInUser.getId(), request);
        return ResponseEntity.ok(updatedSession);
    }

    @PatchMapping("/me/status")
    public ResponseEntity<QueueSessionBusinessResponseDTO> updateStatus(
            @AuthenticationPrincipal User loggedInUser,
            @RequestBody @Valid UpdateQueueStatusDTO dto) {
        QueueSessionBusinessResponseDTO session = queueSessionService.updateQueueStatus(loggedInUser.getId(),dto.activate());
        return ResponseEntity.ok(session);
    }
}
package sodresoftwares.barbearia.controllers.queue;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sodresoftwares.barbearia.dto.queue.*;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.services.queue.QueueEntryService;

@RestController
@RequestMapping("/queue-entries")
@RequiredArgsConstructor
public class QueueEntryController {

    private final QueueEntryService queueEntryService;

    @GetMapping("/me/status")
    public ResponseEntity<UserQueueStatusDTO> getMyQueueStatus(
            @AuthenticationPrincipal User loggedInUser) {
        UserQueueStatusDTO status = queueEntryService.getUserQueueStatus(loggedInUser.getId());
        return ResponseEntity.ok(status);
    }

    @PostMapping
    public ResponseEntity<QueueEntryResponseDTO> joinQueue(
            @RequestBody @Valid JoinQueueDTO dto,
            @AuthenticationPrincipal User loggedInUser) {
        QueueEntryResponseDTO response = queueEntryService.joinQueue(dto,loggedInUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/sessions/{sessionId}/next")
    public ResponseEntity<QueueEntryResponseDTO> callNext(
            @PathVariable String sessionId,
            @RequestBody @Valid CallNextDTO dto,
            @AuthenticationPrincipal User loggedInUser) {

        QueueEntryResponseDTO response = queueEntryService.callNext(sessionId, loggedInUser.getId(),dto.actionMemberId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{entryId}/requeue")
    public ResponseEntity<QueueEntryResponseDTO> requeueEntry(
            @PathVariable String entryId,
            @RequestBody @Valid QueueSessionActionDTO dto,
            @AuthenticationPrincipal User loggedUser) {

        QueueEntryResponseDTO response = queueEntryService.requeueEntry(dto.sessionId(), entryId, loggedUser.getId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{entryId}/start")
    public ResponseEntity<QueueEntryResponseDTO> startService(
            @PathVariable String entryId,
            @RequestBody @Valid QueueSessionActionDTO dto,
            @AuthenticationPrincipal User loggedInUser) {

        QueueEntryResponseDTO response = queueEntryService.startService(dto.sessionId(), entryId, loggedInUser.getId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{entryId}/finish")
    public ResponseEntity<Void> finishService(
            @PathVariable String entryId,
            @AuthenticationPrincipal User loggedInUser) {

        queueEntryService.finishService(entryId, loggedInUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{entryId}/cancel")
    public ResponseEntity<Void> cancelEntry(
            @PathVariable String entryId,
            @AuthenticationPrincipal User loggedInUser) {

        queueEntryService.cancelEntry(entryId, loggedInUser.getId());
        return ResponseEntity.noContent().build();
    }
}
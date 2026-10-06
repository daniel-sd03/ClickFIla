package sodresoftwares.barbearia.controllers.team;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sodresoftwares.barbearia.dto.team.CreateTeamInviteDTO;
import sodresoftwares.barbearia.dto.team.TeamInviteResponseDTO;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.services.team.TeamInviteService;

import java.util.List;

@RestController
@RequestMapping("/team-invites")
@RequiredArgsConstructor
public class TeamInviteController {

    private final TeamInviteService teamInviteService;

    @PostMapping
    public ResponseEntity<Void> sendInvite(
            @AuthenticationPrincipal User loggedUser,
            @Valid @RequestBody CreateTeamInviteDTO dto) {
        teamInviteService.sendInvite(loggedUser.getId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/me")
    public ResponseEntity<List<TeamInviteResponseDTO>> getMyPendingInvites(
            @AuthenticationPrincipal User loggedUser) {
        List<TeamInviteResponseDTO> invites = teamInviteService.getMyPendingInvites(loggedUser.getLogin());
        return ResponseEntity.ok(invites);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<Void> acceptInvite(
            @PathVariable("id") String inviteId,
            @AuthenticationPrincipal User loggedUser) {
        teamInviteService.acceptInvite(inviteId, loggedUser.getId(), loggedUser.getLogin());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<Void> declineInvite(
            @PathVariable("id") String inviteId,
            @AuthenticationPrincipal User loggedUser) {
        teamInviteService.declineInvite(inviteId, loggedUser.getLogin());
        return ResponseEntity.ok().build();
    }
}
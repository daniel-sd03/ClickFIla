package sodresoftwares.barbearia.controllers.business;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sodresoftwares.barbearia.dto.business.BusinessResponseDTO;
import sodresoftwares.barbearia.dto.business.CreateBusinessDTO;
import sodresoftwares.barbearia.dto.business.UpdateBusinessDTO;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.services.business.BusinessService;

@RestController
@RequestMapping("/businesses")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessService businessService;

    @GetMapping("/me")
    public ResponseEntity<BusinessResponseDTO> getMyBusinessProfile(
            @AuthenticationPrincipal User loggedInUser) {
        BusinessResponseDTO profile = businessService.getMyBusinessProfile(loggedInUser.getId());
        return ResponseEntity.ok(profile);
    }

    @PostMapping
    public ResponseEntity<Void> createBusiness(
            @AuthenticationPrincipal User loggedInUser,
            @RequestBody @Valid CreateBusinessDTO data) {
        businessService.createBusiness(loggedInUser.getId(), data);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PatchMapping("/me")
    public ResponseEntity<BusinessResponseDTO> updateMyBusinessProfile(
            @AuthenticationPrincipal User loggedInUser,
            @Valid @RequestBody UpdateBusinessDTO dto) {

        BusinessResponseDTO updatedProfile = businessService.updateBusinessProfile(loggedInUser.getId(), dto);
        return ResponseEntity.ok(updatedProfile);
    }
}
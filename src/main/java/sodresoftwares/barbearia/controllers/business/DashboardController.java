package sodresoftwares.barbearia.controllers.business;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sodresoftwares.barbearia.dto.business.BusinessDashboardDTO;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.services.business.DashboardService;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/professional")
    public ResponseEntity<BusinessDashboardDTO> getMyDashboard(
            @AuthenticationPrincipal User loggedInUser) {
        BusinessDashboardDTO dashboard = dashboardService.getProfessionalDashboard(loggedInUser);
        return ResponseEntity.ok(dashboard);
    }
}
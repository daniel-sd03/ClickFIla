package sodresoftwares.barbearia.controllers.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sodresoftwares.barbearia.dto.billing.PlanResponseDTO;
import sodresoftwares.barbearia.services.billing.PlanService;

import java.util.List;

@RestController
@RequestMapping("/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @GetMapping
    public ResponseEntity<List<PlanResponseDTO>> listPlans() {
        return ResponseEntity.ok(planService.getAllActivePlans());
    }
}
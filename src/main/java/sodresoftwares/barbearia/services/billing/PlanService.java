package sodresoftwares.barbearia.services.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.dto.billing.PlanResponseDTO;
import sodresoftwares.barbearia.repositories.billing.PlanRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    private final PlanRepository planRepository;

    public List<PlanResponseDTO> getAllActivePlans() {
        return planRepository.findByIsActiveTrue()
                .stream()
                .map(PlanResponseDTO::fromEntity)
                .toList();
    }
}
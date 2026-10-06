package sodresoftwares.barbearia.dto.business;

import sodresoftwares.barbearia.dto.user.UserResponseDTO;
import sodresoftwares.barbearia.model.business.Business;

public record BusinessResponseDTO(
        String id,
        String name,
        Boolean isActive,
        UserResponseDTO user
) {
    public static BusinessResponseDTO fromEntity(Business business) {
        return new BusinessResponseDTO(
                business.getId(),
                business.getName(),
                business.getIsActive(),
                business.getUser() != null ? UserResponseDTO.fromEntity(business.getUser()) : null
        );
    }
}
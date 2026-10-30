package sodresoftwares.barbearia.controllers.billing;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sodresoftwares.barbearia.dto.billing.ChangePlanDTO;
import sodresoftwares.barbearia.dto.billing.CheckoutRequest;
import sodresoftwares.barbearia.dto.billing.SubscriptionResponseDTO;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.services.billing.SubscriptionService;

import java.util.Map;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/checkout")
    public ResponseEntity<Void> processCheckout(
            @AuthenticationPrincipal User loggedInUser,
            @Valid @RequestBody CheckoutRequest request) {
        subscriptionService.processTransparentCheckout(loggedInUser.getId(), request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/credit-card")
    public ResponseEntity<Void> updateCreditCard(
            @AuthenticationPrincipal User loggedInUser,
            @RequestBody @Valid CheckoutRequest request) {
        subscriptionService.updateCreditCard(loggedInUser.getId(), request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cancel/{businessId}")
    public ResponseEntity<Void> cancelSubscription(
            @PathVariable String businessId) {
        subscriptionService.cancelSubscription(businessId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/plan")
    public ResponseEntity<Void> changePlan(
            @Valid @RequestBody ChangePlanDTO request,
            @AuthenticationPrincipal User loggedInUser) {

        subscriptionService.changePlan(loggedInUser.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<SubscriptionResponseDTO> getMySubscription(
            @AuthenticationPrincipal User loggedInUser) {
        SubscriptionResponseDTO response = subscriptionService.getMySubscriptionDetails(loggedInUser.getId());
        return ResponseEntity.ok(response);
    }
}
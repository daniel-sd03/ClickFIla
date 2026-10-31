package sodresoftwares.barbearia.controllers.billing;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sodresoftwares.barbearia.dto.billing.ChangePlanDTO;
import sodresoftwares.barbearia.dto.billing.CheckoutRequestDTO;
import sodresoftwares.barbearia.dto.billing.CheckoutResponseDTO;
import sodresoftwares.barbearia.dto.billing.SubscriptionResponseDTO;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.services.billing.SubscriptionService;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponseDTO> processCheckout(
            @Valid @RequestBody CheckoutRequestDTO request,
            @AuthenticationPrincipal User loggedInUser) {

        CheckoutResponseDTO response = subscriptionService.processTransparentCheckout(loggedInUser.getId(), request);

        if (response != null) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.noContent().build();
        }
    }

    @PutMapping("/payment-method")
    public ResponseEntity<CheckoutResponseDTO> updatePaymentMethod(
            @AuthenticationPrincipal User loggedInUser,
            @RequestBody @Valid CheckoutRequestDTO request) {
        CheckoutResponseDTO response = subscriptionService.updatePaymentMethod(loggedInUser.getId(), request);
        if (response != null) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.noContent().build();
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

    @GetMapping("/pix-qr-code")
    public ResponseEntity<CheckoutResponseDTO> getPendingPixQrCode(
            @AuthenticationPrincipal User loggedInUser) {
        CheckoutResponseDTO response = subscriptionService.getPendingPixQrCode(loggedInUser.getId());
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<SubscriptionResponseDTO> getMySubscription(
            @AuthenticationPrincipal User loggedInUser) {
        SubscriptionResponseDTO response = subscriptionService.getMySubscriptionDetails(loggedInUser.getId());
        return ResponseEntity.ok(response);
    }
}
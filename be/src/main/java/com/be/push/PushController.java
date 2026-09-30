package com.be.push;

import com.be.global.security.MemberPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {
    private final PushSubscriptionService service;
    private final PushProperties properties;
    public record Config(boolean enabled, String publicKey) {}
    @GetMapping("/config")
    public ResponseEntity<Config> config() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Config(properties.enabled(), properties.enabled() ? properties.publicKey() : null));
    }
    @PutMapping("/subscriptions")
    public PushSubscriptionResponse register(@AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody PushSubscriptionRequest request) { return service.register(principal.memberId(), request); }
    @GetMapping("/subscriptions/{id}")
    public PushSubscriptionResponse get(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable @Positive Long id) {
        return service.get(principal.memberId(), id);
    }
    @DeleteMapping("/subscriptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable @Positive Long id) {
        service.disable(principal.memberId(), id);
    }
}

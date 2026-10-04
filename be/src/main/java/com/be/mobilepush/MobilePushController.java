package com.be.mobilepush;

import com.be.global.security.MemberPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile/push/subscriptions")
@RequiredArgsConstructor
public class MobilePushController {
    private final MobilePushSubscriptionService service;
    @PutMapping("/binding")
    public ResponseEntity<MobilePushDtos.Response> bind(@AuthenticationPrincipal MemberPrincipal member,
            @RequestHeader("X-Installation-Secret") String secret, @Valid @RequestBody MobilePushDtos.Binding request) {
        return response(service.bind(member.memberId(), secret, request));
    }
    @PutMapping("/{id}")
    public ResponseEntity<MobilePushDtos.Response> register(@AuthenticationPrincipal MemberPrincipal member, @PathVariable @Positive Long id,
            @RequestHeader("X-Installation-Secret") String secret, @Valid @RequestBody MobilePushDtos.Registration request) {
        return response(service.register(member.memberId(), id, secret, request));
    }
    @GetMapping("/{id}")
    public ResponseEntity<MobilePushDtos.Response> get(@AuthenticationPrincipal MemberPrincipal member, @PathVariable @Positive Long id) {
        return response(service.get(member.memberId(), id));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disable(@AuthenticationPrincipal MemberPrincipal member, @PathVariable @Positive Long id,
            @RequestHeader("X-Installation-Secret") String secret, @RequestParam @PositiveOrZero long version) {
        service.disable(member.memberId(), id, secret, version);
        return ResponseEntity.noContent().build();
    }
    private ResponseEntity<MobilePushDtos.Response> response(MobilePushDtos.Response result) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}

package com.be.exam.controller;

import com.be.exam.dto.*;
import com.be.exam.service.ExamAttemptService;
import com.be.global.security.MemberPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 요청 회원 ID를 받지 않고 JWT 인증 주체만 Service로 전달
@RestController
@RequiredArgsConstructor
public class ExamAttemptController {
    private final ExamAttemptService service;
    @PostMapping("/api/enrollments/{enrollmentId}/exam-attempts")
    public ResponseEntity<AttemptResponse> start(@PathVariable @Positive Long enrollmentId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        var result = service.start(enrollmentId, principal);
        return result.created() ? ResponseEntity.created(URI.create("/api/exam-attempts/" + result.attempt().attemptId())).body(result.attempt())
                : ResponseEntity.ok(result.attempt());
    }
    @GetMapping("/api/enrollments/{enrollmentId}/exam-attempts")
    public List<AttemptResponse> history(@PathVariable @Positive Long enrollmentId, @AuthenticationPrincipal MemberPrincipal principal) {
        return service.history(enrollmentId, principal);
    }
    @GetMapping("/api/exam-attempts/{attemptId}")
    public AttemptPaperResponse paper(@PathVariable @Positive Long attemptId, @AuthenticationPrincipal MemberPrincipal principal) {
        return service.paper(attemptId, principal);
    }
    @PutMapping("/api/exam-attempts/{attemptId}/answers/{questionId}")
    public AnswerResponse answer(@PathVariable @Positive Long attemptId, @PathVariable @Positive Long questionId,
            @AuthenticationPrincipal MemberPrincipal principal, @RequestBody @Valid AnswerSaveRequest request) {
        return service.saveAnswer(attemptId, questionId, principal, request);
    }
    @PostMapping("/api/exam-attempts/{attemptId}/submit")
    public AttemptResponse submit(@PathVariable @Positive Long attemptId, @AuthenticationPrincipal MemberPrincipal principal) {
        return service.submit(attemptId, principal);
    }
    @GetMapping("/api/exam-attempts/{attemptId}/result")
    public AttemptResponse result(@PathVariable @Positive Long attemptId, @AuthenticationPrincipal MemberPrincipal principal) {
        return service.result(attemptId, principal);
    }
}

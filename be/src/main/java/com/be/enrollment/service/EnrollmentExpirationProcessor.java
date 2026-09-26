package com.be.enrollment.service;

import com.be.enrollment.repository.EnrollmentRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 한 건의 실패가 다른 수강에 전파되지 않도록 독립 트랜잭션을 제공하는 내부 처리기
@Service
@RequiredArgsConstructor
public class EnrollmentExpirationProcessor {
    private final EnrollmentRepository enrollments;
    private final com.be.notification.service.NotificationService notifications;

    // 후보 조회 이후 바뀐 상태를 새 영속성 컨텍스트에서 재검증하고 @Version으로 저장
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(Long enrollmentId, LocalDate today) {
        var enrollment = enrollments.findById(enrollmentId);
        if (enrollment.isEmpty() || !enrollment.get().expireIfOverdue(today)) return false;
        enrollments.flush();
        notifications.notify(enrollment.get(), com.be.notification.enums.NotificationType.ENROLLMENT_EXPIRED);
        return true;
    }
}

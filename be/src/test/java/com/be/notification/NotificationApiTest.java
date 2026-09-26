package com.be.notification;

import com.be.assignment.AssignmentFixtures;
import com.be.course.enums.CourseStatus;
import com.be.enrollment.entity.Enrollment;
import com.be.global.config.*;
import com.be.global.exception.GlobalExceptionHandler;
import com.be.global.security.*;
import com.be.member.enums.Role;
import com.be.notification.controller.NotificationController;
import com.be.notification.entity.Notification;
import com.be.notification.enums.NotificationType;
import com.be.notification.repository.NotificationRepository;
import com.be.notification.service.NotificationService;
import com.be.security.JwtTestSupport;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Security/Controller/Service는 실제 연결하고 저장소만 대체한다.
@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, MemberSupportConfig.class, GlobalExceptionHandler.class, NotificationService.class})
class NotificationApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean NotificationRepository repository;
    @MockitoBean JwtTokenProvider tokens;
    @MockitoBean MemberAuthenticationService members;
    Notification item;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", JwtTestSupport::secret);
        registry.add("jwt.access-token-ttl-seconds", () -> 300);
        registry.add("jwt.refresh-token-ttl-seconds", () -> 3600);
    }
    @BeforeEach void setUp() {
        var enrollment = Enrollment.manual(AssignmentFixtures.member(2L), AssignmentFixtures.course(CourseStatus.OPEN), LocalDateTime.now());
        ReflectionTestUtils.setField(enrollment, "id", 10L);
        item = Notification.create(enrollment, NotificationType.ENROLLMENT_ASSIGNED, LocalDateTime.of(2026, 9, 26, 0, 0));
        ReflectionTestUtils.setField(item, "id", 1L);
    }
    @Test void listIsOwnedPagedAndNewestFirstWithStableIdTieBreaker() throws Exception {
        when(repository.findByMemberId(eq(2L), any())).thenAnswer(c -> {
            Pageable page = c.getArgument(1);
            assertThat(page.getPageNumber()).isZero();
            assertThat(page.getPageSize()).isEqualTo(20);
            assertThat(page.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
            return new PageImpl<>(List.of(item), page, 1);
        });
        mvc.perform(get("/api/notifications/me").param("memberId", "999").with(as(2L, Role.EMPLOYEE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].notificationId").value(1))
                .andExpect(jsonPath("$.content[0].relatedEnrollmentId").value(10))
                .andExpect(jsonPath("$.content[0].readAt").isEmpty())
                .andExpect(jsonPath("$.content[0].member").doesNotExist()).andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void unreadCountUsesDedicatedOwnedQuery() throws Exception {
        when(repository.countByMemberIdAndReadAtIsNull(2L)).thenReturn(123L);
        mvc.perform(get("/api/notifications/me/unread-count").with(as(2L, Role.EMPLOYEE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(123));
        verify(repository).countByMemberIdAndReadAtIsNull(2L);
        verifyNoMoreInteractions(repository);
    }
    @Test void readIsIdempotentAndKeepsFirstTimestamp() throws Exception {
        var firstRead = LocalDateTime.of(2026, 9, 26, 1, 0);
        when(repository.markRead(eq(1L), eq(2L), any())).thenAnswer(c -> {
            if (item.getReadAt() != null) return 0;
            ReflectionTestUtils.setField(item, "readAt", firstRead);
            return 1;
        });
        when(repository.findByIdAndMemberId(1L, 2L)).thenReturn(Optional.of(item));
        for (int i = 0; i < 2; i++) mvc.perform(patch("/api/notifications/1/read").with(as(2L, Role.EMPLOYEE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.readAt").value("2026-09-26T01:00:00"));
        assertThat(item.getReadAt()).isEqualTo(firstRead);
    }
    @Test void readAllOnlyTargetsAuthenticatedMember() throws Exception {
        mvc.perform(patch("/api/notifications/me/read-all").with(as(2L, Role.EMPLOYEE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.readAt").isNotEmpty());
        verify(repository).markAllRead(eq(2L), any());
        verifyNoMoreInteractions(repository);
    }
    @Test void foreignNotificationIsHiddenEvenFromAdmin() throws Exception {
        for (Role role : List.of(Role.EMPLOYEE, Role.ADMIN)) {
            mvc.perform(patch("/api/notifications/1/read").with(as(3L, role)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        }
        verify(repository, never()).markRead(eq(1L), eq(2L), any());
        assertThat(item.getReadAt()).isNull();
    }
    @ParameterizedTest @ValueSource(strings = {"/me", "/me/unread-count", "/1/read", "/me/read-all"})
    void anonymousIsUnauthorized(String path) throws Exception {
        mvc.perform((path.endsWith("read") || path.endsWith("read-all") ? patch("/api/notifications" + path) : get("/api/notifications" + path)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }
    @Test void invalidPaginationAndIdAreBadRequest() throws Exception {
        mvc.perform(get("/api/notifications/me?page=-1&size=101").with(as(2L, Role.EMPLOYEE))).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/notifications/0/read").with(as(2L, Role.EMPLOYEE))).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
    @Test void adminWithoutEmployeeCanReadOwnEmptyInbox() throws Exception {
        when(repository.findByMemberId(eq(3L), any())).thenReturn(Page.empty());
        mvc.perform(get("/api/notifications/me").with(as(3L, Role.ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }
    private RequestPostProcessor as(Long id, Role role) {
        var principal = new MemberPrincipal(id, "test@example.com", Set.of(role));
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }
}

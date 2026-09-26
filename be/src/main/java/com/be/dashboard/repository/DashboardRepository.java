package com.be.dashboard.repository;

import com.be.course.enums.*;
import com.be.enrollment.entity.Enrollment;
import com.be.enrollment.enums.EnrollmentStatus;
import com.be.member.enums.*;
import java.time.*;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// 읽기 전용 집계 저장소. 전체 Entity 로딩이나 행별 추가 조회를 하지 않는다.
public interface DashboardRepository extends Repository<Enrollment, Long> {
    @Query("select count(m) from Member m where :role member of m.roles and m.status in :statuses")
    long employeeCount(@Param("role") Role role, @Param("statuses") List<MemberStatus> statuses);

    @Query("select count(c) from Course c where c.status = :status")
    long courseCount(@Param("status") CourseStatus status);

    @Query("select e.status as status, count(e) as count from Enrollment e group by e.status")
    List<StatusCount> statusCounts();

    // 서울 날짜 기준 양 끝을 포함한다. null 마감일은 비교 조건에서 제외된다.
    @Query("select count(e) from Enrollment e where e.status in :statuses and e.dueDate >= :today and e.dueDate <= :through")
    long dueSoonCount(@Param("statuses") List<EnrollmentStatus> statuses,
                      @Param("today") LocalDate today, @Param("through") LocalDate through);

    // 과정과 수강을 한 번에 집계. 수강이 없는 OPEN 과정도 LEFT JOIN으로 포함한다.
    @Query("""
            select c.id as courseId, c.title as title, c.courseType as type,
                   count(e.id) as enrollmentCount,
                   sum(case when e.status = com.be.enrollment.enums.EnrollmentStatus.COMPLETED then 1L else 0L end) as completedCount
            from Course c left join Enrollment e on e.course = c
            where c.status = com.be.course.enums.CourseStatus.OPEN
            group by c.id, c.title, c.courseType, c.createdAt
            order by c.createdAt desc, c.id desc
            """)
    List<CourseCount> activeCourses(Pageable pageable);

    @Query("""
            select e.id as enrollmentId, m.name as memberName, d.name as departmentName,
                   c.title as courseTitle, e.status as status, e.assignedAt as assignedAt, e.completedAt as completedAt
            from Enrollment e join e.member m join e.course c left join m.department d
            where e.status = com.be.enrollment.enums.EnrollmentStatus.COMPLETED and e.completedAt is not null
            order by e.completedAt desc, e.id desc
            """)
    List<RecentEnrollment> recentCompletions(Pageable pageable);

    @Query("""
            select e.id as enrollmentId, m.name as memberName, d.name as departmentName,
                   c.title as courseTitle, e.status as status, e.assignedAt as assignedAt, e.completedAt as completedAt
            from Enrollment e join e.member m join e.course c left join m.department d
            order by e.assignedAt desc, e.id desc
            """)
    List<RecentEnrollment> recentAssignments(Pageable pageable);

    interface StatusCount {
        EnrollmentStatus getStatus();
        long getCount();
    }
    interface CourseCount {
        Long getCourseId();
        String getTitle();
        CourseType getType();
        long getEnrollmentCount();
        long getCompletedCount();
    }
    interface RecentEnrollment {
        Long getEnrollmentId();
        String getMemberName();
        String getDepartmentName();
        String getCourseTitle();
        EnrollmentStatus getStatus();
        LocalDateTime getAssignedAt();
        LocalDateTime getCompletedAt();
    }
}

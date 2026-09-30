package com.be.retraining;

import com.be.assignment.AssignmentFixtures;
import com.be.course.enums.CourseStatus;
import com.be.global.exception.*;
import com.be.retraining.entity.RetrainingPolicy;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RetrainingPolicyTest {
    private RetrainingPolicy policy(LocalDate first) {
        return RetrainingPolicy.create(AssignmentFixtures.course(CourseStatus.OPEN), "교육 2026", true, true, false, 1, first, 10, 7);
    }
    @Test void anchoredMonthEndDoesNotDriftAndLeapDayIsPreserved() {
        var p = policy(LocalDate.of(2028, 1, 31));
        assertThat(p.dates(2).startDate()).isEqualTo("2028-02-29");
        assertThat(p.dates(3).startDate()).isEqualTo("2028-03-31");
        assertThat(p.dates(2).endDate()).isEqualTo("2028-03-09");
        assertThat(p.dates(2).generationDate()).isEqualTo("2028-02-22");
        p.advance();
        assertThat(p.getNextGenerationDate()).isEqualTo("2028-02-22");
        assertThat(p.title(3)).isEqualTo("교육 2026 2028-03 · 3회차");
    }
    @Test void invalidScheduleAndDbDateRangeAreRejected() {
        var source = AssignmentFixtures.course(CourseStatus.OPEN);
        for (int months : new int[]{0, -1, 1201}) assertThatThrownBy(() -> RetrainingPolicy.create(source, "교육", true, true, false,
                months, LocalDate.of(2026, 1, 1), 10, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> policy(LocalDate.of(1000, 1, 1))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> policy(LocalDate.of(9999, 12, 31))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> policy(LocalDate.now()).dates(0)).isInstanceOf(BusinessException.class);
    }
    @Test void consumedOccurrencesFreezeScheduleButAllowTitleAndSwitches() {
        var p = policy(LocalDate.of(2026, 1, 31));
        p.advance();
        assertThatThrownBy(() -> p.configure(p.getSourceCourse(), "제목", true, true, false, 12,
                p.getFirstStartDate(), 10, 7)).isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RETRAINING_SCHEDULE_LOCKED));
        p.configure(p.getSourceCourse(), "새 제목", false, false, true, 1, p.getFirstStartDate(), 10, 7);
        assertThat(p.isEnabled()).isFalse();
        assertThat(p.isAutoOpen()).isTrue();
    }
}

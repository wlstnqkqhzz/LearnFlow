-- LearnFlow local 개발·시연 환경 통합 Seed
-- 실행 전제: docs/LearnFlow.sql로 테이블이 생성되어 있어야 한다.
-- 기본 조직/계정 Seed를 먼저 적용하고, 이어서 과정·학습 이력이 포함된 시연 Seed를 적용한다.
-- 공통 비밀번호: Password123!
-- password_hash는 현재 Spring Security 설정
-- Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8() 형식으로 미리 생성했다.
-- 운영 환경에서는 절대 사용하지 않는다.

USE learnflow;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

START TRANSACTION;

-- ============================================================
-- 1. Department Seed
-- 고정 code가 이미 있으면 이름, 계층, 활성 상태를 덮어쓰지 않는다.
-- ============================================================

INSERT INTO departments
    (code, name, parent_department_id, is_active, created_at, updated_at)
SELECT 'DEV_HQ', '개발본부', NULL, TRUE, NOW(6), NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM departments WHERE code = 'DEV_HQ'
);

INSERT INTO departments
    (code, name, parent_department_id, is_active, created_at, updated_at)
SELECT 'BACKEND_TEAM', '백엔드팀', parent.id, TRUE, NOW(6), NOW(6)
FROM departments parent
WHERE parent.code = 'DEV_HQ'
  AND NOT EXISTS (
      SELECT 1 FROM departments WHERE code = 'BACKEND_TEAM'
  );

INSERT INTO departments
    (code, name, parent_department_id, is_active, created_at, updated_at)
SELECT 'FRONTEND_TEAM', '프론트엔드팀', parent.id, TRUE, NOW(6), NOW(6)
FROM departments parent
WHERE parent.code = 'DEV_HQ'
  AND NOT EXISTS (
      SELECT 1 FROM departments WHERE code = 'FRONTEND_TEAM'
  );

INSERT INTO departments
    (code, name, parent_department_id, is_active, created_at, updated_at)
SELECT 'MANAGEMENT_HQ', '경영지원본부', NULL, TRUE, NOW(6), NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM departments WHERE code = 'MANAGEMENT_HQ'
);

INSERT INTO departments
    (code, name, parent_department_id, is_active, created_at, updated_at)
SELECT 'HR_TEAM', '인사팀', parent.id, TRUE, NOW(6), NOW(6)
FROM departments parent
WHERE parent.code = 'MANAGEMENT_HQ'
  AND NOT EXISTS (
      SELECT 1 FROM departments WHERE code = 'HR_TEAM'
  );

-- ============================================================
-- 2. JobPosition Seed
-- ============================================================

INSERT INTO job_positions (code, name, is_active, created_at, updated_at)
SELECT 'BACKEND_DEVELOPER', '백엔드 개발자', TRUE, NOW(6), NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM job_positions WHERE code = 'BACKEND_DEVELOPER'
);

INSERT INTO job_positions (code, name, is_active, created_at, updated_at)
SELECT 'FRONTEND_DEVELOPER', '프론트엔드 개발자', TRUE, NOW(6), NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM job_positions WHERE code = 'FRONTEND_DEVELOPER'
);

INSERT INTO job_positions (code, name, is_active, created_at, updated_at)
SELECT 'INSTRUCTOR', '교육 강사', TRUE, NOW(6), NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM job_positions WHERE code = 'INSTRUCTOR'
);

INSERT INTO job_positions (code, name, is_active, created_at, updated_at)
SELECT 'HR_MANAGER', '인사 담당자', TRUE, NOW(6), NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM job_positions WHERE code = 'HR_MANAGER'
);

-- ============================================================
-- 3. Member Seed
-- 이번 실행에서 새로 생성할 계정만 임시 테이블에 담는다.
-- 기존 email 또는 employee_number가 있으면 비밀번호, 역할, 상태를 변경하지 않는다.
-- ============================================================

DROP TEMPORARY TABLE IF EXISTS tmp_learnflow_local_members;

CREATE TEMPORARY TABLE tmp_learnflow_local_members (
    employee_number VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    department_code VARCHAR(50) NOT NULL,
    job_position_code VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    hire_date DATE NOT NULL,
    resigned_at DATETIME(6) NULL,
    add_admin BOOLEAN NOT NULL,
    add_instructor BOOLEAN NOT NULL,
    PRIMARY KEY (employee_number),
    UNIQUE KEY uk_tmp_learnflow_local_members_email (email)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_learnflow_local_members
    (employee_number, email, name, department_code, job_position_code,
     status, hire_date, resigned_at, add_admin, add_instructor)
SELECT seed.employee_number, seed.email, seed.name, seed.department_code,
       seed.job_position_code, seed.status, seed.hire_date, seed.resigned_at,
       seed.add_admin, seed.add_instructor
FROM (
    SELECT 'LOCAL-ADMIN' employee_number, 'admin@learnflow.local' email,
           'LearnFlow 관리자' name, 'HR_TEAM' department_code, 'HR_MANAGER' job_position_code,
           'ACTIVE' status, CURRENT_DATE - INTERVAL 3 YEAR hire_date,
           NULL resigned_at, TRUE add_admin, FALSE add_instructor
    UNION ALL
    SELECT 'LOCAL-INSTRUCTOR-1', 'instructor1@learnflow.local', '교육 강사1',
           'MANAGEMENT_HQ', 'INSTRUCTOR', 'ACTIVE', CURRENT_DATE - INTERVAL 2 YEAR,
           NULL, FALSE, TRUE
    UNION ALL
    SELECT 'LOCAL-INSTRUCTOR-2', 'instructor2@learnflow.local', '교육 강사2',
           'HR_TEAM', 'INSTRUCTOR', 'ACTIVE', CURRENT_DATE - INTERVAL 10 MONTH,
           NULL, FALSE, TRUE
    UNION ALL
    SELECT 'LOCAL-EMPLOYEE-1', 'employee1@learnflow.local', '백엔드 직원1',
           'BACKEND_TEAM', 'BACKEND_DEVELOPER', 'ACTIVE', CURRENT_DATE - INTERVAL 2 YEAR,
           NULL, FALSE, FALSE
    UNION ALL
    SELECT 'LOCAL-EMPLOYEE-2', 'employee2@learnflow.local', '백엔드 직원2',
           'BACKEND_TEAM', 'BACKEND_DEVELOPER', 'ACTIVE', CURRENT_DATE - INTERVAL 6 MONTH,
           NULL, FALSE, FALSE
    UNION ALL
    SELECT 'LOCAL-EMPLOYEE-3', 'employee3@learnflow.local', '프론트엔드 직원1',
           'FRONTEND_TEAM', 'FRONTEND_DEVELOPER', 'ACTIVE', CURRENT_DATE - INTERVAL 1 YEAR,
           NULL, FALSE, FALSE
    UNION ALL
    SELECT 'LOCAL-EMPLOYEE-4', 'employee4@learnflow.local', '프론트엔드 직원2',
           'FRONTEND_TEAM', 'FRONTEND_DEVELOPER', 'ACTIVE', CURRENT_DATE - INTERVAL 3 MONTH,
           NULL, FALSE, FALSE
    UNION ALL
    SELECT 'LOCAL-EMPLOYEE-5', 'employee5@learnflow.local', '신입 인사 직원',
           'HR_TEAM', 'HR_MANAGER', 'ACTIVE', CURRENT_DATE - INTERVAL 14 DAY,
           NULL, FALSE, FALSE
    UNION ALL
    SELECT 'LOCAL-LEAVE', 'leave@learnflow.local', '휴직 직원',
           'BACKEND_TEAM', 'BACKEND_DEVELOPER', 'ON_LEAVE', CURRENT_DATE - INTERVAL 1 YEAR,
           NULL, FALSE, FALSE
    UNION ALL
    SELECT 'LOCAL-RESIGNED', 'resigned@learnflow.local', '퇴사 직원',
           'FRONTEND_TEAM', 'FRONTEND_DEVELOPER', 'RESIGNED', CURRENT_DATE - INTERVAL 2 YEAR,
           UTC_TIMESTAMP(6), FALSE, FALSE
    UNION ALL
    SELECT 'LOCAL-MULTI-ROLE', 'multi-role@learnflow.local', '복수 역할 직원',
           'MANAGEMENT_HQ', 'INSTRUCTOR', 'ACTIVE', CURRENT_DATE - INTERVAL 8 MONTH,
           NULL, FALSE, TRUE
) seed
WHERE NOT EXISTS (
          SELECT 1 FROM members m WHERE m.email = seed.email
      )
  AND NOT EXISTS (
          SELECT 1 FROM members m WHERE m.employee_number = seed.employee_number
      );

-- Password123!를 PBKDF2WithHmacSHA256, 310000 iterations로 인코딩한 값이다.
INSERT INTO members
    (employee_number, email, password_hash, name, department_id,
     job_position_id, status, hire_date, resigned_at, created_at, updated_at)
SELECT seed.employee_number,
       seed.email,
       '0102030405060708090a0b0c0d0e0f10b2d1913b4892f657c071dfccfba0da0785b8497d61f161e95bab198cddd1ae86',
       seed.name,
       department.id,
       position.id,
       seed.status,
       seed.hire_date,
       seed.resigned_at,
       NOW(6),
       NOW(6)
FROM tmp_learnflow_local_members seed
JOIN departments department
  ON department.code = seed.department_code
JOIN job_positions position
  ON position.code = seed.job_position_code;

-- 모든 회원은 기존 도메인 규칙과 동일하게 EMPLOYEE 역할을 기본으로 가진다.
INSERT INTO member_roles (member_id, role)
SELECT member.id, 'EMPLOYEE'
FROM tmp_learnflow_local_members seed
JOIN members member ON member.email = seed.email;

INSERT INTO member_roles (member_id, role)
SELECT member.id, 'ADMIN'
FROM tmp_learnflow_local_members seed
JOIN members member ON member.email = seed.email
WHERE seed.add_admin = TRUE;

INSERT INTO member_roles (member_id, role)
SELECT member.id, 'INSTRUCTOR'
FROM tmp_learnflow_local_members seed
JOIN members member ON member.email = seed.email
WHERE seed.add_instructor = TRUE;

DROP TEMPORARY TABLE tmp_learnflow_local_members;

COMMIT;

-- 확인용 조회. 비밀번호 해시는 출력하지 않는다.
SELECT d.code, d.name, parent.code AS parent_code, d.is_active
FROM departments d
LEFT JOIN departments parent ON parent.id = d.parent_department_id
WHERE d.code IN ('DEV_HQ', 'BACKEND_TEAM', 'FRONTEND_TEAM', 'MANAGEMENT_HQ', 'HR_TEAM')
ORDER BY d.id;

SELECT j.code, j.name, j.is_active
FROM job_positions j
WHERE j.code IN ('BACKEND_DEVELOPER', 'FRONTEND_DEVELOPER', 'INSTRUCTOR', 'HR_MANAGER')
ORDER BY j.id;

SELECT m.employee_number, m.email, m.name, m.status, m.hire_date,
       d.name AS department_name, j.name AS job_position_name,
       GROUP_CONCAT(mr.role ORDER BY mr.role SEPARATOR ', ') AS roles
FROM members m
JOIN departments d ON d.id = m.department_id
JOIN job_positions j ON j.id = m.job_position_id
JOIN member_roles mr ON mr.member_id = m.id
WHERE m.email LIKE '%@learnflow.local'
GROUP BY m.id, m.employee_number, m.email, m.name, m.status, m.hire_date, d.name, j.name
ORDER BY m.id;


-- ============================================================
-- 4. Demo learning data
-- DEMO_ 조직/직무와 demo.*@learnflow.local은 시연 데이터의 예약 식별자다.
-- 기존 교육 시연 상태를 보존하기 위해 재실행 시 날짜/진도/읽음 상태를 갱신하지 않는다.
-- DEMO_SEED_V1 부서 코드는 완료 표식이며, 전체 시연 데이터는 한 트랜잭션으로 생성된다.
-- ============================================================

SET @learnflow_local_seed_confirm = 'LOCAL_ONLY';

-- 중도 오류로 남은 이 파일 전용 실행 도구만 정리한다. 업무 테이블/데이터는 삭제하지 않는다.
DROP PROCEDURE IF EXISTS learnflow_local_demo_seed_v1;
DELIMITER $$
CREATE PROCEDURE learnflow_local_demo_seed_v1()
SQL SECURITY INVOKER
seed: BEGIN
    DECLARE seed_now DATETIME(6);
    DECLARE seed_today DATE;
    DECLARE lock_acquired INT DEFAULT 0;
    DECLARE dev_id BIGINT;
    DECLARE backend_id BIGINT;
    DECLARE frontend_id BIGINT;
    DECLARE qa_id BIGINT;
    DECLARE management_id BIGINT;
    DECLARE hr_id BIGINT;
    DECLARE instructor_id BIGINT;
    DECLARE security_id BIGINT;
    DECLARE privacy_id BIGINT;
    DECLARE respect_id BIGINT;
    DECLARE onboarding_id BIGINT;
    DECLARE archive_id BIGINT;
    DECLARE rule_id BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        IF lock_acquired = 1 THEN DO RELEASE_LOCK('learnflow.local-demo-seed-v1'); END IF;
        RESIGNAL;
    END;

    IF DATABASE() <> 'learnflow' OR COALESCE(@learnflow_local_seed_confirm, '') <> 'LOCAL_ONLY' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Select the local learnflow database and explicitly confirm LOCAL_ONLY.';
    END IF;
    SELECT GET_LOCK('learnflow.local-demo-seed-v1', 10) INTO lock_acquired;
    IF COALESCE(lock_acquired, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Another seed is running. Retry later.';
    END IF;
    START TRANSACTION;
    IF EXISTS (SELECT 1 FROM departments WHERE code = 'DEMO_SEED_V1') THEN
        COMMIT;
        DO RELEASE_LOCK('learnflow.local-demo-seed-v1');
        SELECT 'Already seeded; existing data preserved.' AS result;
        LEAVE seed;
    END IF;
    IF EXISTS (SELECT 1 FROM members WHERE email LIKE 'demo.%@learnflow.local' OR LEFT(employee_number, 5) = 'DEMO-')
       OR EXISTS (SELECT 1 FROM departments WHERE LEFT(code, 5) = 'DEMO_')
       OR EXISTS (SELECT 1 FROM job_positions WHERE LEFT(code, 5) = 'DEMO_')
       OR EXISTS (SELECT 1 FROM courses WHERE LEFT(title, 5) = '[시연] ') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Reserved demo identifiers already exist without completion marker; no data changed.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'notifications') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Run docs/LearnFlow.sql first. No data changed.';
    END IF;
    SET seed_now = UTC_TIMESTAMP(6);
    SET seed_today = DATE(seed_now + INTERVAL 9 HOUR);

    -- 1. 6개 조직. 개발본부의 불변 코드를 완료 표식으로 겸용한다 (전체 트랜잭션 커밋 후만 보임).
    INSERT INTO departments(code,name,parent_department_id,is_active,created_at,updated_at)
    VALUES ('DEMO_SEED_V1','개발본부',NULL,TRUE,seed_now,seed_now);
    SET dev_id = LAST_INSERT_ID();
    INSERT INTO departments(code,name,parent_department_id,is_active,created_at,updated_at)
    VALUES ('DEMO_BACKEND','백엔드팀',dev_id,TRUE,seed_now,seed_now);
    SET backend_id = LAST_INSERT_ID();
    INSERT INTO departments(code,name,parent_department_id,is_active,created_at,updated_at)
    VALUES ('DEMO_FRONTEND','프론트엔드팀',dev_id,TRUE,seed_now,seed_now);
    SET frontend_id = LAST_INSERT_ID();
    INSERT INTO departments(code,name,parent_department_id,is_active,created_at,updated_at)
    VALUES ('DEMO_QA','QA팀',dev_id,TRUE,seed_now,seed_now);
    SET qa_id = LAST_INSERT_ID();
    INSERT INTO departments(code,name,parent_department_id,is_active,created_at,updated_at)
    VALUES ('DEMO_MANAGEMENT','경영지원본부',NULL,TRUE,seed_now,seed_now);
    SET management_id = LAST_INSERT_ID();
    INSERT INTO departments(code,name,parent_department_id,is_active,created_at,updated_at)
    VALUES ('DEMO_HR','인사팀',management_id,TRUE,seed_now,seed_now);
    SET hr_id = LAST_INSERT_ID();
    INSERT INTO job_positions(code,name,is_active,created_at,updated_at) VALUES
    ('DEMO_BACKEND','백엔드 개발자',TRUE,seed_now,seed_now),
    ('DEMO_FRONTEND','프론트엔드 개발자',TRUE,seed_now,seed_now),
    ('DEMO_QA','QA 엔지니어',TRUE,seed_now,seed_now),
    ('DEMO_INSTRUCTOR','교육 강사',TRUE,seed_now,seed_now),
    ('DEMO_HR','인사 담당자',TRUE,seed_now,seed_now);

    -- 2. 실제 PasswordEncoder 형식. 기존 회원 UPDATE 없음.
    INSERT INTO members(employee_number,email,password_hash,name,department_id,job_position_id,status,hire_date,resigned_at,created_at,updated_at)
    SELECT CONCAT('DEMO-',UPPER(x.account)), CONCAT('demo.',x.account,'@learnflow.local'),
        '0102030405060708090a0b0c0d0e0f10b2d1913b4892f657c071dfccfba0da0785b8497d61f161e95bab198cddd1ae86',
        x.name,x.department_id,j.id,x.status,seed_today - INTERVAL 30 DAY,
        IF(x.status='RESIGNED',seed_now - INTERVAL 1 DAY,NULL),seed_now,seed_now
    FROM (
        SELECT 'admin' account,'시연 관리자' name,hr_id department_id,'DEMO_HR' job,'ACTIVE' status
        UNION ALL SELECT 'instructor1','김강사',backend_id,'DEMO_INSTRUCTOR','ACTIVE'
        UNION ALL SELECT 'instructor2','이강사',frontend_id,'DEMO_INSTRUCTOR','ACTIVE'
        UNION ALL SELECT 'employee1','김민수',backend_id,'DEMO_BACKEND','ACTIVE'
        UNION ALL SELECT 'employee2','이서연',frontend_id,'DEMO_FRONTEND','ACTIVE'
        UNION ALL SELECT 'employee3','박지훈',qa_id,'DEMO_QA','ACTIVE'
        UNION ALL SELECT 'employee4','최유진',backend_id,'DEMO_BACKEND','ACTIVE'
        UNION ALL SELECT 'leave1','정휴직',hr_id,'DEMO_HR','ON_LEAVE'
        UNION ALL SELECT 'resigned1','한퇴사',qa_id,'DEMO_QA','RESIGNED'
    ) x JOIN job_positions j ON j.code=x.job;
    INSERT INTO member_roles(member_id,role)
    SELECT id,'EMPLOYEE' FROM members WHERE LEFT(employee_number,5)='DEMO-';
    INSERT INTO member_roles(member_id,role)
    SELECT id,IF(email='demo.admin@learnflow.local','ADMIN','INSTRUCTOR') FROM members
    WHERE email IN ('demo.admin@learnflow.local','demo.instructor1@learnflow.local','demo.instructor2@learnflow.local');
    SELECT id INTO instructor_id FROM members WHERE email='demo.instructor1@learnflow.local';

    -- 3. 실행일 기준 과정. 4 OPEN, 1 CLOSED, 1 DRAFT. 강사 미지정 과정도 포함.
    INSERT INTO courses(title,description,course_type,status,start_date,end_date,passing_progress_rate,instructor_id,created_at,updated_at)
    VALUES (CONCAT('[시연] 정보보안 필수교육 ',YEAR(seed_today)),'local 시연 교육','MANDATORY','OPEN',seed_today-INTERVAL 14 DAY,seed_today+INTERVAL 7 DAY,100,instructor_id,seed_now,seed_now);
    SET security_id=LAST_INSERT_ID();
    INSERT INTO courses(title,description,course_type,status,start_date,end_date,passing_progress_rate,instructor_id,created_at,updated_at)
    VALUES ('[시연] 개인정보보호 교육','local 시연 교육','MANDATORY','OPEN',seed_today-INTERVAL 14 DAY,seed_today+INTERVAL 14 DAY,100,NULL,seed_now,seed_now);
    SET privacy_id=LAST_INSERT_ID();
    INSERT INTO courses(title,description,course_type,status,start_date,end_date,passing_progress_rate,instructor_id,created_at,updated_at)
    VALUES ('[시연] 직장 내 괴롭힘 예방교육','local 시연 교육','MANDATORY','OPEN',seed_today-INTERVAL 14 DAY,seed_today+INTERVAL 3 DAY,100,NULL,seed_now,seed_now);
    SET respect_id=LAST_INSERT_ID();
    INSERT INTO courses(title,description,course_type,status,start_date,end_date,passing_progress_rate,instructor_id,created_at,updated_at)
    VALUES ('[시연] 신입사원 온보딩','local 시연 교육','MANDATORY','OPEN',seed_today-INTERVAL 14 DAY,seed_today+INTERVAL 30 DAY,100,instructor_id,seed_now,seed_now);
    SET onboarding_id=LAST_INSERT_ID();
    INSERT INTO courses(title,description,course_type,status,start_date,end_date,passing_progress_rate,instructor_id,created_at,updated_at)
    VALUES ('[시연] 지난 교육 이력','local 시연 교육','MANDATORY','CLOSED',seed_today-INTERVAL 60 DAY,seed_today-INTERVAL 1 DAY,100,NULL,seed_now-INTERVAL 60 DAY,seed_now);
    SET archive_id=LAST_INSERT_ID();
    INSERT INTO courses(title,description,course_type,status,start_date,end_date,passing_progress_rate,instructor_id,created_at,updated_at)
    VALUES ('[시연] 다음 교육 준비','local 시연 초안','OPTIONAL','DRAFT',NULL,NULL,100,NULL,seed_now,seed_now);

    -- 4. 각 운영/종료 과정에 필수 2개 + 선택 1개. 영상/문서는 교육 본문이 아닌 공개 시연 샘플.
    INSERT INTO course_contents(course_id,title,content_type,content_url,duration_seconds,sort_order,is_required,created_at,updated_at)
    SELECT c.id,x.title,x.kind,x.url,NULL,x.ord,x.required,c.created_at,seed_now
    FROM courses c CROSS JOIN (
        SELECT '학습 영상 (시연용 샘플)' title,'VIDEO' kind,'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4' url,1 ord,TRUE required
        UNION ALL SELECT '학습 문서 (시연용 샘플)','DOCUMENT','https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf',2,TRUE
        UNION ALL SELECT '추가 학습 자료','LINK','https://www.kisa.or.kr/',3,FALSE
    ) x WHERE c.id IN (security_id,privacy_id,respect_id,onboarding_id,archive_id);

    -- 5. 시험 두 개. TRUE_FALSE의 선택지 문자열은 기존 Validator 정책인 TRUE/FALSE를 사용.
    INSERT INTO exams(course_id,title,passing_score,max_attempts,created_at,updated_at)
    VALUES (security_id,'정보보안 확인 시험',70,2,seed_now,seed_now),(privacy_id,'개인정보보호 확인 시험',70,2,seed_now,seed_now);
    INSERT INTO questions(exam_id,question_text,question_type,score,sort_order,created_at,updated_at)
    SELECT e.id,x.body,x.kind,x.points,x.ord,seed_now,seed_now FROM exams e CROSS JOIN (
        SELECT '안전한 계정 관리 방법은?' body,'SINGLE_CHOICE' kind,40 points,1 ord
        UNION ALL SELECT '올바른 보안 실천을 모두 선택하세요.','MULTIPLE_CHOICE',30,2
        UNION ALL SELECT '개인정보는 필요한 범위에서만 사용해야 한다.','TRUE_FALSE',30,3
    ) x WHERE e.course_id IN (security_id,privacy_id);
    INSERT INTO question_choices(question_id,choice_text,is_correct,sort_order,created_at,updated_at)
    SELECT q.id,x.body,x.correct,x.ord,seed_now,seed_now FROM questions q JOIN exams e ON e.id=q.exam_id JOIN (
        SELECT 1 question_order,'비밀번호 공유 금지' body,TRUE correct,1 ord
        UNION ALL SELECT 1,'동료에게 비밀번호 전달',FALSE,2
        UNION ALL SELECT 2,'화면 잠금',TRUE,1
        UNION ALL SELECT 2,'의심 메일 신고',TRUE,2
        UNION ALL SELECT 2,'출처 불명 파일 실행',FALSE,3
        UNION ALL SELECT 3,'TRUE',TRUE,1
        UNION ALL SELECT 3,'FALSE',FALSE,2
    ) x ON x.question_order=q.sort_order WHERE e.course_id IN (security_id,privacy_id);

    -- 6. 자동 배정은 시연 백엔드팀 ACTIVE만. 나머지는 수동 배정.
    INSERT INTO assignment_rules(course_id,rule_type,department_id,job_position_id,new_employee_days,is_active,created_at,updated_at)
    VALUES (security_id,'DEPARTMENT',backend_id,NULL,NULL,TRUE,seed_now,seed_now);
    SET rule_id=LAST_INSERT_ID();
    INSERT INTO enrollments(member_id,course_id,status,assignment_source,assignment_rule_id,assigned_at,started_at,completed_at,due_date,version,created_at,updated_at)
    SELECT m.id,c.id,x.status,IF(x.automatic,'AUTOMATIC','MANUAL'),IF(x.automatic,rule_id,NULL),
        IF(c.id=archive_id,seed_now-INTERVAL 20 DAY,seed_now-INTERVAL 3 DAY),
        IF(x.status IN ('IN_PROGRESS','COMPLETED','FAILED'),seed_now-INTERVAL 2 DAY,NULL),
        IF(x.status='COMPLETED',seed_now-INTERVAL 1 DAY,NULL),c.end_date,0,
        IF(c.id=archive_id,seed_now-INTERVAL 20 DAY,seed_now-INTERVAL 3 DAY),seed_now
    FROM (
        SELECT 'employee1' account,security_id course_id,'ASSIGNED' status,TRUE automatic
        UNION ALL SELECT 'employee4',security_id,'ASSIGNED',TRUE
        UNION ALL SELECT 'instructor1',security_id,'ASSIGNED',TRUE
        UNION ALL SELECT 'employee1',privacy_id,'COMPLETED',FALSE
        UNION ALL SELECT 'employee2',security_id,'FAILED',FALSE
        UNION ALL SELECT 'employee1',respect_id,'IN_PROGRESS',FALSE
        UNION ALL SELECT 'employee1',onboarding_id,'ASSIGNED',FALSE
        UNION ALL SELECT 'employee3',onboarding_id,'IN_PROGRESS',FALSE
        UNION ALL SELECT 'leave1',respect_id,'ASSIGNED',FALSE
        UNION ALL SELECT 'employee1',archive_id,'EXPIRED',FALSE
    ) x JOIN members m ON m.email=CONCAT('demo.',x.account,'@learnflow.local') JOIN courses c ON c.id=x.course_id;
    INSERT INTO content_progresses(enrollment_id,course_content_id,progress_rate,completed_at,version,created_at,updated_at)
    SELECT e.id,c.id,IF(e.status='COMPLETED',100,40),IF(e.status='COMPLETED',e.completed_at-INTERVAL 1 HOUR,NULL),0,e.started_at,seed_now
    FROM enrollments e JOIN course_contents c ON c.course_id=e.course_id AND c.is_required=TRUE
    WHERE e.course_id IN (privacy_id,respect_id,onboarding_id) AND e.status IN ('COMPLETED','IN_PROGRESS');

    -- 수료 근거: 필수 콘텐츠 100% + 1회 100점 합격. 실패 근거: 2회 모두 0점으로 소진.
    INSERT INTO exam_attempts(exam_id,enrollment_id,attempt_number,score,passed,started_at,submitted_at)
    SELECT ex.id,e.id,n.num,IF(e.status='COMPLETED',100,0),e.status='COMPLETED',
        e.started_at + INTERVAL n.num HOUR,
        IF(e.status='COMPLETED',e.completed_at,e.started_at + INTERVAL (n.num+1) HOUR)
    FROM enrollments e JOIN exams ex ON ex.course_id=e.course_id CROSS JOIN (SELECT 1 num UNION ALL SELECT 2) n
    WHERE e.course_id IN (security_id,privacy_id) AND (e.status='FAILED' OR (e.status='COMPLETED' AND n.num=1));
    INSERT INTO exam_answers(exam_attempt_id,question_id,is_correct,earned_score)
    SELECT a.id,q.id,a.passed,IF(a.passed,q.score,0)
    FROM exam_attempts a JOIN questions q ON q.exam_id=a.exam_id JOIN enrollments e ON e.id=a.enrollment_id
    WHERE e.course_id IN (security_id,privacy_id);
    INSERT INTO exam_answer_choices(exam_answer_id,question_choice_id)
    SELECT ans.id,c.id FROM exam_answers ans JOIN exam_attempts a ON a.id=ans.exam_attempt_id
    JOIN question_choices c ON c.question_id=ans.question_id AND c.is_correct=a.passed
    JOIN enrollments e ON e.id=a.enrollment_id WHERE e.course_id IN (security_id,privacy_id);

    -- 7. Notification.create와 같은 유형/문구. UNIQUE(enrollment_id,type) 및 전체 Seed 멱등 유지.
    INSERT INTO notifications(member_id,enrollment_id,type,title,message,created_at,read_at)
    SELECT e.member_id,e.id,'ENROLLMENT_ASSIGNED','새로운 교육이 배정되었습니다.',CONCAT(c.title,' 교육이 배정되었습니다.'),e.assigned_at,NULL
    FROM enrollments e JOIN courses c ON c.id=e.course_id WHERE c.id IN (security_id,privacy_id,respect_id,onboarding_id,archive_id);
    INSERT INTO notifications(member_id,enrollment_id,type,title,message,created_at,read_at)
    SELECT e.member_id,e.id,
        CASE e.status WHEN 'COMPLETED' THEN 'COURSE_COMPLETED' WHEN 'FAILED' THEN 'COURSE_FAILED' ELSE 'ENROLLMENT_EXPIRED' END,
        CASE e.status WHEN 'COMPLETED' THEN '교육과정을 수료했습니다.' WHEN 'FAILED' THEN '교육과정 이수가 완료되지 않았습니다.' ELSE '교육 수강 기간이 만료되었습니다.' END,
        CONCAT(c.title, CASE e.status WHEN 'COMPLETED' THEN ' 교육과정을 수료했습니다.' WHEN 'FAILED' THEN ' 교육의 시험 응시 기회를 모두 사용했습니다.' ELSE ' 교육의 수강 기간이 만료되었습니다.' END),
        COALESCE(e.completed_at,seed_now),NULL
    FROM enrollments e JOIN courses c ON c.id=e.course_id
    WHERE c.id IN (security_id,privacy_id,respect_id,onboarding_id,archive_id) AND e.status IN ('COMPLETED','FAILED','EXPIRED');
    COMMIT;
    DO RELEASE_LOCK('learnflow.local-demo-seed-v1');
    SELECT 'Created demo accounts, courses, learning history and notifications.' AS result;
END$$
DELIMITER ;

CALL learnflow_local_demo_seed_v1();
DROP PROCEDURE learnflow_local_demo_seed_v1;

-- 검증: 9명, 과정 6개(OPEN 4/CLOSED 1/DRAFT 1), 수강 10건(5/2/1/1/1), 알림 13건.
SELECT email,name,status FROM members WHERE LEFT(employee_number,5)='DEMO-' ORDER BY id;
SELECT title,status,start_date,end_date FROM courses WHERE LEFT(title,5)='[시연] ' ORDER BY id;
SELECT e.status,COUNT(*) AS count FROM enrollments e JOIN courses c ON c.id=e.course_id
WHERE LEFT(c.title,5)='[시연] ' GROUP BY e.status;

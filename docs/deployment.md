# LearnFlow Backend 1단계 배포

이 문서는 서버에서 수행할 절차이며 실제 서버 실행 결과가 아니다. 저장소의 Secret 없는
템플릿만 사용한다. 기준 실행 예는 Ubuntu 24.04 LTS / systemd / Java 21 / MySQL 8 / Redis이며,
서버 OS가 다르면 패키지·서비스·설정 경로를 먼저 확인한다. 서버 주소나 비밀번호는 제공하지 않는다.
Backend 단일 인스턴스이며 nginx/Web/HTTPS는 다음 단계다.

## 1. 배포 자료와 서버 준비

현재 `be/pom.xml`로 `clean package`한 실제 산출물은 `be/target/be-0.0.1-SNAPSHOT.jar`다.
파일 이름의 SNAPSHOT은 Maven artifact 버전이며 제품 v3.0.0 이름으로 임의 변경하지 않았다.
서버의 작업 디렉터리에 이 JAR과 저장소의 다음 파일만 안전하게 전송한다:

- `docs/LearnFlow.sql` (유일한 스키마 기준본)
- `deploy/learnflow.env.example`
- `deploy/systemd/learnflow-backend.service.example`

아래 설치·배치 명령은 이 상대 경로와 JAR이 있는 업로드 작업 디렉터리에서 실행한다.
로컬 설정, `.env.local`, `local-seed.sql`은 배포하지 않는다.

```bash
sudo apt-get update
sudo apt-get install openjdk-21-jre-headless mysql-server redis-server curl python3
java -version
command -v java
sudo systemctl enable --now mysql redis-server
id learnflow || sudo useradd --system --user-group --home-dir /opt/learnflow/backend --shell /usr/sbin/nologin learnflow
sudo install -d -o root -g learnflow -m 0750 /opt/learnflow/backend
sudo install -d -o root -g root -m 0700 /etc/learnflow
```

템플릿은 `/usr/bin/java`를 사용한다. Java 21 경로인지 확인한다. 앱 사용자에게 JAR 수정 권한은
주지 않는다. 로그는 journald로 보관하므로 별도 `/var/log/learnflow`는 만들지 않는다.

## 2. MySQL — 신규 빈 DB에만 적용

`/etc/mysql/mysql.conf.d/mysqld.cnf`에서 `bind-address=127.0.0.1`을 확인한다.
MySQL X Protocol을 사용할 경우 해당 listener도 loopback으로 제한하거나 비활성화한다.
변경했다면 `sudo systemctl restart mysql` 후 정상 상태를 확인한다.

기준 SQL은 `learnflow` DB를 명시하고 기존 테이블을 DROP한다. DB 이름을 CLI로 바꾸더라도
SQL 안의 `USE learnflow`가 우선한다. 기존 운영 DB에는 이 파일 전체를 실행하지 않는다.
다음 보호 명령은 DB가 이미 존재하면, 비어 있어도 중단한다. 가장 단순하고 엄격한 최초 배포 절차다.
기존 빈 DB를 사용해야 한다면 DBA가 대상과 모든 객체·데이터 부재를 별도로 확인한 뒤 진행한다.

```bash
(
  set -eu
  existing=$(sudo mysql --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='learnflow'")
  if [ "$existing" != "0" ]; then
    echo 'STOP: learnflow already exists; do not run the destructive initialization script.' >&2
    exit 1
  fi
  # 다른 배포 작업이 동시에 DB를 생성/사용하지 않는 유지보수 구간에서만 실행.
  sudo mysql --default-character-set=utf8mb4 < docs/LearnFlow.sql
)
```

관리자 세션에서 아래를 확인한다. 정상 신규 기준본은 22개 테이블과 binding_generation 컬럼을 포함한다.

```bash
sudo mysql -e "SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='learnflow'; SELECT COUNT(*) AS table_count FROM information_schema.TABLES WHERE TABLE_SCHEMA='learnflow'; SHOW COLUMNS FROM learnflow.mobile_push_subscriptions LIKE 'binding_generation';"
sudo env MYSQL_HISTFILE=/dev/null mysql
```

위 대화형 관리자 세션에서 전용 `learnflow_app` 계정을 생성한다. 다음 비밀번호 표시는
실행 가능한 값이 아니다. 운영자가 선택한 실제 값을 서버에서만 입력한다. SQL 문자열 escaping을
적용하고 DB general/audit 로그의 비밀번호 처리 정책도 확인한다. Secret을 CLI 인자로 넘기지 않는다.

```sql
CREATE USER 'learnflow_app'@'127.0.0.1' IDENTIFIED BY '<서버에서 운영자가 입력; 예시 그대로 실행 금지>';
GRANT SELECT, INSERT, UPDATE, DELETE ON learnflow.* TO 'learnflow_app'@'127.0.0.1';
SHOW GRANTS FOR 'learnflow_app'@'127.0.0.1';
```

CREATE/DROP/ALTER/GRANT 권한은 앱 계정에 부여하지 않는다. 관리자 계정으로 스키마를 적용하고
앱은 validate만 수행한다. TCP 로그인과 CURRENT_USER를 확인해 실제 계정 매칭도 확인한다.

```bash
mysql --protocol=TCP -h 127.0.0.1 -u learnflow_app -p learnflow \
  -e "SELECT CURRENT_USER(), DATABASE(); SHOW TABLES;"
```

`local-seed.sql`은 실행하지 않는다. 최초 ADMIN은 아래 startup Bootstrap 절차로 생성한다.

## 3. Redis와 네트워크

`/etc/redis/redis.conf`의 bind는 `127.0.0.1`(필요 시 `::1`), `protected-mode yes`로 유지한다.
인터넷에 6379를 열지 않는다. 인증이 필요하면 서버에서 비밀번호/ACL을 구성하고 동일한 값을
EnvironmentFile의 REDIS_USERNAME/REDIS_PASSWORD에 넣는다. 명령행 `-a`로 비밀번호를 노출하지 않는다.

```bash
sudo systemctl restart redis-server
redis-cli -h 127.0.0.1 -p 6379 PING
# 인증 설정 시 위 명령 대신 사용. ACL user를 쓴다면 --user <실제 사용자>도 추가.
redis-cli -h 127.0.0.1 -p 6379 --askpass PING
```

결과는 PONG이어야 한다. 제한 ACL을 사용한다면 Health의 PING/INFO와 Refresh의
GET/SET/DEL 및 Lua EVAL/EVALSHA(클라이언트 script 실행)를 허용해야 한다. Redis 장애는
인증/Refresh 장애이므로 선택 의존성으로 취급하지 않는다. 재시작 시 세션 유지가 필요하면
Redis persistence 정책도 운영자가 정한다. 데이터 유실 시 재로그인이 필요하다.

클라우드 보안그룹·호스트 방화벽은 실제 SSH 포트만 관리자 접근 범위로 허용한다.
8080/3306/33060/6379는 외부 차단하고 80/443은 다음 nginx 단계에서 개방한다.
SSH 연결을 잃을 수 있는 방화벽 변경은 콘솔 복구 경로를 확보한 뒤 적용한다.

## 4. 외부 EnvironmentFile과 JAR 배치

```bash
# 최초에만 복사. 기존 운영 env 파일을 덮어쓰지 않는다.
sudo test ! -e /etc/learnflow/learnflow.env && \
  sudo install -o root -g root -m 0600 deploy/learnflow.env.example /etc/learnflow/learnflow.env
sudoedit /etc/learnflow/learnflow.env
sudo chown root:root /etc/learnflow/learnflow.env
sudo chmod 0600 /etc/learnflow/learnflow.env
sudo stat -c '%U %G %a %n' /etc/learnflow/learnflow.env
sha256sum be-0.0.1-SNAPSHOT.jar
sudo install -o root -g learnflow -m 0640 be-0.0.1-SNAPSHOT.jar /opt/learnflow/backend/be-0.0.1-SNAPSHOT.jar
sudo sha256sum /opt/learnflow/backend/be-0.0.1-SNAPSHOT.jar
```

필수 운영 입력: APP_DATASOURCE_PASSWORD, JWT_SECRET, JWT_ACCESS_TOKEN_TTL_SECONDS,
JWT_REFRESH_TOKEN_TTL_SECONDS. Secret은 생성/제공하지 않는다. JWT key는 Base64로 표현한
최소 32바이트, TTL은 양수다. URL/user는 템플릿의 제안값과 실제 서버 구성이 일치해야 한다.

템플릿 전체 변수는 현재 코드에서 확인한 다음 항목이다:

- SPRING_PROFILES_ACTIVE=prod, SERVER_ADDRESS=127.0.0.1, SERVER_PORT=8080, TZ=UTC
- APP_DATASOURCE_URL / USERNAME / PASSWORD, DB_POOL_MAX_SIZE / DB_POOL_MIN_IDLE
- REDIS_HOST / PORT / USERNAME / PASSWORD / SSL_ENABLED
- JWT_SECRET / JWT_ACCESS_TOKEN_TTL_SECONDS / JWT_REFRESH_TOKEN_TTL_SECONDS
- PUSH_ENABLED / VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY / VAPID_SUBJECT
- MOBILE_PUSH_ENABLED / EXPO_PUSH_ACCESS_TOKEN

APP_DATASOURCE_USERNAME 등은 축약 표기이며 실제 key는 env 템플릿을 따른다.
EnvironmentFile은 shell 스크립트가 아니다. `export`, 명령 치환, `${...}` 참조를 넣거나
`source`로 실행하지 않는다. root의 systemd manager가 0600 파일을 읽어 서비스에 전달한다.
`systemctl show ... Environment`나 env 파일 내용을 로그/공유 보고서에 출력하지 않는다.

첫 부팅은 PUSH_ENABLED=false, MOBILE_PUSH_ENABLED=false로 한다. 이 경우 VAPID/Expo 값이
없어도 기동할 수 있다. PUSH_ENABLED=true인데 VAPID가 없거나 잘못되면 현재 코드는 기동을
거부하므로 true로 바꾸기 전에 유효한 키 쌍/subject를 준비해야 한다. Expo enhanced security를
사용하는 프로젝트라면 EXPO_PUSH_ACCESS_TOKEN도 서버 전용으로 제공한다.

## 5. systemd 설치·시작

```bash
sudo install -o root -g root -m 0644 deploy/systemd/learnflow-backend.service.example /etc/systemd/system/learnflow-backend.service
sudo systemd-analyze verify /etc/systemd/system/learnflow-backend.service
sudo systemctl daemon-reload
sudo systemctl enable --now learnflow-backend
sudo systemctl status learnflow-backend --no-pager
sudo journalctl -u learnflow-backend -b -n 100 --no-pager
```

단일 인스턴스 전제다. 만료(서울 00:00), 재교육(서울 00:10), Expo receipt(60초) Scheduler는
자동 구성된다. 정상 기동했다고 업무 데이터가 없는 상태에서 모든 스케줄 작업 실행까지
검증됐다고 보지는 않는다. journald 접근은 운영자로 제한하고 Authorization/본문 debug 로그를 켜지 않는다.

### 최초 ADMIN Bootstrap

기본값은 비활성화다. 최초 기동 전에 Repository 밖의 root 전용 0600
`/etc/learnflow/learnflow.env`에 운영자가 다음 값을 입력한다. 실제 값을 명령행·Git·로그에 남기지 않는다.

- `LEARNFLOW_BOOTSTRAP_ADMIN_ENABLED=true`
- `LEARNFLOW_BOOTSTRAP_ADMIN_EMAIL`: 운영 관리자 이메일
- `LEARNFLOW_BOOTSTRAP_ADMIN_PASSWORD`: 기존 회원 정책과 동일한 8~128자 비밀번호
- `LEARNFLOW_BOOTSTRAP_ADMIN_NAME`: 관리자 이름(최대 100자)

서비스를 시작하고 일반 로그 `Initial admin created`를 확인한다. 기존 ADMIN이 한 명이라도
있으면 상태와 관계없이 `Admin already exists; bootstrap skipped`로 종료하며 계정·비밀번호를
변경하지 않는다. 이 경우 입력값을 제거해도 재시작할 수 있다.
ADMIN이 없는데 이메일이 기존 회원과 겹치거나 필수 입력이 잘못되면 기동을 중단한다.
기존 회원을 자동 승격하지 않는다.

Member 필수 조건을 위해 예약 코드/사번 `LEARNFLOW_BOOTSTRAP_ADMIN`으로 활성 부서
`시스템 관리`, 직무 `시스템 관리자`, ACTIVE ADMIN 계정을 하나의 Transaction에서 생성한다.
입사일 필드는 생성 시점의 Asia/Seoul 날짜다. EMPLOYEE/INSTRUCTOR 역할 및 교육 자동 배정은
추가하지 않는다. 예약 코드가 기존 데이터와 충돌하면 재사용하지 않고 실패한다.
동시 기동은 예약 코드의 기존 DB UNIQUE 제약으로 직렬화하며 실패한 Transaction은 전체 rollback한다.

안전한 SSH 터널 또는 HTTPS 경로에서 기존 로그인 API로 최초 로그인을 확인한다.
비밀번호·응답 Token을 콘솔/공유 로그에 출력하지 않는다. 확인 즉시 env 파일에서
`LEARNFLOW_BOOTSTRAP_ADMIN_ENABLED=false`로 바꾸고 `LEARNFLOW_BOOTSTRAP_ADMIN_PASSWORD`를
삭제한다(email/name도 제거 가능). `sudo systemctl restart learnflow-backend` 후 Health와
로그인을 다시 확인한다. Bootstrap은 비밀번호 재설정 수단이 아니며 공개 생성 API는 없다.

## 6. 실제 배포 검증과 재시작

```bash
sudo systemctl is-active learnflow-backend mysql redis-server
sudo ss -ltnp
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d=={"status":"UP"}, "Health is not aggregate UP"; print("Health UP")'
for path in /actuator/env /actuator/info /actuator/health/db /api/admin/dashboard; do
  code=$(curl --silent --output /dev/null --write-out '%{http_code}' "http://127.0.0.1:8080$path")
  printf '%s %s\n' "$path" "$code"
  test "$code" = 401 || test "$code" = 403 || exit 1
done
sudo systemctl restart learnflow-backend
sudo systemctl status learnflow-backend --no-pager
```

시작 직후에는 준비 시간이 필요하므로 journalctl에서 기동 완료를 확인한 뒤 같은 Health 검증을 다시 실행한다.
확인할 증거:

- prod profile 활성화, Hibernate EntityManagerFactory 초기화 완료, schema validation 오류 없음.
- prod의 ddl-auto=validate / SQL init=never 유지. update/create로 변경하지 않음.
- MySQL 앱 계정 TCP 접속 성공, Redis PONG, DB/Redis indicator를 비활성화하지 않은 상태의 Health UP.
- Backend는 127.0.0.1:8080만 사용, MySQL/Redis도 loopback/private listener만 사용.
- Health 외 Actuator·관리 API는 익명 접근 거절. 재시작 후에도 같은 결과.
- Secret 값이 로그에 없는지 서버 내부에서 확인. 로그 원문을 외부에 붙여 넣지 않음.

validate 실패 시 앱을 중지하고 오류 대상 table/column/type을 Entity 및 유일한 SQL 기준본과
대조한다. 기존 데이터는 백업하고 차이만 DBA가 확인한다. 초기화 SQL 재실행이나
별도 migration SQL 파일 추가로 우회하지 않는다.

실패가 반복돼 start limit에 도달했다면 원인을 해결한 뒤
`sudo systemctl reset-failed learnflow-backend` → `sudo systemctl start learnflow-backend`순서로 실행한다.

## 다음 단계 진행 조건

실제 서버에서 위 항목을 확인하기 전에는 배포 완료로 처리하지 않는다. Health UP 확인 후
같은 origin의 nginx `/api` proxy, Web 정적 배포, TLS 단계로 진행한다. 공개 로그인은 HTTP로 제공하지 않는다.

참고: [systemd service](https://www.freedesktop.org/software/systemd/man/latest/systemd.service.html),
[systemd EnvironmentFile](https://www.freedesktop.org/software/systemd/man/latest/systemd.exec.html),
[MySQL privileges](https://dev.mysql.com/doc/refman/8.4/en/grant.html),
[Redis security](https://redis.io/docs/latest/operate/oss_and_stack/management/security/)

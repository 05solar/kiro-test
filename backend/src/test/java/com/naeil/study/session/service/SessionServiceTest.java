package com.naeil.study.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.naeil.study.session.dto.UpdateExamRequest;
import com.naeil.study.session.entity.SessionStatus;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.exception.InvalidExamTimeException;
import com.naeil.study.session.exception.InvalidSessionCodeException;
import com.naeil.study.session.exception.SessionCodeGenerationException;
import com.naeil.study.session.exception.SessionNotFoundException;
import com.naeil.study.session.repository.StudySessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionService - 세션 생성/조회")
class SessionServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final long EXPIRATION_DAYS = 30L;
    private static final int MAX_ATTEMPTS = 10;

    /** 2026-08-27T15:30:00 (Asia/Seoul) 고정 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 15, 30, 0);

    @Mock
    private StudySessionRepository studySessionRepository;

    @Mock
    private SessionCodeGenerator sessionCodeGenerator;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        sessionService = new SessionService(
                studySessionRepository, sessionCodeGenerator, fixedClock, EXPIRATION_DAYS, MAX_ATTEMPTS);
    }

    @Nested
    @DisplayName("세션 생성")
    class CreateSession {

        @Test
        @DisplayName("신규 세션은 CREATED 상태로 저장되고 만료일은 30일 뒤다")
        void createsNewSession() {
            given(sessionCodeGenerator.generate()).willReturn("7K2M9QXF");
            given(studySessionRepository.existsBySessionCode("7K2M9QXF")).willReturn(false);
            given(studySessionRepository.save(any(StudySession.class))).willAnswer(i -> i.getArgument(0));

            StudySession created = sessionService.createSession();

            assertThat(created.getSessionCode()).isEqualTo("7K2M9QXF");
            assertThat(created.getStatus()).isEqualTo(SessionStatus.CREATED);
            assertThat(created.getSubject()).isNull();
            assertThat(created.getExamAt()).isNull();
            assertThat(created.getAvailableStudyMinutes()).isNull();
            assertThat(created.getRemainingStudyMinutes()).isNull();
            assertThat(created.getCurrentStepOrder()).isNull();
            assertThat(created.getCreatedAt()).isEqualTo(NOW);
            assertThat(created.getUpdatedAt()).isEqualTo(NOW);
            assertThat(created.getLastAccessedAt()).isEqualTo(NOW);
            assertThat(created.getExpiresAt()).isEqualTo(NOW.plusDays(EXPIRATION_DAYS));
            verify(studySessionRepository).save(any(StudySession.class));
        }

        @Test
        @DisplayName("코드가 중복되면 중복되지 않는 코드가 나올 때까지 재생성한다")
        void regeneratesCodeWhenDuplicated() {
            given(sessionCodeGenerator.generate()).willReturn("AAAAAAAA", "BBBBBBBB", "CCCCCCCC");
            given(studySessionRepository.existsBySessionCode("AAAAAAAA")).willReturn(true);
            given(studySessionRepository.existsBySessionCode("BBBBBBBB")).willReturn(true);
            given(studySessionRepository.existsBySessionCode("CCCCCCCC")).willReturn(false);
            given(studySessionRepository.save(any(StudySession.class))).willAnswer(i -> i.getArgument(0));

            StudySession created = sessionService.createSession();

            assertThat(created.getSessionCode()).isEqualTo("CCCCCCCC");
            verify(sessionCodeGenerator, times(3)).generate();
        }

        @Test
        @DisplayName("최대 재시도 횟수까지 모두 중복되면 예외가 발생하고 저장하지 않는다")
        void throwsWhenUniqueCodeCannotBeGenerated() {
            given(sessionCodeGenerator.generate()).willReturn("AAAAAAAA");
            given(studySessionRepository.existsBySessionCode(anyString())).willReturn(true);

            assertThatThrownBy(() -> sessionService.createSession())
                    .isInstanceOf(SessionCodeGenerationException.class);

            verify(sessionCodeGenerator, times(MAX_ATTEMPTS)).generate();
            verify(studySessionRepository, never()).save(any(StudySession.class));
        }
    }

    @Nested
    @DisplayName("세션 조회")
    class GetSession {

        @Test
        @DisplayName("존재하는 세션을 조회하면 세션을 반환한다")
        void returnsExistingSession() {
            StudySession session = StudySession.create("7K2M9QXF", NOW.minusDays(3), EXPIRATION_DAYS);
            given(studySessionRepository.findBySessionCode("7K2M9QXF")).willReturn(Optional.of(session));

            StudySession found = sessionService.getSessionAndTouch("7K2M9QXF");

            assertThat(found.getSessionCode()).isEqualTo("7K2M9QXF");
            assertThat(found.getStatus()).isEqualTo(SessionStatus.CREATED);
        }

        @Test
        @DisplayName("조회에 성공하면 lastAccessedAt을 현재 시각으로 갱신한다")
        void updatesLastAccessedAt() {
            StudySession session = StudySession.create("7K2M9QXF", NOW.minusDays(3), EXPIRATION_DAYS);
            given(studySessionRepository.findBySessionCode("7K2M9QXF")).willReturn(Optional.of(session));

            StudySession found = sessionService.getSessionAndTouch("7K2M9QXF");

            assertThat(found.getLastAccessedAt()).isEqualTo(NOW);
            assertThat(found.getUpdatedAt()).isEqualTo(NOW);
            assertThat(found.getCreatedAt()).isEqualTo(NOW.minusDays(3));
        }

        @Test
        @DisplayName("조회에 성공하면 expiresAt을 현재 시각 기준 30일 뒤로 연장한다")
        void extendsExpiresAtByThirtyDays() {
            StudySession session = StudySession.create("7K2M9QXF", NOW.minusDays(3), EXPIRATION_DAYS);
            LocalDateTime expiresAtBefore = session.getExpiresAt();
            given(studySessionRepository.findBySessionCode("7K2M9QXF")).willReturn(Optional.of(session));

            StudySession found = sessionService.getSessionAndTouch("7K2M9QXF");

            assertThat(expiresAtBefore).isEqualTo(NOW.minusDays(3).plusDays(EXPIRATION_DAYS));
            assertThat(found.getExpiresAt()).isEqualTo(NOW.plusDays(EXPIRATION_DAYS));
            assertThat(found.getExpiresAt()).isAfter(expiresAtBefore);
        }

        @Test
        @DisplayName("존재하지 않는 세션을 조회하면 SessionNotFoundException이 발생한다")
        void throwsWhenSessionNotFound() {
            given(studySessionRepository.findBySessionCode("7K2M9QXF")).willReturn(Optional.empty());

            assertThatThrownBy(() -> sessionService.getSessionAndTouch("7K2M9QXF"))
                    .isInstanceOf(SessionNotFoundException.class);
        }

        @Test
        @DisplayName("형식이 잘못된 코드는 DB를 조회하지 않고 InvalidSessionCodeException이 발생한다")
        void throwsWithoutQueryingDatabaseWhenCodeFormatIsInvalid() {
            assertThatThrownBy(() -> sessionService.getSessionAndTouch("ABC"))
                    .isInstanceOf(InvalidSessionCodeException.class);

            verifyNoInteractions(studySessionRepository);
        }

        @Test
        @DisplayName("혼동 문자가 포함된 코드도 DB 조회 없이 거부한다")
        void rejectsCodeWithConfusingCharacters() {
            assertThatThrownBy(() -> sessionService.getSessionAndTouch("0K2M9QXF"))
                    .isInstanceOf(InvalidSessionCodeException.class);

            verifyNoInteractions(studySessionRepository);
        }
    }

    @Nested
    @DisplayName("시험 정보 등록/수정")
    class UpdateExamInfo {

        private StudySession givenSession() {
            StudySession session = StudySession.create("7K2M9QXF", NOW.minusHours(1), EXPIRATION_DAYS);
            given(studySessionRepository.findBySessionCode("7K2M9QXF")).willReturn(Optional.of(session));
            return session;
        }

        @Test
        @DisplayName("시험까지 남은 시간이 충분하면 입력한 학습시간을 그대로 저장한다")
        void storesRequestedMinutesWhenEnoughTimeRemains() {
            // 현재 2026-08-27 15:30, 시험 2026-08-28 10:00 → 남은 시간 1110분
            givenSession();
            UpdateExamRequest request = new UpdateExamRequest(
                    "운영체제", null, LocalDateTime.of(2026, 8, 28, 10, 0), 360);

            StudySession updated = sessionService.updateExamInfo("7K2M9QXF", request);

            assertThat(updated.getSubject()).isEqualTo("운영체제");
            assertThat(updated.getExamAt()).isEqualTo(LocalDateTime.of(2026, 8, 28, 10, 0));
            assertThat(updated.getAvailableStudyMinutes()).isEqualTo(360);
            assertThat(updated.getRemainingStudyMinutes()).isEqualTo(360);
        }

        @Test
        @DisplayName("시험까지 남은 시간이 더 짧으면 남은 시간으로 잘라서 저장한다")
        void clampsToMinutesUntilExam() {
            // 현재 15:30, 시험 19:30 → 남은 시간 240분, 입력 360분
            givenSession();
            UpdateExamRequest request = new UpdateExamRequest(
                    "운영체제", null, NOW.plusMinutes(240), 360);

            StudySession updated = sessionService.updateExamInfo("7K2M9QXF", request);

            assertThat(updated.getAvailableStudyMinutes()).isEqualTo(360);
            assertThat(updated.getRemainingStudyMinutes()).isEqualTo(240);
        }

        @Test
        @DisplayName("availableStudyMinutes는 사용자가 입력한 원본값을 그대로 유지한다")
        void keepsRequestedMinutesAsOriginalValue() {
            givenSession();
            UpdateExamRequest request = new UpdateExamRequest("운영체제", null, NOW.plusMinutes(10), 360);

            StudySession updated = sessionService.updateExamInfo("7K2M9QXF", request);

            assertThat(updated.getAvailableStudyMinutes()).isEqualTo(360);
            assertThat(updated.getRemainingStudyMinutes()).isEqualTo(10);
        }

        @Test
        @DisplayName("초 단위는 버리고 분 단위로 계산한다")
        void truncatesSecondsWhenCalculatingMinutes() {
            StudySession session = StudySession.create("7K2M9QXF", NOW.minusHours(1), EXPIRATION_DAYS);
            given(studySessionRepository.findBySessionCode("7K2M9QXF")).willReturn(Optional.of(session));
            // 현재 15:30:00, 시험 19:29:59 → 239분 59초 → 239분
            UpdateExamRequest request = new UpdateExamRequest(
                    "운영체제", null, NOW.plusMinutes(240).minusSeconds(1), 360);

            StudySession updated = sessionService.updateExamInfo("7K2M9QXF", request);

            assertThat(updated.getRemainingStudyMinutes()).isEqualTo(239);
        }

        @Test
        @DisplayName("시험 정보를 다시 등록하면 두 시간 값이 모두 다시 계산된다")
        void recalculatesOnUpdate() {
            StudySession session = givenSession();
            sessionService.updateExamInfo("7K2M9QXF",
                    new UpdateExamRequest("운영체제", null, NOW.plusMinutes(240), 360));
            assertThat(session.getRemainingStudyMinutes()).isEqualTo(240);

            StudySession updated = sessionService.updateExamInfo("7K2M9QXF",
                    new UpdateExamRequest("데이터베이스", null, NOW.plusMinutes(600), 420));

            assertThat(updated.getSubject()).isEqualTo("데이터베이스");
            assertThat(updated.getExamAt()).isEqualTo(NOW.plusMinutes(600));
            assertThat(updated.getAvailableStudyMinutes()).isEqualTo(420);
            assertThat(updated.getRemainingStudyMinutes()).isEqualTo(420);
        }

        @Test
        @DisplayName("시험 정보를 등록해도 상태는 CREATED로 유지된다")
        void doesNotChangeStatus() {
            givenSession();

            StudySession updated = sessionService.updateExamInfo("7K2M9QXF",
                    new UpdateExamRequest("운영체제", null, NOW.plusMinutes(240), 360));

            assertThat(updated.getStatus()).isEqualTo(SessionStatus.CREATED);
        }

        @Test
        @DisplayName("등록에 성공하면 lastAccessedAt과 expiresAt이 갱신된다")
        void refreshesAccessTime() {
            StudySession session = StudySession.create("7K2M9QXF", NOW.minusDays(3), EXPIRATION_DAYS);
            given(studySessionRepository.findBySessionCode("7K2M9QXF")).willReturn(Optional.of(session));

            StudySession updated = sessionService.updateExamInfo("7K2M9QXF",
                    new UpdateExamRequest("운영체제", null, NOW.plusMinutes(240), 360));

            assertThat(updated.getLastAccessedAt()).isEqualTo(NOW);
            assertThat(updated.getExpiresAt()).isEqualTo(NOW.plusDays(EXPIRATION_DAYS));
            assertThat(updated.getUpdatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("과목명 앞뒤 공백은 제거하고 저장한다")
        void stripsSubject() {
            givenSession();

            StudySession updated = sessionService.updateExamInfo("7K2M9QXF",
                    new UpdateExamRequest("  운영체제  ", null, NOW.plusMinutes(240), 360));

            assertThat(updated.getSubject()).isEqualTo("운영체제");
        }

        @Test
        @DisplayName("시험 시각이 과거면 InvalidExamTimeException이 발생한다")
        void throwsWhenExamTimeIsInThePast() {
            givenSession();
            UpdateExamRequest request = new UpdateExamRequest("운영체제", null, NOW.minusMinutes(1), 360);

            assertThatThrownBy(() -> sessionService.updateExamInfo("7K2M9QXF", request))
                    .isInstanceOf(InvalidExamTimeException.class);
        }

        @Test
        @DisplayName("시험 시각이 현재와 같아도 InvalidExamTimeException이 발생한다")
        void throwsWhenExamTimeEqualsNow() {
            givenSession();
            UpdateExamRequest request = new UpdateExamRequest("운영체제", null, NOW, 360);

            assertThatThrownBy(() -> sessionService.updateExamInfo("7K2M9QXF", request))
                    .isInstanceOf(InvalidExamTimeException.class);
        }

        @Test
        @DisplayName("존재하지 않는 세션이면 SessionNotFoundException이 발생한다")
        void throwsWhenSessionNotFound() {
            given(studySessionRepository.findBySessionCode("ZZZZZZZZ")).willReturn(Optional.empty());
            UpdateExamRequest request = new UpdateExamRequest("운영체제", null, NOW.plusMinutes(240), 360);

            assertThatThrownBy(() -> sessionService.updateExamInfo("ZZZZZZZZ", request))
                    .isInstanceOf(SessionNotFoundException.class);
        }

        @Test
        @DisplayName("코드 형식이 잘못되면 DB 조회 없이 InvalidSessionCodeException이 발생한다")
        void throwsWithoutQueryingDatabaseWhenCodeFormatIsInvalid() {
            UpdateExamRequest request = new UpdateExamRequest("운영체제", null, NOW.plusMinutes(240), 360);

            assertThatThrownBy(() -> sessionService.updateExamInfo("ABC", request))
                    .isInstanceOf(InvalidSessionCodeException.class);

            verifyNoInteractions(studySessionRepository);
        }
    }
}

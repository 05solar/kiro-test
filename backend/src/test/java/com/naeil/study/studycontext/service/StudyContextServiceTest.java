package com.naeil.study.studycontext.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naeil.study.session.entity.SessionStatus;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.exception.InvalidSessionCodeException;
import com.naeil.study.session.exception.SessionNotFoundException;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.studycontext.dto.UpdateStudyContextRequest;
import com.naeil.study.studycontext.entity.StudyContext;
import com.naeil.study.studycontext.repository.StudyContextRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudyContextService - 학습 맥락 저장/조회")
class StudyContextServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 17, 30, 0);
    private static final UUID SESSION_ID = UUID.fromString("6f79a1b2-0000-4000-8000-000000000001");
    private static final String SESSION_CODE = "7K2M9QXF";

    @Mock
    private StudyContextRepository studyContextRepository;

    @Mock
    private SessionService sessionService;

    private StudyContextService studyContextService;
    private StudySession session;

    @BeforeEach
    void setUp() throws Exception {
        Clock fixedClock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        studyContextService = new StudyContextService(studyContextRepository, sessionService, fixedClock);
        session = StudySession.create(SESSION_CODE, NOW.minusHours(1), 30L);
        Field field = StudySession.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(session, SESSION_ID);
    }

    private void givenSessionFound() {
        given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
    }

    private UpdateStudyContextRequest request(String emphasis, String pastExam, String weak, String must) {
        return new UpdateStudyContextRequest(emphasis, pastExam, weak, must);
    }

    @Nested
    @DisplayName("저장 / 수정")
    class Upsert {

        @Test
        @DisplayName("학습 맥락이 없으면 새로 만든다")
        void createsWhenAbsent() {
            givenSessionFound();
            given(studyContextRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());
            given(studyContextRepository.save(any(StudyContext.class))).willAnswer(i -> i.getArgument(0));

            StudyContext saved = studyContextService.upsert(SESSION_CODE,
                    request("교착상태 4가지 조건 강조", "CPU Scheduling 계산 문제 기출", "가상 메모리", "교착상태"));

            assertThat(saved.getProfessorEmphasis()).isEqualTo("교착상태 4가지 조건 강조");
            assertThat(saved.getPastExamInfo()).isEqualTo("CPU Scheduling 계산 문제 기출");
            assertThat(saved.getWeakAreas()).isEqualTo("가상 메모리");
            assertThat(saved.getMustStudyAreas()).isEqualTo("교착상태");
            assertThat(saved.getCreatedAt()).isEqualTo(NOW);
            assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
            verify(studyContextRepository).save(any(StudyContext.class));
        }

        @Test
        @DisplayName("이미 있으면 새 행을 만들지 않고 기존 행을 고친다")
        void updatesWhenPresent() {
            givenSessionFound();
            StudyContext existing = StudyContext.create(
                    session, null, null, "가상 메모리", null, NOW.minusDays(1));
            given(studyContextRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.of(existing));

            StudyContext updated = studyContextService.upsert(SESSION_CODE,
                    request(null, null, "교착상태", null));

            assertThat(updated).isSameAs(existing);
            assertThat(updated.getWeakAreas()).isEqualTo("교착상태");
            assertThat(updated.getCreatedAt()).isEqualTo(NOW.minusDays(1));
            assertThat(updated.getUpdatedAt()).isEqualTo(NOW);
            verify(studyContextRepository, never()).save(any(StudyContext.class));
        }

        @Test
        @DisplayName("PUT은 전체 교체다. 보내지 않은 항목은 비워진다")
        void replacesAllFields() {
            givenSessionFound();
            StudyContext existing = StudyContext.create(
                    session, "이전 강조", "이전 기출", "이전 취약", "이전 필수", NOW.minusDays(1));
            given(studyContextRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.of(existing));

            StudyContext updated = studyContextService.upsert(SESSION_CODE,
                    request("새 강조", null, null, null));

            assertThat(updated.getProfessorEmphasis()).isEqualTo("새 강조");
            assertThat(updated.getPastExamInfo()).isNull();
            assertThat(updated.getWeakAreas()).isNull();
            assertThat(updated.getMustStudyAreas()).isNull();
        }

        @Test
        @DisplayName("모든 항목이 null이어도 정상 저장한다")
        void acceptsAllNullFields() {
            givenSessionFound();
            given(studyContextRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());
            given(studyContextRepository.save(any(StudyContext.class))).willAnswer(i -> i.getArgument(0));

            StudyContext saved = studyContextService.upsert(SESSION_CODE, request(null, null, null, null));

            assertThat(saved.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("앞뒤 공백을 제거하고 저장한다")
        void normalizesWhitespace() {
            givenSessionFound();
            given(studyContextRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());
            given(studyContextRepository.save(any(StudyContext.class))).willAnswer(i -> i.getArgument(0));

            StudyContext saved = studyContextService.upsert(SESSION_CODE,
                    request("   교착상태 강조   ", null, null, null));

            assertThat(saved.getProfessorEmphasis()).isEqualTo("교착상태 강조");
        }

        @Test
        @DisplayName("공백만 입력하면 null로 저장한다")
        void turnsBlankIntoNull() {
            givenSessionFound();
            given(studyContextRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());
            given(studyContextRepository.save(any(StudyContext.class))).willAnswer(i -> i.getArgument(0));

            StudyContext saved = studyContextService.upsert(SESSION_CODE,
                    request(null, null, "     ", null));

            assertThat(saved.getWeakAreas()).isNull();
            assertThat(saved.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("학습 맥락을 저장해도 세션 상태는 바뀌지 않는다")
        void doesNotChangeSessionStatus() {
            givenSessionFound();
            given(studyContextRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());
            given(studyContextRepository.save(any(StudyContext.class))).willAnswer(i -> i.getArgument(0));

            studyContextService.upsert(SESSION_CODE, request("강조", null, null, null));

            assertThat(session.getStatus()).isEqualTo(SessionStatus.CREATED);
        }

        @Test
        @DisplayName("저장은 세션 접근으로 기록된다")
        void refreshesSessionAccessTime() {
            givenSessionFound();
            given(studyContextRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());
            given(studyContextRepository.save(any(StudyContext.class))).willAnswer(i -> i.getArgument(0));

            studyContextService.upsert(SESSION_CODE, request("강조", null, null, null));

            verify(sessionService).getSessionAndTouch(SESSION_CODE);
        }

        @Test
        @DisplayName("존재하지 않는 세션이면 SessionNotFoundException이 발생한다")
        void throwsWhenSessionNotFound() {
            given(sessionService.getSessionAndTouch("ZZZZZZZZ")).willThrow(new SessionNotFoundException());

            assertThatThrownBy(() -> studyContextService.upsert("ZZZZZZZZ", request("강조", null, null, null)))
                    .isInstanceOf(SessionNotFoundException.class);

            verify(studyContextRepository, never()).save(any(StudyContext.class));
        }

        @Test
        @DisplayName("코드 형식이 잘못되면 InvalidSessionCodeException이 발생한다")
        void throwsWhenSessionCodeInvalid() {
            given(sessionService.getSessionAndTouch("ABC")).willThrow(new InvalidSessionCodeException());

            assertThatThrownBy(() -> studyContextService.upsert("ABC", request("강조", null, null, null)))
                    .isInstanceOf(InvalidSessionCodeException.class);
        }
    }

    @Nested
    @DisplayName("조회")
    class Find {

        @Test
        @DisplayName("저장된 학습 맥락을 돌려준다")
        void returnsStoredContext() {
            givenSessionFound();
            StudyContext existing = StudyContext.create(session, "강조", null, "가상 메모리", null, NOW);
            given(studyContextRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.of(existing));

            Optional<StudyContext> found = studyContextService.find(SESSION_CODE);

            assertThat(found).containsSame(existing);
        }

        @Test
        @DisplayName("아직 입력하지 않았으면 비어 있다 (오류가 아니다)")
        void returnsEmptyWhenNotEntered() {
            givenSessionFound();
            given(studyContextRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());

            assertThat(studyContextService.find(SESSION_CODE)).isEmpty();
        }

        @Test
        @DisplayName("조회는 세션 ID를 기준으로 한다 (다른 세션 데이터 차단)")
        void queriesBySessionId() {
            givenSessionFound();
            given(studyContextRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());

            studyContextService.find(SESSION_CODE);

            verify(studyContextRepository).findByStudySessionId(SESSION_ID);
        }

        @Test
        @DisplayName("존재하지 않는 세션이면 SessionNotFoundException이 발생한다")
        void throwsWhenSessionNotFound() {
            given(sessionService.getSessionAndTouch("ZZZZZZZZ")).willThrow(new SessionNotFoundException());

            assertThatThrownBy(() -> studyContextService.find("ZZZZZZZZ"))
                    .isInstanceOf(SessionNotFoundException.class);
        }
    }
}

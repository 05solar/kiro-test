package com.naeil.study.chat.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naeil.study.curriculum.repository.CurriculumRepository;
import com.naeil.study.curriculum.repository.StudyStepRepository;
import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.entity.DocumentStatus;
import com.naeil.study.document.repository.DocumentRepository;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.entity.StudySourceType;
import com.naeil.study.storage.StoredFile;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import com.naeil.study.topic.repository.TopicRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpleStudyContextProvider - 챗봇이 보는 근거를 고른다")
class SimpleStudyContextProviderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 22, 0, 0);
    private static final UUID SESSION_ID = UUID.fromString("5b2d0000-0000-4000-8000-000000000001");
    private static final UUID DOCUMENT_ID = UUID.fromString("5b2d0000-0000-4000-8000-000000000002");

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private CurriculumRepository curriculumRepository;

    @Mock
    private StudyStepRepository studyStepRepository;

    private SimpleStudyContextProvider provider;
    private StudySession session;

    @BeforeEach
    void setUp() throws Exception {
        provider = new SimpleStudyContextProvider(
                topicRepository, documentRepository, curriculumRepository, studyStepRepository, 6_000);

        session = StudySession.create("7K2M9QXF", NOW.minusHours(5), 30L);
        Field field = StudySession.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(session, SESSION_ID);
        session.updateExamInfo("운영체제", "3장 프로세스", NOW.plusHours(6), 180, 180, NOW.minusHours(4));

        // 계획이 없는 상태가 기본이다. 필요한 테스트에서만 다르게 준다.
        lenient().when(curriculumRepository.findByStudySessionId(SESSION_ID)).thenReturn(Optional.empty());
    }

    private Topic topic(String title, List<String> keyPoints) {
        return Topic.create(session, title, "요약", keyPoints, TopicImportance.HIGH, 40,
                false, false, false, false, List.of(DOCUMENT_ID), 1, NOW.minusHours(1));
    }

    private Document parsedDocument(String text) {
        Document document = Document.create(session, "강의.txt",
                new StoredFile("stored.txt", "sessions/x/stored.txt"),
                DocumentFileType.TXT, text.length(), NOW.minusHours(2));
        document.markParsed(text, NOW.minusHours(2));
        return document;
    }

    private void givenGrounded() {
        session.startAnalyzing(StudySourceType.USER_MATERIAL, NOW.minusHours(1));
    }

    private void givenGeneralKnowledge() {
        session.startAnalyzing(StudySourceType.GENERAL_KNOWLEDGE, NOW.minusHours(1));
    }

    @Test
    @DisplayName("질문과 관련된 문단만 뽑는다 — 자료 전문을 매 질문마다 보내지 않는다")
    void selectsOnlyRelevantParagraphs() {
        givenGrounded();
        given(topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(SESSION_ID))
                .willReturn(List.of(topic("교착상태", List.of("환형 대기"))));
        given(documentRepository.findAllByStudySessionIdAndStatusOrderByCreatedAtAsc(
                SESSION_ID, DocumentStatus.PARSED))
                .willReturn(List.of(parsedDocument("""
                        파일 시스템은 저장 장치를 관리한다.

                        교착상태는 네 조건이 동시에 성립할 때 발생한다.

                        네트워크 계층은 라우팅을 담당한다.""")));

        StudyChatContext context = provider.provide(session, "교착상태 조건이 뭐야?");

        assertThat(context.materialExcerpt()).contains("교착상태는 네 조건이");
        // 키워드가 걸린 문단의 이웃까지만 담는다. 세 번째 문단은 이웃이라 들어오고,
        // 첫 문단도 이웃이라 들어온다 — 문서 전체가 들어오는 것과는 다르다.
        assertThat(context.grounded()).isTrue();
    }

    @Test
    @DisplayName("관련 구간을 못 찾으면 빈 문자열이다 — 아무 문단이나 근거로 붙이지 않는다")
    void returnsEmptyExcerptWhenNothingMatches() {
        givenGrounded();
        given(topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(SESSION_ID))
                .willReturn(List.of());
        given(documentRepository.findAllByStudySessionIdAndStatusOrderByCreatedAtAsc(
                SESSION_ID, DocumentStatus.PARSED))
                .willReturn(List.of(parsedDocument("파일 시스템은 저장 장치를 관리한다.")));

        StudyChatContext context = provider.provide(session, "블록체인 합의 알고리즘");

        assertThat(context.materialExcerpt()).isEmpty();
    }

    @Test
    @DisplayName("자료가 없는 세션은 문서를 읽지도 않는다")
    void skipsDocumentsForGeneralKnowledgeSession() {
        givenGeneralKnowledge();
        given(topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(SESSION_ID))
                .willReturn(List.of(topic("프로세스", List.of("PCB"))));

        StudyChatContext context = provider.provide(session, "프로세스가 뭐야?");

        assertThat(context.grounded()).isFalse();
        assertThat(context.materialExcerpt()).isEmpty();
        assertThat(context.topicOutline()).hasSize(1);
        verify(documentRepository, never())
                .findAllByStudySessionIdAndStatusOrderByCreatedAtAsc(any(), any());
    }

    @Test
    @DisplayName("학습 주제 요약에 제목·요약·핵심 개념이 함께 담긴다")
    void outlineCarriesTitleSummaryAndKeyPoints() {
        givenGeneralKnowledge();
        given(topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(SESSION_ID))
                .willReturn(List.of(topic("프로세스", List.of("PCB", "문맥 교환"))));

        StudyChatContext context = provider.provide(session, "질문");

        assertThat(context.topicOutline().get(0))
                .contains("프로세스")
                .contains("요약")
                .contains("PCB")
                .contains("문맥 교환");
    }

    @Test
    @DisplayName("학습 주제도 자료도 없으면 비어 있다고 알린다")
    void reportsEmptyWhenNothingToGroundOn() {
        given(topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(SESSION_ID))
                .willReturn(List.of());

        StudyChatContext context = provider.provide(session, "질문");

        assertThat(context.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("과목명과 시험 범위를 함께 넘긴다 — 답변이 범위 밖으로 새지 않게")
    void carriesSubjectAndExamScope() {
        givenGeneralKnowledge();
        given(topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(SESSION_ID))
                .willReturn(List.of(topic("프로세스", List.of("PCB"))));

        StudyChatContext context = provider.provide(session, "질문");

        assertThat(context.subject()).isEqualTo("운영체제");
        assertThat(context.examScope()).isEqualTo("3장 프로세스");
    }
}

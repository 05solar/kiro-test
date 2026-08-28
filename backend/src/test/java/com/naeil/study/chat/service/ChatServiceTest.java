package com.naeil.study.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naeil.study.chat.client.FakeAiChatClient;
import com.naeil.study.chat.client.dto.AiChatAnswer;
import com.naeil.study.chat.client.dto.AiChatRequest;
import com.naeil.study.chat.context.StudyChatContext;
import com.naeil.study.chat.context.StudyContextProvider;
import com.naeil.study.chat.dto.ChatResponse;
import com.naeil.study.chat.entity.ChatMessage;
import com.naeil.study.chat.entity.ChatRole;
import com.naeil.study.chat.exception.ChatFailedException;
import com.naeil.study.chat.exception.ChatNotReadyException;
import com.naeil.study.chat.repository.ChatMessageRepository;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.entity.StudySourceType;
import com.naeil.study.session.service.SessionService;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService - 학습 챗봇")
class ChatServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 22, 0, 0);
    private static final UUID SESSION_ID = UUID.fromString("3a1c0000-0000-4000-8000-000000000001");
    private static final String SESSION_CODE = "7K2M9QXF";
    private static final int HISTORY_SIZE = 8;

    @Mock
    private SessionService sessionService;

    @Mock
    private StudyContextProvider contextProvider;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    private FakeAiChatClient aiChatClient;
    private ChatService service;
    private StudySession session;

    @BeforeEach
    void setUp() throws Exception {
        aiChatClient = new FakeAiChatClient();
        service = new ChatService(
                sessionService,
                contextProvider,
                aiChatClient,
                chatMessageRepository,
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE),
                HISTORY_SIZE);

        session = StudySession.create(SESSION_CODE, NOW.minusHours(5), 30L);
        setId(session, SESSION_ID);
        session.updateExamInfo("운영체제", "3장 프로세스", NOW.plusHours(6), 180, 180, NOW.minusHours(4));
    }

    private void setId(StudySession target, UUID id) throws Exception {
        Field field = StudySession.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    private StudyChatContext groundedContext() {
        return new StudyChatContext(true, "운영체제", "3장 프로세스",
                List.of("프로세스와 스레드 - 요약"), "프로세스는 실행 중인 프로그램이다.", "STEP 1");
    }

    private StudyChatContext generalKnowledgeContext() {
        return new StudyChatContext(false, "운영체제", "3장 프로세스",
                List.of("프로세스와 스레드 - 요약"), "", "");
    }

    private void givenSession() {
        given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
    }

    private void givenContext(StudyChatContext context) {
        given(contextProvider.provide(any(StudySession.class), any(String.class))).willReturn(context);
    }

    private void givenNoHistory() {
        given(chatMessageRepository.findRecent(any(UUID.class), any())).willReturn(List.of());
    }

    @Nested
    @DisplayName("질문에 답한다")
    class Ask {

        @Test
        @DisplayName("답변과 근거 표시를 함께 돌려준다")
        void returnsAnswerWithBasis() {
            givenSession();
            givenContext(groundedContext());
            givenNoHistory();

            ChatResponse response = service.ask(SESSION_CODE, "프로세스랑 스레드 차이가 뭐야?");

            assertThat(response.answer()).isEqualTo("가짜 답변입니다.");
            assertThat(response.grounded()).isTrue();
            assertThat(response.answeredFromMaterial()).isTrue();
            assertThat(response.answeredAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("질문과 답변을 한 번에 저장한다 — 답 없는 질문만 남지 않게")
        void savesQuestionAndAnswerTogether() {
            givenSession();
            givenContext(groundedContext());
            givenNoHistory();

            service.ask(SESSION_CODE, "  프로세스가 뭐야?  ");

            ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
            verify(chatMessageRepository).saveAll(captor.capture());

            List<ChatMessage> saved = captor.getValue();
            assertThat(saved).hasSize(2);
            assertThat(saved.get(0).getRole()).isEqualTo(ChatRole.USER);
            // 앞뒤 공백은 저장 전에 다듬는다.
            assertThat(saved.get(0).getContent()).isEqualTo("프로세스가 뭐야?");
            assertThat(saved.get(1).getRole()).isEqualTo(ChatRole.ASSISTANT);
            assertThat(saved.get(1).getContent()).isEqualTo("가짜 답변입니다.");
        }

        @Test
        @DisplayName("순서를 시각이 아니라 번호로 남긴다 — 두 줄은 같은 시각이다")
        void numbersMessagesInsteadOfRelyingOnTime() {
            givenSession();
            givenContext(groundedContext());
            givenNoHistory();
            // 이미 두 줄이 쌓여 있다. 이어지는 번호를 받아야 한다.
            given(chatMessageRepository.findLatestOrder(SESSION_ID)).willReturn(2);

            service.ask(SESSION_CODE, "질문");

            ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
            verify(chatMessageRepository).saveAll(captor.capture());

            List<ChatMessage> saved = captor.getValue();
            assertThat(saved.get(0).getMessageOrder()).isEqualTo(3);
            assertThat(saved.get(1).getMessageOrder()).isEqualTo(4);
            // 시각이 같다는 것이 이 번호가 필요한 이유다. 시각만으로는 순서를 정할 수 없다.
            assertThat(saved.get(0).getCreatedAt()).isEqualTo(saved.get(1).getCreatedAt());
        }

        @Test
        @DisplayName("AI 호출이 실패하면 아무것도 저장하지 않는다")
        void savesNothingWhenAiFails() {
            givenSession();
            givenContext(groundedContext());
            givenNoHistory();
            aiChatClient.failWith(new ChatFailedException("boom"));

            assertThatThrownBy(() -> service.ask(SESSION_CODE, "질문"))
                    .isInstanceOf(ChatFailedException.class);

            verify(chatMessageRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("AI 가 빈 답을 주면 실패로 다룬다 — 빈 말풍선을 남기지 않는다")
        void rejectsEmptyAnswer() {
            givenSession();
            givenContext(groundedContext());
            givenNoHistory();
            aiChatClient.respondWith(request -> new AiChatAnswer("   ", true));

            assertThatThrownBy(() -> service.ask(SESSION_CODE, "질문"))
                    .isInstanceOf(ChatFailedException.class);

            verify(chatMessageRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("근거가 하나도 없으면 AI 를 부르지 않는다")
        void doesNotCallAiWithoutContext() {
            givenSession();
            givenContext(new StudyChatContext(false, "운영체제", "", List.of(), "", ""));

            assertThatThrownBy(() -> service.ask(SESSION_CODE, "질문"))
                    .isInstanceOf(ChatNotReadyException.class);

            assertThat(aiChatClient.callCount()).isZero();
            verify(chatMessageRepository, never()).saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("근거를 사실대로 표시한다")
    class Basis {

        @Test
        @DisplayName("자료가 없는 세션은 AI 가 뭐라 답하든 자료 기반이 아니다")
        void neverClaimsMaterialWhenThereIsNone() {
            givenSession();
            givenContext(generalKnowledgeContext());
            givenNoHistory();
            // 모델이 규칙을 어기고 true 로 답해도 그대로 내보내지 않는다.
            aiChatClient.respondWith(request -> new AiChatAnswer("자료에 따르면...", true));

            ChatResponse response = service.ask(SESSION_CODE, "질문");

            assertThat(response.grounded()).isFalse();
            assertThat(response.answeredFromMaterial()).isFalse();
        }

        @Test
        @DisplayName("자료가 있어도 모델이 자료 밖이라고 하면 그대로 알린다")
        void reportsWhenAnswerIsNotFromMaterial() {
            givenSession();
            givenContext(groundedContext());
            givenNoHistory();
            aiChatClient.respondWith(request -> new AiChatAnswer("자료에서 못 찾았어요.", false));

            ChatResponse response = service.ask(SESSION_CODE, "질문");

            assertThat(response.grounded()).isTrue();
            assertThat(response.answeredFromMaterial()).isFalse();
        }

        @Test
        @DisplayName("모델이 표시를 빠뜨리면 자료 기반이 아닌 것으로 본다")
        void treatsMissingFlagAsNotFromMaterial() {
            givenSession();
            givenContext(groundedContext());
            givenNoHistory();
            aiChatClient.respondWith(request -> new AiChatAnswer("답변", null));

            ChatResponse response = service.ask(SESSION_CODE, "질문");

            assertThat(response.answeredFromMaterial()).isFalse();
        }
    }

    @Nested
    @DisplayName("지난 대화")
    class History {

        @Test
        @DisplayName("서버에 저장된 것만, 오래된 것부터 프롬프트에 넣는다")
        void sendsStoredHistoryInChronologicalOrder() {
            givenSession();
            givenContext(groundedContext());
            // 리포지터리는 새 것부터 준다.
            given(chatMessageRepository.findRecent(any(UUID.class), any())).willReturn(List.of(
                    ChatMessage.assistant(session, "두 번째 답", 4, NOW.minusMinutes(1)),
                    ChatMessage.user(session, "두 번째 질문", 3, NOW.minusMinutes(2)),
                    ChatMessage.assistant(session, "첫 답", 2, NOW.minusMinutes(3)),
                    ChatMessage.user(session, "첫 질문", 1, NOW.minusMinutes(4))));

            service.ask(SESSION_CODE, "세 번째 질문");

            AiChatRequest sent = aiChatClient.lastRequest();
            assertThat(sent.history()).extracting("content")
                    .containsExactly("첫 질문", "첫 답", "두 번째 질문", "두 번째 답");
            assertThat(sent.history()).extracting("assistant")
                    .containsExactly(false, true, false, true);
            assertThat(sent.question()).isEqualTo("세 번째 질문");
        }

        @Test
        @DisplayName("설정한 개수만 읽는다 — 대화가 길어져도 요청이 무한정 커지지 않는다")
        void limitsHistorySize() {
            givenSession();
            givenContext(groundedContext());
            givenNoHistory();

            service.ask(SESSION_CODE, "질문");

            ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                    ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
            verify(chatMessageRepository).findRecent(any(UUID.class), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(HISTORY_SIZE);
        }
    }

    @Nested
    @DisplayName("대화 조회")
    class ReadHistory {

        @Test
        @DisplayName("대화가 하나도 없어도 근거 표시는 돌려준다")
        void returnsGroundedEvenWhenEmpty() {
            session.startAnalyzing(StudySourceType.USER_MATERIAL, NOW.minusHours(1));
            givenSession();
            given(chatMessageRepository.findAllBySessionIdOrderByMessageOrderAsc(SESSION_ID))
                    .willReturn(List.of());

            ChatService.ChatHistory history = service.history(SESSION_CODE);

            assertThat(history.messages()).isEmpty();
            assertThat(history.grounded()).isTrue();
        }
    }
}

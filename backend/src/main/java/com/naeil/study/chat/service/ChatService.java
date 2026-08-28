package com.naeil.study.chat.service;

import com.naeil.study.chat.client.AiChatClient;
import com.naeil.study.chat.client.dto.AiChatAnswer;
import com.naeil.study.chat.client.dto.AiChatRequest;
import com.naeil.study.chat.client.dto.AiChatTurn;
import com.naeil.study.chat.context.StudyChatContext;
import com.naeil.study.chat.context.StudyContextProvider;
import com.naeil.study.chat.dto.ChatResponse;
import com.naeil.study.chat.entity.ChatMessage;
import com.naeil.study.chat.entity.ChatRole;
import com.naeil.study.chat.exception.ChatFailedException;
import com.naeil.study.chat.exception.ChatNotReadyException;
import com.naeil.study.chat.repository.ChatMessageRepository;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 학습 챗봇 대화.
 *
 * <p><b>클래스에 트랜잭션을 걸지 않는다.</b> AI 호출이 수십 초 걸릴 수 있는데 그동안 DB
 * 커넥션을 잡아 두지 않기 위해서다(분석·퀴즈 서비스와 같은 이유). 조회는 각 리포지터리
 * 호출의 짧은 트랜잭션으로 충분하고, 저장은 마지막에 두 줄을 한 번에 넣는다.
 *
 * <p><b>질문과 답을 함께 저장한다.</b> 질문을 먼저 저장해 두면 AI 호출이 실패했을 때
 * 답 없는 질문만 남고, 그것이 다음 요청의 프롬프트에 "답하지 않은 질문"으로 섞여 들어간다.
 * 성공한 한 쌍만 기록에 남긴다.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final SessionService sessionService;
    private final StudyContextProvider contextProvider;
    private final AiChatClient aiChatClient;
    private final ChatMessageRepository chatMessageRepository;
    private final Clock clock;
    private final int historySize;

    public ChatService(
            SessionService sessionService,
            StudyContextProvider contextProvider,
            AiChatClient aiChatClient,
            ChatMessageRepository chatMessageRepository,
            Clock clock,
            @Value("${chat.history-size:8}") int historySize
    ) {
        this.sessionService = sessionService;
        this.contextProvider = contextProvider;
        this.aiChatClient = aiChatClient;
        this.chatMessageRepository = chatMessageRepository;
        this.clock = clock;
        this.historySize = historySize;
    }

    /**
     * 질문에 답하고 그 대화를 기록한다.
     *
     * <p>세션 조회는 {@link SessionService#getSessionAndTouch} 를 지난다. 다른 세션의 코드로는
     * 아무것도 볼 수 없고, 없는 코드와 만료된 코드는 같은 응답을 받는다.
     */
    public ChatResponse ask(String sessionCode, String question) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);

        String trimmed = question.strip();
        StudyChatContext context = contextProvider.provide(session, trimmed);
        if (context.isEmpty()) {
            // 학습 주제도 자료도 없다. 이 상태에서 답하면 무엇에도 근거하지 않은 상식이 나가고,
            // 사용자는 그것을 자기 시험 범위의 내용으로 받아들인다.
            throw new ChatNotReadyException();
        }

        AiChatAnswer answer = aiChatClient.answer(
                new AiChatRequest(context, recentHistory(session), trimmed));
        String body = answer.answer() == null ? "" : answer.answer().strip();
        if (body.isBlank()) {
            throw new ChatFailedException("ai returned an empty answer");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        chatMessageRepository.saveAll(List.of(
                ChatMessage.user(session, trimmed, now),
                ChatMessage.assistant(session, body, now)));

        // 질문도 답변도 로그에 남기지 않는다. 사용자가 약점을 그대로 적는 자리다.
        boolean fromMaterial = context.grounded() && Boolean.TRUE.equals(answer.answeredFromMaterial());
        log.info("study chat answered: sessionId={}, grounded={}, fromMaterial={}, answerLength={}",
                session.getId(), context.grounded(), fromMaterial, body.length());

        return new ChatResponse(body, context.grounded(), fromMaterial, now);
    }

    /**
     * 세션의 대화 전체. 오래된 것부터.
     *
     * <p>근거 여부를 함께 돌려준다. {@code ChatMessage.session} 은 지연 로딩이라 트랜잭션
     * 밖에서 건드리면 초기화에 실패하고, 대화가 하나도 없으면 애초에 꺼낼 곳도 없다.
     */
    public ChatHistory history(String sessionCode) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        return new ChatHistory(
                session.isGrounded(),
                chatMessageRepository.findAllBySessionIdOrderByCreatedAtAscIdAsc(session.getId()));
    }

    /** 대화 기록과, 그 대화가 무엇에 근거하는지. */
    public record ChatHistory(boolean grounded, List<ChatMessage> messages) {
    }

    /**
     * 프롬프트에 넣을 지난 대화. 오래된 것부터.
     *
     * <p>전체를 넣지 않는다. 요청이 길어지고, 지금 묻는 것과 관계없는 옛 화제가 답변을 끌고 간다.
     */
    private List<AiChatTurn> recentHistory(StudySession session) {
        List<ChatMessage> recent = chatMessageRepository
                .findRecent(session.getId(), PageRequest.of(0, historySize));

        List<AiChatTurn> turns = new ArrayList<>(recent.size());
        // 조회는 새 것부터다. 프롬프트에는 시간 순서로 넣어야 대화가 이어져 읽힌다.
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatMessage message = recent.get(i);
            turns.add(new AiChatTurn(message.getRole() == ChatRole.ASSISTANT, message.getContent()));
        }
        return turns;
    }
}

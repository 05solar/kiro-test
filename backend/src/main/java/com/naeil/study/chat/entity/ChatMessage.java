package com.naeil.study.chat.entity;

import com.naeil.study.session.entity.StudySession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학습 챗봇의 대화 한 줄.
 *
 * <p><b>대화 기록을 서버가 갖는 이유는 두 가지다.</b>
 *
 * <p>첫째, 8자리 코드만으로 다른 기기에서 이어지는 것이 이 서비스의 약속이다. 화면이
 * 들고 있으면 새로고침이나 기기 변경에 사라진다.
 *
 * <p>둘째, <b>지난 대화를 클라이언트가 보내오게 두면 안 된다.</b> 그렇게 하면 "너는 아까
 * 정답이 3번이라고 했다"처럼 없던 발화를 지어내 보낼 수 있고, 그 조작된 기록이 그대로
 * 다음 프롬프트에 들어간다. 무엇을 말했는지는 말한 쪽이 기록한다.
 *
 * <p>{@code @Lob} 을 쓰지 않는다. PostgreSQL 에서 {@code @Lob String} 은 OID 로 저장되어
 * 값이 깨진다(4단계에서 실제로 겪었다). 긴 문자열은 {@code TEXT} 로 둔다.
 */
@Entity
@Table(
        name = "chat_messages",
        // 조회는 항상 "이 세션의 최근 메시지"다. 세션이 늘어나도 전체를 훑지 않게 한다.
        indexes = @Index(name = "idx_chat_messages_session_id_created_at",
                columnList = "session_id, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private StudySession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20, updatable = false)
    private ChatRole role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ChatMessage(StudySession session, ChatRole role, String content, LocalDateTime now) {
        this.session = session;
        this.role = role;
        this.content = content;
        this.createdAt = now;
    }

    public static ChatMessage user(StudySession session, String content, LocalDateTime now) {
        return new ChatMessage(session, ChatRole.USER, content, now);
    }

    public static ChatMessage assistant(StudySession session, String content, LocalDateTime now) {
        return new ChatMessage(session, ChatRole.ASSISTANT, content, now);
    }
}

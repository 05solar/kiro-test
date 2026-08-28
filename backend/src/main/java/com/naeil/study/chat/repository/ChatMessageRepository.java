package com.naeil.study.chat.repository;

import com.naeil.study.chat.entity.ChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * 세션의 최근 대화를 <b>새 것부터</b> 가져온다.
     *
     * <p>프롬프트에 넣을 것은 최근 몇 개뿐이라 전체를 읽지 않는다. 오래된 대화까지 넣으면
     * 요청이 길어지고, 지금 묻는 것과 관계없는 내용이 답변을 끌고 간다.
     *
     * <p>호출부에서 시간 순서로 뒤집어 쓴다.
     */
    @Query("select message from ChatMessage message "
            + "where message.session.id = :sessionId "
            + "order by message.createdAt desc, message.id desc")
    List<ChatMessage> findRecent(@Param("sessionId") UUID sessionId, Pageable pageable);

    /** 화면에 처음부터 다시 그릴 때 쓴다. 오래된 것부터. */
    List<ChatMessage> findAllBySessionIdOrderByCreatedAtAscIdAsc(UUID sessionId);
}

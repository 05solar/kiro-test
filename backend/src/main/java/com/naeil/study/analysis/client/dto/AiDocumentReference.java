package com.naeil.study.analysis.client.dto;

import java.util.UUID;

/**
 * AI에게 보여줄 문서 참조.
 *
 * <p>AI에게는 내부 UUID 대신 {@code DOC_1} 같은 짧은 참조값을 준다.
 * LLM이 그럴듯한 UUID를 지어내면 존재하지 않는 문서를 가리키게 되기 때문이다.
 * 응답에 돌아온 참조값은 서버가 실제 UUID로 되돌린다.
 *
 * @param reference AI용 참조값 (예: {@code DOC_1})
 */
public record AiDocumentReference(String reference, UUID documentId, String fileName) {
}

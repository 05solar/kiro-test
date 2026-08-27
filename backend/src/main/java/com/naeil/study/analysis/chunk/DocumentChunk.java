package com.naeil.study.analysis.chunk;

import java.util.UUID;

/**
 * AI에 보낼 강의자료 조각 하나.
 *
 * <p>어느 문서의 몇 번째 조각인지 함께 들고 다닌다. 최종 통합 단계에서
 * 주제가 어느 문서에서 나왔는지 되짚기 위해서다.
 *
 * @param chunkIndex 문서 안에서의 순서. 0부터 시작한다
 */
public record DocumentChunk(UUID documentId, String fileName, int chunkIndex, String text) {
}

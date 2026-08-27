package com.naeil.study.analysis.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 문서 텍스트를 AI에 보낼 크기로 나눈다.
 *
 * <p>여러 문서를 한 문자열로 붙여 한 번에 보내면 모델의 컨텍스트 한도를 넘길 수 있다.
 * 문서마다 따로 나누고, 어느 문서의 몇 번째 조각인지 함께 들고 다닌다.
 *
 * <p><b>문단 경계를 우선한다.</b> 정해진 글자 수에서 그냥 자르면 문장이 중간에 끊겨
 * 분석 품질이 떨어진다. 목표 크기 근처의 빈 줄 → 줄바꿈 → 공백 순으로 끊을 자리를 찾고,
 * 마땅한 자리가 없을 때만 글자 수로 자른다.
 *
 * <p>토큰 수가 아니라 글자 수로 나눈다. 특정 모델의 토크나이저에 코드가 묶이지 않게 하기 위해서다.
 * 크기와 겹침은 설정값이며 코드에 숫자를 흩어 놓지 않는다.
 */
@Component
public class DocumentChunker {

    /** 끊을 자리를 찾을 때 목표 크기에서 뒤로 훑어볼 범위의 비율. */
    private static final double BOUNDARY_SEARCH_RATIO = 0.2;

    private final int chunkSize;
    private final int overlap;

    public DocumentChunker(
            @Value("${ai.analysis.chunk-size:8000}") int chunkSize,
            @Value("${ai.analysis.chunk-overlap:300}") int overlap
    ) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunk-size must be positive: " + chunkSize);
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("chunk-overlap must be in [0, chunk-size): " + overlap);
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    /**
     * 문서 하나의 텍스트를 조각으로 나눈다.
     *
     * @return 원문 순서대로 정렬된 조각. 내용이 없으면 빈 목록
     */
    public List<DocumentChunk> chunk(UUID documentId, String fileName, String text) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        int position = 0;
        int index = 0;
        while (position < text.length()) {
            int end = findChunkEnd(text, position);
            String piece = text.substring(position, end).strip();
            if (!piece.isEmpty()) {
                chunks.add(new DocumentChunk(documentId, fileName, index++, piece));
            }
            if (end >= text.length()) {
                break;
            }
            position = nextPosition(position, end);
        }
        return chunks;
    }

    /**
     * 조각의 끝 위치를 정한다.
     *
     * <p>목표 크기를 넘지 않는 선에서 문단 경계를 찾는다.
     */
    private int findChunkEnd(String text, int start) {
        int hardEnd = Math.min(start + chunkSize, text.length());
        if (hardEnd >= text.length()) {
            return text.length();
        }

        int searchFrom = hardEnd - (int) (chunkSize * BOUNDARY_SEARCH_RATIO);
        int boundary = lastIndexOfBetween(text, "\n\n", searchFrom, hardEnd);
        if (boundary < 0) {
            boundary = lastIndexOfBetween(text, "\n", searchFrom, hardEnd);
        }
        if (boundary < 0) {
            boundary = lastIndexOfBetween(text, " ", searchFrom, hardEnd);
        }
        // 경계를 못 찾으면 목표 크기에서 자른다. 표나 코드처럼 줄바꿈이 없는 구간이 있다.
        return boundary > start ? boundary : hardEnd;
    }

    private int lastIndexOfBetween(String text, String separator, int from, int to) {
        int found = text.lastIndexOf(separator, to - 1);
        return found >= from ? found : -1;
    }

    /**
     * 다음 조각의 시작 위치. 겹침만큼 뒤로 물러난다.
     *
     * <p>경계에서 끊긴 문장이 다음 조각 앞부분에 다시 나오게 해서 문맥이 끊기지 않게 한다.
     * 진행이 멈추지 않도록 항상 앞으로 나아가는지 확인한다.
     */
    private int nextPosition(int currentStart, int end) {
        int next = end - overlap;
        return next > currentStart ? next : end;
    }
}

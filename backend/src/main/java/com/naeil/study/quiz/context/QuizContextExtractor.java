package com.naeil.study.quiz.context;

import com.naeil.study.document.entity.Document;
import com.naeil.study.topic.entity.Topic;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Topic 과 관련된 강의자료 구간을 추출한다.
 *
 * <p>문서 전체를 매번 AI에 보내면 비용이 크고, 관련 없는 내용이 문제의 근거로 쓰일 수 있다.
 * MVP 에서는 임베딩·벡터 검색 없이 <b>키워드 기반 문단 선택</b>으로 처리한다.
 *
 * <pre>
 * 1. 문서 텍스트를 빈 줄 기준 문단으로 나눈다
 * 2. Topic 제목과 keyPoints 가 나타나는 문단과 그 앞뒤 문단을 고른다
 * 3. 문서 순서를 유지하며 이어붙이되 전체 길이 상한을 지킨다
 * </pre>
 *
 * <p>키워드가 한 번도 나타나지 않는 문서는 앞부분부터 담는다. Topic 은 그 문서에서 나온
 * 것이므로(sourceDocumentIds) 표기 차이로 문자열 매칭이 실패해도 내용 자체는 관련이 있다.
 * 빈 결과로 생성을 실패시키는 것보다 낫다.
 *
 * <p>이후 벡터 검색을 도입하면 이 컴포넌트만 교체한다. 호출부는 바뀌지 않는다.
 */
@Component
public class QuizContextExtractor {

    /** 키워드가 걸린 문단의 앞뒤로 함께 담을 문단 수. */
    private static final int NEIGHBOR_PARAGRAPHS = 1;

    private final int maxContextCharacters;

    public QuizContextExtractor(
            @Value("${quiz.max-context-characters:20000}") int maxContextCharacters) {
        this.maxContextCharacters = maxContextCharacters;
    }

    /**
     * 문서들에서 Topic 관련 구간을 추출해 하나의 텍스트로 만든다.
     *
     * @return 추출한 텍스트. 쓸 수 있는 텍스트가 전혀 없으면 빈 문자열
     */
    public String extract(Topic topic, List<Document> documents) {
        return extract(topic, documents, maxContextCharacters);
    }

    /**
     * 길이 상한을 직접 지정해 추출한다.
     *
     * <p>오답 요약처럼 여러 Topic 의 구간을 한 요청에 담는 호출자는 Topic 당 예산이
     * 기본 상한보다 작아야 한다.
     */
    public String extract(Topic topic, List<Document> documents, int maxCharacters) {
        List<String> keywords = keywordsOf(topic);
        StringBuilder context = new StringBuilder();

        for (Document document : documents) {
            if (context.length() >= maxCharacters) {
                break;
            }
            String text = document.getExtractedText();
            if (text == null || text.isBlank()) {
                continue;
            }
            String selected = selectRelevantText(text, keywords,
                    maxCharacters - context.length());
            if (selected.isBlank()) {
                continue;
            }
            if (context.length() > 0) {
                context.append("\n\n");
            }
            context.append(selected);
        }
        return context.toString();
    }

    private List<String> keywordsOf(Topic topic) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        keywords.add(topic.getTitle().strip().toLowerCase(Locale.ROOT));
        for (String keyPoint : topic.getKeyPoints()) {
            if (keyPoint != null && !keyPoint.isBlank()) {
                keywords.add(keyPoint.strip().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(keywords);
    }

    /**
     * 한 문서에서 관련 문단을 고른다. 키워드가 전혀 없으면 문서 앞부분을 그대로 쓴다.
     */
    private String selectRelevantText(String text, List<String> keywords, int limit) {
        String[] paragraphs = text.split("\\n{2,}");
        boolean[] selected = new boolean[paragraphs.length];
        boolean anyMatch = false;

        for (int i = 0; i < paragraphs.length; i++) {
            if (containsAnyKeyword(paragraphs[i], keywords)) {
                anyMatch = true;
                int from = Math.max(0, i - NEIGHBOR_PARAGRAPHS);
                int to = Math.min(paragraphs.length - 1, i + NEIGHBOR_PARAGRAPHS);
                for (int j = from; j <= to; j++) {
                    selected[j] = true;
                }
            }
        }

        if (!anyMatch) {
            return truncate(text.strip(), limit);
        }

        List<String> picked = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < paragraphs.length; i++) {
            if (!selected[i]) {
                continue;
            }
            String paragraph = paragraphs[i].strip();
            if (paragraph.isEmpty()) {
                continue;
            }
            if (used + paragraph.length() > limit) {
                int room = limit - used;
                if (room > 0) {
                    picked.add(truncate(paragraph, room));
                }
                break;
            }
            picked.add(paragraph);
            used += paragraph.length();
        }
        return String.join("\n\n", picked);
    }

    private boolean containsAnyKeyword(String paragraph, List<String> keywords) {
        String lower = paragraph.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, Math.max(0, limit));
    }
}

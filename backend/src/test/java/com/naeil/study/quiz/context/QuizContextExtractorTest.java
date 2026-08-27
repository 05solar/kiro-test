package com.naeil.study.quiz.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.storage.StoredFile;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QuizContextExtractor - 강의자료 관련 구간 추출")
class QuizContextExtractorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 20, 0, 0);
    private static final int MAX_CHARACTERS = 200;

    private final QuizContextExtractor extractor = new QuizContextExtractor(MAX_CHARACTERS);

    private Topic topic(String title, List<String> keyPoints) {
        return Topic.create(null, title, "요약", keyPoints, TopicImportance.HIGH, 30,
                false, false, false, false, List.of(), 1, NOW);
    }

    private Document document(String text) {
        Document document = Document.create(null, "강의.txt",
                new StoredFile("stored.txt", "sessions/x/stored.txt"),
                DocumentFileType.TXT, text.length(), NOW);
        document.markParsed(text, NOW);
        return document;
    }

    @Test
    @DisplayName("Topic 제목이 나타나는 문단과 이웃 문단을 추출한다")
    void extractsParagraphsContainingTitle() {
        String text = String.join("\n\n",
                "1장 서론입니다.",
                "CPU 스케줄링은 준비 큐를 다룬다.",
                "다음 문단은 이어지는 설명이다.",
                "전혀 관련 없는 파일 시스템 내용.",
                "역시 관련 없는 내용.");

        String context = extractor.extract(topic("CPU 스케줄링", List.of()), List.of(document(text)));

        assertThat(context).contains("CPU 스케줄링은 준비 큐를 다룬다.");
        // 이웃 문단도 함께 담는다
        assertThat(context).contains("1장 서론입니다.");
        assertThat(context).contains("다음 문단은 이어지는 설명이다.");
        // 멀리 떨어진 무관한 문단은 담지 않는다
        assertThat(context).doesNotContain("역시 관련 없는 내용.");
    }

    @Test
    @DisplayName("keyPoint 가 나타나는 문단도 추출한다")
    void extractsParagraphsContainingKeyPoint() {
        String text = String.join("\n\n",
                "무관한 문단 하나.",
                "무관한 문단 둘.",
                "무관한 문단 셋.",
                "Round Robin 은 시간 할당량을 사용한다.");

        String context = extractor.extract(
                topic("스케줄링 개요", List.of("Round Robin")), List.of(document(text)));

        assertThat(context).contains("Round Robin 은 시간 할당량을 사용한다.");
        assertThat(context).doesNotContain("무관한 문단 하나.");
    }

    @Test
    @DisplayName("키워드 대소문자가 달라도 찾는다")
    void matchesCaseInsensitively() {
        String text = "무관한 첫 문단.\n\n무관한 둘째 문단.\n\nround robin 방식은 선점형이다.";

        String context = extractor.extract(
                topic("스케줄링", List.of("Round Robin")), List.of(document(text)));

        assertThat(context).contains("round robin 방식은 선점형이다.");
    }

    @Test
    @DisplayName("여러 출처 문서에서 추출해 이어붙인다")
    void extractsFromMultipleDocuments() {
        Document first = document("무관.\n\n무관 2.\n\nCPU 스케줄링 설명 첫 문서.");
        Document second = document("무관 3.\n\n무관 4.\n\nCPU 스케줄링 설명 둘째 문서.");

        String context = extractor.extract(topic("CPU 스케줄링", List.of()), List.of(first, second));

        assertThat(context).contains("첫 문서");
        assertThat(context).contains("둘째 문서");
    }

    @Test
    @DisplayName("키워드가 전혀 없으면 문서 앞부분을 그대로 쓴다")
    void fallsBackToDocumentHeadWhenNoMatch() {
        String text = "표기 차이로 키워드가 걸리지 않는 본문이다.\n\n하지만 이 문서가 이 주제의 출처다.";

        String context = extractor.extract(topic("찾을 수 없는 제목", List.of("없는 키워드")),
                List.of(document(text)));

        assertThat(context).startsWith("표기 차이로 키워드가 걸리지 않는 본문이다.");
        assertThat(context).isNotBlank();
    }

    @Test
    @DisplayName("전체 길이 상한을 넘지 않는다")
    void respectsMaxCharacters() {
        String longText = ("CPU 스케줄링 " + "내용 ".repeat(200)).strip();

        String context = extractor.extract(topic("CPU 스케줄링", List.of()),
                List.of(document(longText), document(longText)));

        assertThat(context.length()).isLessThanOrEqualTo(MAX_CHARACTERS);
    }

    @Test
    @DisplayName("텍스트가 없는 문서는 건너뛴다")
    void skipsDocumentsWithoutText() {
        Document empty = Document.create(null, "빈문서.txt",
                new StoredFile("stored2.txt", "sessions/x/stored2.txt"),
                DocumentFileType.TXT, 0, NOW);
        Document withText = document("무관 문단.\n\n무관 문단 2.\n\nCPU 스케줄링 내용이다.");

        String context = extractor.extract(topic("CPU 스케줄링", List.of()), List.of(empty, withText));

        assertThat(context).contains("CPU 스케줄링 내용이다.");
    }

    @Test
    @DisplayName("쓸 수 있는 텍스트가 전혀 없으면 빈 문자열을 돌려준다")
    void returnsEmptyWhenNothingUsable() {
        Document empty = Document.create(null, "빈문서.txt",
                new StoredFile("stored3.txt", "sessions/x/stored3.txt"),
                DocumentFileType.TXT, 0, NOW);

        assertThat(extractor.extract(topic("주제", List.of()), List.of(empty))).isEmpty();
        assertThat(extractor.extract(topic("주제", List.of()), List.of())).isEmpty();
    }
}

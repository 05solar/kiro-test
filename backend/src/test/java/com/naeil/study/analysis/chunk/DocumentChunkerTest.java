package com.naeil.study.analysis.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DocumentChunker - 강의자료 조각 나누기")
class DocumentChunkerTest {

    private static final UUID DOCUMENT_ID = UUID.fromString("11111111-0000-4000-8000-000000000001");
    private static final String FILE_NAME = "운영체제_1주차.pdf";

    private DocumentChunker chunker(int chunkSize, int overlap) {
        return new DocumentChunker(chunkSize, overlap);
    }

    @Test
    @DisplayName("짧은 문서는 조각 하나가 된다")
    void shortTextBecomesSingleChunk() {
        List<DocumentChunk> chunks = chunker(8000, 300)
                .chunk(DOCUMENT_ID, FILE_NAME, "운영체제에서 프로세스는 실행 중인 프로그램을 의미한다.");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).chunkIndex()).isZero();
        assertThat(chunks.get(0).text()).isEqualTo("운영체제에서 프로세스는 실행 중인 프로그램을 의미한다.");
    }

    @Test
    @DisplayName("긴 문서는 여러 조각이 된다")
    void longTextBecomesMultipleChunks() {
        String text = ("가".repeat(200) + "\n\n").repeat(20);

        List<DocumentChunk> chunks = chunker(1000, 100).chunk(DOCUMENT_ID, FILE_NAME, text);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text().length()).isLessThanOrEqualTo(1000));
    }

    @Test
    @DisplayName("조각 순서와 chunkIndex가 원문 순서와 같다")
    void keepsOrderAndIndex() {
        String text = "AAAA\n\nBBBB\n\nCCCC\n\nDDDD\n\nEEEE";

        List<DocumentChunk> chunks = chunker(12, 0).chunk(DOCUMENT_ID, FILE_NAME, text);

        assertThat(chunks.size()).isGreaterThan(1);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
        }
        String joined = String.join("", chunks.stream().map(DocumentChunk::text).toList());
        assertThat(joined).contains("AAAA").contains("EEEE");
    }

    @Test
    @DisplayName("문서 정보를 조각마다 유지한다")
    void keepsDocumentInformation() {
        List<DocumentChunk> chunks = chunker(50, 0)
                .chunk(DOCUMENT_ID, FILE_NAME, "가".repeat(300));

        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.documentId()).isEqualTo(DOCUMENT_ID);
            assertThat(chunk.fileName()).isEqualTo(FILE_NAME);
        });
    }

    @Test
    @DisplayName("빈 텍스트는 조각을 만들지 않는다")
    void returnsEmptyForBlankText() {
        DocumentChunker chunker = chunker(8000, 300);

        assertThat(chunker.chunk(DOCUMENT_ID, FILE_NAME, null)).isEmpty();
        assertThat(chunker.chunk(DOCUMENT_ID, FILE_NAME, "")).isEmpty();
        assertThat(chunker.chunk(DOCUMENT_ID, FILE_NAME, "   \n\n  ")).isEmpty();
    }

    @Test
    @DisplayName("목표 크기 근처에 빈 줄이 있으면 그 자리에서 끊는다")
    void splitsAtParagraphBoundary() {
        // "1장 프로세스\n"(8자) + 92자 = 100자 지점에 빈 줄이 오도록 맞춘다.
        // 조각 크기 120에서 경계를 찾는 범위는 96~120이라 이 빈 줄이 후보가 된다.
        String first = "1장 프로세스\n" + "가".repeat(92);
        String second = "2장 스레드\n" + "나".repeat(80);
        String text = first + "\n\n" + second;

        List<DocumentChunk> chunks = chunker(120, 0).chunk(DOCUMENT_ID, FILE_NAME, text);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        // 문단 중간이 아니라 빈 줄에서 끊겼다.
        assertThat(chunks.get(0).text()).isEqualTo(first);
        assertThat(chunks.get(1).text()).startsWith("2장 스레드");
    }

    @Test
    @DisplayName("줄바꿈이 전혀 없어도 조각을 만든다")
    void splitsTextWithoutBoundaries() {
        String text = "가".repeat(500);

        List<DocumentChunk> chunks = chunker(100, 0).chunk(DOCUMENT_ID, FILE_NAME, text);

        assertThat(chunks).hasSize(5);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text()).hasSize(100));
    }

    @Test
    @DisplayName("겹침을 주면 앞 조각의 끝이 다음 조각에 다시 나온다")
    void appliesOverlap() {
        String text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

        List<DocumentChunk> chunks = chunker(10, 3).chunk(DOCUMENT_ID, FILE_NAME, text);

        assertThat(chunks.size()).isGreaterThan(1);
        String firstChunk = chunks.get(0).text();
        String secondChunk = chunks.get(1).text();
        assertThat(secondChunk).startsWith(firstChunk.substring(firstChunk.length() - 3));
    }

    @Test
    @DisplayName("겹침이 있어도 조각 나누기가 끝난다")
    void terminatesWithOverlap() {
        String text = "가".repeat(5000);

        List<DocumentChunk> chunks = chunker(100, 99).chunk(DOCUMENT_ID, FILE_NAME, text);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isLessThan(6000);
    }

    @Test
    @DisplayName("잘못된 설정값은 기동 시점에 막는다")
    void rejectsInvalidSettings() {
        assertThatThrownBy(() -> chunker(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> chunker(100, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> chunker(100, 100)).isInstanceOf(IllegalArgumentException.class);
    }
}

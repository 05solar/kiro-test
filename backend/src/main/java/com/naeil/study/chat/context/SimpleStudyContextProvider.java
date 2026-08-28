package com.naeil.study.chat.context;

import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.repository.CurriculumRepository;
import com.naeil.study.curriculum.repository.StudyStepRepository;
import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentStatus;
import com.naeil.study.document.repository.DocumentRepository;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.repository.TopicRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 키워드로 관련 문단을 고르는 기본 구현.
 *
 * <p>임베딩도 벡터 검색도 쓰지 않는다. 질문에 나온 단어와 학습 주제의 제목·핵심 개념이
 * 등장하는 문단, 그리고 그 앞뒤 문단을 고른다. 벼락치기 자료는 대개 목차와 용어가 그대로
 * 반복되므로 이 정도로도 대부분 걸린다.
 *
 * <pre>
 * 1. 질문에서 두 글자 이상인 단어를 뽑는다
 * 2. 학습 주제의 제목·핵심 개념을 후보 키워드에 더한다
 * 3. 추출된 강의자료를 문단으로 나누고 키워드가 걸린 문단과 그 이웃을 담는다
 * 4. 길이 상한에서 멈춘다
 * </pre>
 *
 * <p><b>퀴즈의 {@code QuizContextExtractor} 와 따로 둔다.</b> 고르는 기준이 다르다. 그쪽은
 * Topic 하나를 기준으로 출제 범위를 넓게 잡고, 이쪽은 <b>방금 던진 질문</b>을 기준으로
 * 좁게 잡는다. 한 클래스에 두 기준을 넣으면 한쪽을 고칠 때마다 다른 쪽이 흔들린다.
 *
 * <p>자료가 없는 세션(일반 지식 기반)에서는 강의자료 구간이 비고, 학습 주제 요약만 남는다.
 * 그 주제 자체가 일반 지식에서 나온 것이므로 답변 범위는 여전히 시험 범위 안에 묶인다.
 */
@Component
public class SimpleStudyContextProvider implements StudyContextProvider {

    /** 키워드가 걸린 문단의 앞뒤로 함께 담을 문단 수. */
    private static final int NEIGHBOR_PARAGRAPHS = 1;
    /** 한 글자 단어는 아무 문단에나 걸린다. 걸러 낸다. */
    private static final int MIN_KEYWORD_LENGTH = 2;
    /** 프롬프트에 넣을 학습 주제 수 상한. 전부 넣으면 질문과 무관한 주제가 답을 끌고 간다. */
    private static final int MAX_TOPICS = 12;

    private final TopicRepository topicRepository;
    private final DocumentRepository documentRepository;
    private final CurriculumRepository curriculumRepository;
    private final StudyStepRepository studyStepRepository;
    private final int maxContextCharacters;

    public SimpleStudyContextProvider(
            TopicRepository topicRepository,
            DocumentRepository documentRepository,
            CurriculumRepository curriculumRepository,
            StudyStepRepository studyStepRepository,
            @Value("${chat.max-context-characters:6000}") int maxContextCharacters
    ) {
        this.topicRepository = topicRepository;
        this.documentRepository = documentRepository;
        this.curriculumRepository = curriculumRepository;
        this.studyStepRepository = studyStepRepository;
        this.maxContextCharacters = maxContextCharacters;
    }

    @Override
    public StudyChatContext provide(StudySession session, String question) {
        List<Topic> topics = topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(session.getId());
        boolean grounded = session.isGrounded();

        return new StudyChatContext(
                grounded,
                nullToEmpty(session.getSubject()),
                nullToEmpty(session.getExamScope()),
                outlineOf(topics),
                grounded ? materialExcerpt(session, topics, question) : "",
                currentStepTitle(session)
        );
    }

    /**
     * 학습 주제 요약.
     *
     * <p>제목만으로는 답변이 겉돌고, 요약과 핵심 개념까지 넣으면 자료가 없어도 그 주제
     * 안에서 답할 수 있다. 자료 기반 세션에서도 "지금 무엇을 공부하는 중인지"의 지도가 된다.
     */
    private List<String> outlineOf(List<Topic> topics) {
        List<String> outline = new ArrayList<>();
        for (Topic topic : topics.stream().limit(MAX_TOPICS).toList()) {
            StringBuilder line = new StringBuilder(topic.getTitle());
            if (topic.getSummary() != null && !topic.getSummary().isBlank()) {
                line.append(" - ").append(topic.getSummary());
            }
            List<String> keyPoints = topic.getKeyPoints();
            if (keyPoints != null && !keyPoints.isEmpty()) {
                line.append(" (핵심: ").append(String.join(", ", keyPoints)).append(")");
            }
            outline.add(line.toString());
        }
        return outline;
    }

    /** 지금 진행 중인 단계. 답변이 "지금 보고 있는 것"에 맞춰지게 한다. */
    private String currentStepTitle(StudySession session) {
        Integer order = session.getCurrentStepOrder();
        if (order == null) {
            return "";
        }
        return curriculumRepository.findByStudySessionId(session.getId())
                .map(curriculum -> studyStepRepository
                        .findAllByCurriculumIdOrderByStepOrderAsc(curriculum.getId()).stream()
                        .filter(step -> step.getStepOrder() == order)
                        .map(StudyStep::getTitle)
                        .findFirst()
                        .orElse(""))
                .orElse("");
    }

    /**
     * 질문과 관련된 강의자료 구간을 뽑는다.
     *
     * <p>키워드가 한 번도 걸리지 않으면 <b>빈 문자열을 돌려준다.</b> 퀴즈 쪽은 앞부분이라도
     * 담지만(그 Topic 이 그 문서에서 나왔다는 근거가 있다), 여기서는 사용자가 무엇이든
     * 물을 수 있어 그 근거가 없다. 관계없는 문단을 근거랍시고 붙이면 그럴듯한 오답이 나온다.
     */
    private String materialExcerpt(StudySession session, List<Topic> topics, String question) {
        List<Document> documents = documentRepository
                .findAllByStudySessionIdAndStatusOrderByCreatedAtAsc(session.getId(), DocumentStatus.PARSED);
        if (documents.isEmpty()) {
            return "";
        }

        Set<String> keywords = keywordsOf(question, topics);
        if (keywords.isEmpty()) {
            return "";
        }

        StringBuilder excerpt = new StringBuilder();
        for (Document document : documents) {
            if (excerpt.length() >= maxContextCharacters) {
                break;
            }
            String text = document.getExtractedText();
            if (text == null || text.isBlank()) {
                continue;
            }
            String selected = selectRelevantText(text, keywords, maxContextCharacters - excerpt.length());
            if (!selected.isBlank()) {
                if (!excerpt.isEmpty()) {
                    excerpt.append("\n\n");
                }
                excerpt.append(selected);
            }
        }
        return excerpt.toString();
    }

    /**
     * 질문의 단어와 학습 주제의 용어를 합쳐 키워드로 삼는다.
     *
     * <p>질문 단어만 쓰면 "이거 왜 이래?" 같은 짧은 질문에서 아무것도 걸리지 않는다.
     * 주제 용어를 함께 넣으면 최소한 지금 공부 중인 범위의 문단은 잡힌다.
     */
    private Set<String> keywordsOf(String question, List<Topic> topics) {
        Set<String> keywords = new LinkedHashSet<>();
        for (String word : question.split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= MIN_KEYWORD_LENGTH) {
                keywords.add(word.toLowerCase(Locale.ROOT));
            }
        }
        for (Topic topic : topics) {
            addKeyword(keywords, topic.getTitle());
            if (topic.getKeyPoints() != null) {
                topic.getKeyPoints().forEach(point -> addKeyword(keywords, point));
            }
        }
        return keywords;
    }

    private void addKeyword(Set<String> keywords, String value) {
        if (value != null && value.length() >= MIN_KEYWORD_LENGTH) {
            keywords.add(value.toLowerCase(Locale.ROOT));
        }
    }

    /** 키워드가 걸린 문단과 그 이웃을 문서 순서 그대로 담는다. */
    private String selectRelevantText(String text, Set<String> keywords, int budget) {
        String[] paragraphs = text.split("\\R{2,}");
        boolean[] picked = new boolean[paragraphs.length];

        for (int i = 0; i < paragraphs.length; i++) {
            String lower = paragraphs[i].toLowerCase(Locale.ROOT);
            if (keywords.stream().anyMatch(lower::contains)) {
                int from = Math.max(0, i - NEIGHBOR_PARAGRAPHS);
                int to = Math.min(paragraphs.length - 1, i + NEIGHBOR_PARAGRAPHS);
                for (int j = from; j <= to; j++) {
                    picked[j] = true;
                }
            }
        }

        StringBuilder selected = new StringBuilder();
        for (int i = 0; i < paragraphs.length; i++) {
            if (!picked[i] || paragraphs[i].isBlank()) {
                continue;
            }
            if (selected.length() + paragraphs[i].length() > budget) {
                break;
            }
            if (!selected.isEmpty()) {
                selected.append("\n\n");
            }
            selected.append(paragraphs[i].strip());
        }
        return selected.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

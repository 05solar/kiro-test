package com.naeil.study.curriculum.planner;

import com.naeil.study.curriculum.entity.PriorityReason;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 계획을 세우는 데 필요한 Topic 정보만 담은 값.
 *
 * <p>Planner가 JPA 엔티티에 묶이지 않게 한다. 덕분에 Planner는 DB 없이 단위 테스트할 수 있고,
 * 이후 동적 재조정에서도 같은 입력 형태를 쓸 수 있다.
 */
public record PlanningTopic(
        UUID topicId,
        String title,
        TopicImportance importance,
        int estimatedMinutes,
        boolean professorEmphasisMatched,
        boolean pastExamMatched,
        boolean weakAreaMatched,
        boolean mustStudyMatched,
        int topicOrder
) {

    public static PlanningTopic from(Topic topic) {
        return new PlanningTopic(
                topic.getId(),
                topic.getTitle(),
                topic.getImportance(),
                topic.getEstimatedStudyMinutes(),
                topic.isProfessorEmphasisMatched(),
                topic.isPastExamMatched(),
                topic.isWeakAreaMatched(),
                topic.isMustStudyMatched(),
                topic.getTopicOrder());
    }

    /** 화면에 보여줄 선정 이유. 계산에 쓰지 않는다. */
    public List<PriorityReason> priorityReasons() {
        List<PriorityReason> reasons = new ArrayList<>();
        if (mustStudyMatched) {
            reasons.add(PriorityReason.MUST_STUDY);
        }
        if (importance == TopicImportance.VERY_HIGH || importance == TopicImportance.HIGH) {
            reasons.add(PriorityReason.CORE_TOPIC);
        }
        if (professorEmphasisMatched) {
            reasons.add(PriorityReason.PROFESSOR_EMPHASIS);
        }
        if (pastExamMatched) {
            reasons.add(PriorityReason.PAST_EXAM);
        }
        if (weakAreaMatched) {
            reasons.add(PriorityReason.WEAK_AREA);
        }
        return List.copyOf(reasons);
    }
}

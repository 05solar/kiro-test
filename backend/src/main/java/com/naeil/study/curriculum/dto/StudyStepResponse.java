package com.naeil.study.curriculum.dto;

import com.naeil.study.curriculum.entity.PriorityReason;
import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.entity.StudyStepStatus;
import com.naeil.study.curriculum.entity.StudyStepType;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 학습 단계 하나의 응답.
 *
 * <p>시간 값 세 개를 함께 내려준다.
 * <pre>
 * originalEstimatedMinutes  원래 필요하다고 본 시간
 * allocatedMinutes          이번 계획에서 배정한 시간
 * actualStudyMinutes        실제로 쓴 시간
 * </pre>
 * "원래 50분짜리인데 40분만 배정했고 실제로 52분 걸렸다"를 화면에서 보여줄 수 있어야
 * 사용자가 자신의 진행 상태를 판단할 수 있다.
 *
 * <p>진행 정보를 함께 담기 때문에 다른 기기에서 세션 코드만으로 접속해도
 * 이 응답 하나로 학습 화면을 복구할 수 있다.
 *
 * @param topicId    {@code REVIEW} 단계에는 없다
 * @param importance {@code REVIEW} 단계에는 없다
 * @param actualStudyMinutes 완료 전에는 없다
 * @param startedAt  시작 전에는 없다
 * @param completedAt 완료 전에는 없다
 */
public record StudyStepResponse(
        UUID id,
        int order,
        StudyStepType type,
        UUID topicId,
        String title,
        TopicImportance importance,
        int originalEstimatedMinutes,
        int allocatedMinutes,
        Integer actualStudyMinutes,
        boolean mandatory,
        List<PriorityReason> priorityReasons,
        StudyStepStatus status,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {

    public static StudyStepResponse from(StudyStep step) {
        Topic topic = step.getTopic();
        return new StudyStepResponse(
                step.getId(),
                step.getStepOrder(),
                step.getType(),
                topic == null ? null : topic.getId(),
                step.getTitle(),
                topic == null ? null : topic.getImportance(),
                step.getOriginalEstimatedMinutes(),
                step.getAllocatedMinutes(),
                step.getActualStudyMinutes(),
                step.isMandatory(),
                step.getPriorityReasons(),
                step.getStatus(),
                step.getStartedAt(),
                step.getCompletedAt()
        );
    }
}

package com.naeil.study.common.exception;

import org.springframework.http.HttpStatus;

/**
 * API 에러 응답에 사용하는 공통 에러 코드.
 *
 * <p>클라이언트는 {@code code} 문자열로 분기하고, {@code message}는 사용자에게 그대로 노출한다.
 * 세션 존재 여부를 추측할 수 없도록 메시지는 구체적인 내부 정보를 담지 않는다.
 */
public enum ErrorCode {

    INVALID_SESSION_CODE(HttpStatus.BAD_REQUEST, "올바르지 않은 세션 코드입니다."),
    INVALID_EXAM_TIME(HttpStatus.BAD_REQUEST, "시험 시간은 현재 시간보다 이후여야 합니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "유효한 학습 세션을 찾을 수 없습니다."),
    SESSION_CODE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "세션 코드를 생성하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),

    EMPTY_FILE(HttpStatus.BAD_REQUEST, "빈 파일은 업로드할 수 없습니다."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "PDF, DOCX, TXT 파일만 업로드할 수 있습니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "파일 하나당 최대 20MB까지 업로드할 수 있습니다."),
    FILE_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "강의자료는 최대 10개까지 업로드할 수 있습니다."),
    SESSION_STORAGE_EXCEEDED(HttpStatus.BAD_REQUEST, "강의자료 전체 용량은 100MB를 넘을 수 없습니다."),
    EXAM_INFO_REQUIRED(HttpStatus.BAD_REQUEST, "시험 정보를 먼저 입력해 주세요."),
    TOPICS_REQUIRED(HttpStatus.BAD_REQUEST, "강의자료 분석을 먼저 완료해 주세요."),
    SESSION_NOT_READY(HttpStatus.BAD_REQUEST, "강의자료 분석이 끝난 뒤에 학습 계획을 만들 수 있습니다."),
    NO_STUDY_TIME_AVAILABLE(HttpStatus.BAD_REQUEST, "남은 학습 시간이 없습니다. 시험 정보를 다시 확인해 주세요."),
    CURRICULUM_NOT_FOUND(HttpStatus.NOT_FOUND, "학습 계획을 찾을 수 없습니다."),
    CURRICULUM_GENERATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "현재 남은 시간으로 학습 계획을 생성할 수 없습니다."),
    STUDY_STEP_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 학습 단계를 찾을 수 없습니다."),
    STUDY_STEP_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 완료한 학습 단계입니다."),
    STUDY_STEP_NOT_STARTED(HttpStatus.CONFLICT, "아직 시작하지 않은 학습 단계입니다."),
    INVALID_STUDY_STEP_ORDER(HttpStatus.CONFLICT, "앞선 학습 단계를 먼저 진행해 주세요."),
    ANOTHER_STEP_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 학습 단계가 있습니다. 먼저 완료해 주세요."),
    EXAM_ALREADY_STARTED(HttpStatus.CONFLICT, "시험 시간이 지나 새로운 학습을 시작할 수 없습니다."),
    NO_PARSED_DOCUMENT(HttpStatus.BAD_REQUEST, "분석할 수 있는 강의자료가 없습니다. 자료를 올리고 내용을 먼저 읽어 주세요."),
    ANALYSIS_ALREADY_RUNNING(HttpStatus.CONFLICT, "이미 강의자료를 분석하고 있습니다. 잠시 후 다시 확인해 주세요."),
    ANALYSIS_FAILED(HttpStatus.BAD_GATEWAY, "자료 분석에 실패했습니다. 다시 시도해 주세요."),
    TOPIC_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 학습 주제를 찾을 수 없습니다."),
    QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 퀴즈를 찾을 수 없습니다."),
    QUIZ_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "퀴즈 생성에 실패했습니다. 다시 시도해 주세요."),
    TOPIC_STUDY_NOT_COMPLETED(HttpStatus.CONFLICT, "해당 주제의 학습을 먼저 완료해 주세요."),
    INVALID_QUIZ_OPTION(HttpStatus.BAD_REQUEST, "보기 번호가 올바르지 않습니다."),
    NO_QUIZ_SOURCE_CONTEXT(HttpStatus.BAD_REQUEST, "퀴즈를 만들 강의자료 내용을 찾을 수 없습니다."),
    QUIZ_NOT_COMPLETED(HttpStatus.CONFLICT, "아직 풀지 않은 퀴즈가 있습니다. 모든 문제를 푼 뒤 다시 시도해 주세요."),
    WRONG_ANSWER_SUMMARY_NOT_FOUND(HttpStatus.NOT_FOUND, "오답 복습 요약을 찾을 수 없습니다."),
    WRONG_ANSWER_SUMMARY_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "복습 요약 생성에 실패했습니다. 다시 시도해 주세요."),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 강의자료를 찾을 수 없습니다."),
    DOCUMENT_ALREADY_PARSING(HttpStatus.CONFLICT, "이미 문서를 읽는 중입니다. 잠시 후 다시 확인해 주세요."),
    DOCUMENT_PARSE_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "문서 내용을 읽는 중 오류가 발생했습니다."),
    NO_EXTRACTABLE_TEXT(HttpStatus.UNPROCESSABLE_ENTITY, "문서에서 학습에 사용할 텍스트를 추출할 수 없습니다."),
    STORED_FILE_NOT_FOUND(HttpStatus.UNPROCESSABLE_ENTITY, "저장된 파일을 찾을 수 없습니다. 파일을 다시 업로드해 주세요."),
    FILE_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장 중 오류가 발생했습니다."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}

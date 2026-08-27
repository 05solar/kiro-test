package com.naeil.study.session.service;

import com.naeil.study.session.entity.SessionCodePolicy;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 8자리 세션 코드 생성기.
 *
 * <p>세션 코드는 곧 학습 공간의 접근 키이므로 반드시 {@link SecureRandom}으로 생성한다.
 * 중복 검사는 DB를 아는 {@link SessionService}가 담당하고,
 * 이 클래스는 규칙에 맞는 코드 한 개를 만드는 책임만 가진다.
 */
@Component
public class SessionCodeGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 허용 문자만 사용하는 8자리 코드를 생성한다.
     *
     * @return 예: {@code 7K2M9QXF}
     */
    public String generate() {
        char[] buffer = new char[SessionCodePolicy.LENGTH];
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = SessionCodePolicy.ALPHABET.charAt(secureRandom.nextInt(SessionCodePolicy.ALPHABET.length()));
        }
        return new String(buffer);
    }
}

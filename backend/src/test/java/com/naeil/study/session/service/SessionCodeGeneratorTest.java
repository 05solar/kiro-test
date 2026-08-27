package com.naeil.study.session.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.session.entity.SessionCodePolicy;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

@DisplayName("SessionCodeGenerator - 8자리 세션 코드 생성기")
class SessionCodeGeneratorTest {

    private final SessionCodeGenerator generator = new SessionCodeGenerator();

    @RepeatedTest(50)
    @DisplayName("생성한 코드는 항상 8자리다")
    void generatesCodeWithLengthOfEight() {
        String code = generator.generate();

        assertThat(code).hasSize(8);
    }

    @RepeatedTest(50)
    @DisplayName("생성한 코드는 허용된 문자만 사용한다")
    void generatesCodeWithAllowedCharactersOnly() {
        String code = generator.generate();

        assertThat(code).matches(SessionCodePolicy.PATTERN);
        assertThat(code.chars()).allMatch(c -> SessionCodePolicy.ALPHABET.indexOf(c) >= 0);
    }

    @Test
    @DisplayName("혼동하기 쉬운 문자(0, O, 1, I)는 사용하지 않는다")
    void neverGeneratesConfusingCharacters() {
        for (int i = 0; i < 2000; i++) {
            assertThat(generator.generate()).doesNotContain("0", "O", "1", "I");
        }
    }

    @Test
    @DisplayName("생성한 코드는 사실상 중복되지 않는다")
    void generatesPracticallyUniqueCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            codes.add(generator.generate());
        }

        assertThat(codes).hasSize(5000);
    }

    @Test
    @DisplayName("생성한 코드는 형식 검증을 통과한다")
    void generatedCodeIsValidBySessionCodePolicy() {
        for (int i = 0; i < 1000; i++) {
            assertThat(SessionCodePolicy.isValid(generator.generate())).isTrue();
        }
    }
}

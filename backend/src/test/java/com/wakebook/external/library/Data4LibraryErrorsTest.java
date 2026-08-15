package com.wakebook.external.library;

import com.wakebook.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 정보나루는 오류를 HTTP 상태가 아니라 본문 errCode로 알려 준다.
 * 그래서 일일 한도 초과가 "결과 없음"처럼 보이던 문제가 있었다.
 */
class Data4LibraryErrorsTest {

    @Test
    void errCode가_없으면_통과한다() {
        assertThatCode(() -> Data4LibraryErrors.check(null, null)).doesNotThrowAnyException();
        assertThatCode(() -> Data4LibraryErrors.check(" ", null)).doesNotThrowAnyException();
    }

    @Test
    void 일일_호출_한도를_넘기면_BOOK_003으로_알린다() {
        assertThatThrownBy(() -> Data4LibraryErrors.check(
            "outOflimit", "1일 500건 이상 요청 시 IP 등록이 필요합니다."
        ))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("호출 한도를 모두 사용했습니다");
    }

    @Test
    void 그_밖의_오류는_BOOK_002로_원문을_함께_알린다() {
        assertThatThrownBy(() -> Data4LibraryErrors.check("periodErr", "검색조건(기간) 오류입니다."))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("검색조건(기간) 오류입니다.");
    }
}

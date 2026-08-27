package com.wakebook.external.library;

import com.wakebook.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * 정보나루는 오류를 HTTP 상태가 아니라 본문의 errCode로 알려 준다. 그래서 그동안 한도 초과가
 * "빈 결과"로 보였다. 특히 일일 호출 한도(IP 미등록 시 500건)를 넘기면 outOflimit이 오는데,
 * 이걸 그냥 빈 목록으로 처리하면 데이터가 없는 것처럼 보인다.
 */
public final class Data4LibraryErrors {

    private static final Logger log = LoggerFactory.getLogger(Data4LibraryErrors.class);

    private static final String OUT_OF_LIMIT = "outOflimit";

    private Data4LibraryErrors() {
    }

    public static void check(String errCode, String error) {
        if (errCode == null || errCode.isBlank()) {
            return;
        }
        log.warn("정보나루 API 오류 (errCode={}, error={})", errCode, error);
        if (OUT_OF_LIMIT.equalsIgnoreCase(errCode)) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "BOOK_003",
                "오늘 도서관 정보나루 API 호출 한도를 모두 사용했습니다. 내일 다시 시도해 주세요."
            );
        }
        throw new ApiException(
            HttpStatus.BAD_GATEWAY, "BOOK_002",
            error == null || error.isBlank() ? "도서관 정보나루 조회에 실패했습니다." : error
        );
    }
}

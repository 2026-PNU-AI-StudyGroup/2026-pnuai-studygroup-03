package com.wakebook.book.domain;

/** 잠자는 도서 후보군을 무엇으로 산출했는지. 정밀도가 다르므로 화면에서도 구분해 보여 준다. */
public enum HiddenBookSource {

    /** 사서가 올린 정보나루 "장서 대출목록" CSV의 실제 대출건수 기준. 가장 정확하다. */
    CSV_UPLOAD,

    /** 정보나루 API(장서 목록 - 대출 순위) 차집합. 대출건수는 알 수 없고 "순위 밖"이라는 간접 신호다. */
    LIBRARY_API,

    /** 서비스 체험용으로 미리 넣어 둔 데모 도서관 데이터. */
    DEMO_SEED
}

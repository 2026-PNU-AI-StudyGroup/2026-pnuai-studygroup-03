package com.wakebook.external.library;

import java.util.Optional;

/**
 * "잠자는 도서" 후보군 보강(표지·소개 문구) 전용 조회 인터페이스. 도서관 소장 여부와 대출건수는
 * CSV({@link ItemUsageRecord})에서 이미 확보돼 있으므로 이 인터페이스는 표지 이미지와 소개 문구를
 * 얻기 위한 목적으로만 쓰인다. 정보나루 authKey를 쓰는 {@link BookDetailProvider}와는 별도
 * 인터페이스라, 도서 검색/상세/추천 등 다른 기능은 영향받지 않는다.
 */
public interface HiddenBookDetailProvider {

    Optional<BookDetail> fetch(String isbn);
}

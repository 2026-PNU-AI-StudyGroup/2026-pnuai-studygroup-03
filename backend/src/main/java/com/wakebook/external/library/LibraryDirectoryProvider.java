package com.wakebook.external.library;

import java.util.List;

/** 정보나루에 등록된 도서관 목록(libSrch). 이용자가 도서관 코드를 직접 입력하지 않아도 되게 한다. */
public interface LibraryDirectoryProvider {

    List<LibraryDirectoryItem> findByRegion(String region);
}

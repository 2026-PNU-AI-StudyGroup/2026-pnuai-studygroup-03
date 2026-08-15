package com.wakebook.book.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.repository.HiddenBookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 도서관별 후보군 교체. 산출은 오래 걸리지만 저장은 짧은 트랜잭션 하나로 끝내야
 * 실패했을 때 기존 후보군이 지워진 채로 남지 않는다. 같은 빈 안에서 호출하면
 * 프록시를 타지 않아 트랜잭션이 걸리지 않으므로 별도 빈으로 둔다.
 */
@Service
public class HiddenBookPoolWriter {

    private final HiddenBookRepository hiddenBookRepository;

    public HiddenBookPoolWriter(HiddenBookRepository hiddenBookRepository) {
        this.hiddenBookRepository = hiddenBookRepository;
    }

    @Transactional
    public void replace(String libraryCode, List<HiddenBook> collected) {
        hiddenBookRepository.deleteAllByLibraryCode(libraryCode);
        hiddenBookRepository.saveAll(collected);
    }
}

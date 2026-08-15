package com.wakebook.external.library;

import com.wakebook.external.library.dto.LibSrchByBookApiResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class Data4LibraryHoldingProvider implements LibraryHoldingProvider {

    private final Data4LibraryHoldingLookup lookup;

    public Data4LibraryHoldingProvider(Data4LibraryHoldingLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public List<LibraryHolding> findHoldings(String isbn, String region) {
        return lookup.findLibraries(isbn, region).stream()
            .map(lib -> toHolding(isbn, lib))
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * libSrchByBook만으로는 청구기호·대출가능여부를 알 수 없어 도서관마다 bookExist, itemSrch를
     * 추가로 호출한다. 한 도서관 조회가 실패해도 나머지 결과는 보여줄 수 있도록 개별 실패는 건너뛴다.
     * 호출 수가 도서관 수에 비례하므로 각 조회는 캐싱된다(Data4LibraryHoldingLookup).
     */
    private LibraryHolding toHolding(String isbn, LibSrchByBookApiResponse.Lib lib) {
        Boolean available = lookup.findAvailability(isbn, lib.libCode());
        if (available == null) {
            return null;
        }
        return new LibraryHolding(lib.libName(), lookup.findCallNumber(isbn, lib.libCode()), available);
    }
}

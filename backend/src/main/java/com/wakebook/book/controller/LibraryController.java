package com.wakebook.book.controller;

import com.wakebook.book.dto.HiddenBookJobResponse;
import com.wakebook.book.dto.LibraryDirectoryResponse;
import com.wakebook.book.dto.HiddenBookResponse;
import com.wakebook.book.dto.LibrarySummaryResponse;
import com.wakebook.common.PageResponse;
import com.wakebook.book.service.HiddenBookService;
import com.wakebook.book.service.LibraryCollectService;
import com.wakebook.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/libraries")
public class LibraryController {

    private final HiddenBookService hiddenBookService;
    private final LibraryCollectService libraryCollectService;

    public LibraryController(HiddenBookService hiddenBookService, LibraryCollectService libraryCollectService) {
        this.hiddenBookService = hiddenBookService;
        this.libraryCollectService = libraryCollectService;
    }

    /** 잠자는 도서 후보군이 이미 등록돼 바로 추천이 되는 도서관 목록. */
    @GetMapping
    public ApiResponse<List<LibrarySummaryResponse>> getLibraries() {
        return ApiResponse.success(hiddenBookService.getLibraries());
    }

    /** 정보나루에 등록된 지역별 전체 도서관 목록. 후보군을 새로 만들 도서관을 고를 때 쓴다. */
    @GetMapping("/directory")
    public ApiResponse<List<LibraryDirectoryResponse>> getLibraryDirectory(@RequestParam String region) {
        List<LibraryDirectoryResponse> libraries = libraryCollectService.findLibraries(region).stream()
            .map(LibraryDirectoryResponse::from)
            .toList();
        return ApiResponse.success(libraries);
    }

    /**
     * 도서관의 잠자는 도서 목록. AI도 정보나루도 거치지 않고 저장된 후보군을 그대로 보여 준다.
     */
    @GetMapping("/{libraryCode}/hidden-books")
    public ApiResponse<PageResponse<HiddenBookResponse>> getHiddenBooks(
        @PathVariable String libraryCode,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "12") int size
    ) {
        return ApiResponse.success(hiddenBookService.getHiddenBooks(libraryCode, page, size));
    }

    /**
     * 사서의 CSV 업로드 없이 정보나루 API로 후보군을 산출한다. 수 분이 걸리는 작업이라
     * 접수만 하고 202를 돌려주며, 진행 상태는 작업 조회 API로 확인한다.
     */
    @PostMapping("/{libraryCode}/hidden-books")
    public ResponseEntity<ApiResponse<HiddenBookJobResponse>> collectHiddenBooks(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String libraryCode
    ) {
        HiddenBookJobResponse response =
            HiddenBookJobResponse.from(libraryCollectService.requestCollect(jwt.getSubject(), libraryCode));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ApiResponse.success("후보군 산출을 시작했습니다.", response));
    }
}

package com.wakebook.book.controller;

import com.wakebook.book.dto.HiddenBookJobResponse;
import com.wakebook.book.service.HiddenBookUploadService;
import com.wakebook.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/librarian/hidden-books")
public class LibrarianHiddenBookController {

    private final HiddenBookUploadService hiddenBookUploadService;

    public LibrarianHiddenBookController(HiddenBookUploadService hiddenBookUploadService) {
        this.hiddenBookUploadService = hiddenBookUploadService;
    }

    /**
     * 대상 도서관은 인증된 사서의 소속으로 정해진다. libraryCode를 함께 보내면 소속과 일치하는지만 검증한다.
     * 산출은 수 분이 걸리므로 접수만 하고 202를 돌려주며, 진행 상태는 GET /hidden-book-jobs/{jobId}로 확인한다.
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<HiddenBookJobResponse>> upload(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) String libraryCode,
        @RequestPart MultipartFile file
    ) {
        HiddenBookJobResponse response = HiddenBookJobResponse.from(
            hiddenBookUploadService.upload(jwt.getSubject(), libraryCode, file)
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ApiResponse.success("장서 데이터를 접수했습니다. 후보군을 만드는 중입니다.", response));
    }
}

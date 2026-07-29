package com.wakebook.book.controller;

import com.wakebook.book.dto.HiddenBookUploadResponse;
import com.wakebook.book.service.HiddenBookUploadService;
import com.wakebook.common.response.ApiResponse;
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

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ApiResponse<HiddenBookUploadResponse> upload(
        @RequestParam String libraryCode,
        @RequestParam String libraryName,
        @RequestPart MultipartFile file
    ) {
        HiddenBookUploadResponse response = hiddenBookUploadService.upload(libraryCode, libraryName, file);
        return ApiResponse.success("장서/대출 데이터를 반영했습니다.", response);
    }
}

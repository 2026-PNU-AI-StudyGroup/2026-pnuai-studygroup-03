package com.wakebook.book.controller;

import com.wakebook.book.domain.HiddenBookJob;
import com.wakebook.book.domain.HiddenBookJobStatus;
import com.wakebook.book.domain.HiddenBookSource;
import com.wakebook.book.dto.HiddenBookJobResponse;
import com.wakebook.book.service.HiddenBookUploadService;
import com.wakebook.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LibrarianHiddenBookControllerTest {

    @Test
    void 업로드_요청은_인증된_jwt_subject를_전달하고_작업을_202로_접수한다() {
        HiddenBookUploadService hiddenBookUploadService = mock(HiddenBookUploadService.class);
        LibrarianHiddenBookController controller =
                new LibrarianHiddenBookController(hiddenBookUploadService);
        MockMultipartFile file = new MockMultipartFile(
                "file", "library.csv", "text/csv", "dummy".getBytes(StandardCharsets.UTF_8)
        );
        HiddenBookJob job =
                new HiddenBookJob("121018", "부산광역시 금정도서관", HiddenBookSource.CSV_UPLOAD, 12L);
        when(hiddenBookUploadService.upload(eq("12"), eq("121018"), any())).thenReturn(job);
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject("12")
                .build();

        ResponseEntity<ApiResponse<HiddenBookJobResponse>> response =
                controller.upload(jwt, "121018", file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().libraryCode()).isEqualTo("121018");
        assertThat(response.getBody().data().status()).isEqualTo(HiddenBookJobStatus.PENDING);
    }
}

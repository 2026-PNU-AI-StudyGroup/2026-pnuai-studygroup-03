package com.wakebook.book.controller;

import com.wakebook.book.domain.HiddenBookJob;
import com.wakebook.book.domain.HiddenBookSource;
import com.wakebook.book.dto.HiddenBookJobResponse;
import com.wakebook.book.service.HiddenBookJobService;
import com.wakebook.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HiddenBookJobControllerTest {

    @Test
    void 작업_조회는_인증된_jwt_subject를_권한_검증에_전달한다() {
        HiddenBookJobService jobService = mock(HiddenBookJobService.class);
        HiddenBookJobController controller = new HiddenBookJobController(jobService);
        HiddenBookJob job = new HiddenBookJob(
                "121020", null, HiddenBookSource.LIBRARY_API, 7L,
                LocalDateTime.of(2026, 8, 19, 12, 0)
        );
        Jwt jwt = Jwt.withTokenValue("access-token").header("alg", "HS256").subject("7").build();
        when(jobService.getForRequester(5L, "7")).thenReturn(job);

        ApiResponse<HiddenBookJobResponse> response = controller.getJob(jwt, 5L);

        assertThat(response.data().libraryCode()).isEqualTo("121020");
        verify(jobService).getForRequester(eq(5L), eq("7"));
    }
}

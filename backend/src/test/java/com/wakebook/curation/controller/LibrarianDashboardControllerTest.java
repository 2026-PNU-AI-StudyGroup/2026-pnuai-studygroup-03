package com.wakebook.curation.controller;

import com.wakebook.common.response.ApiResponse;
import com.wakebook.curation.dto.CurationSummaryResponse;
import com.wakebook.curation.dto.LibrarianDashboardResponse;
import com.wakebook.curation.service.LibrarianDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LibrarianDashboardControllerTest {

    @Test
    void 대시보드_요청은_인증된_jwt_subject로_서비스_결과를_그대로_반환한다() {
        LibrarianDashboardService librarianDashboardService = mock(LibrarianDashboardService.class);
        LibrarianDashboardController controller = new LibrarianDashboardController(librarianDashboardService);
        LibrarianDashboardResponse dashboard = new LibrarianDashboardResponse(
                128, 12, List.of("청년", "불안", "관계"),
                List.of(new CurationSummaryResponse(5L, "괜찮지 않아도 괜찮은 우리에게", 5, true))
        );
        when(librarianDashboardService.getDashboard("12")).thenReturn(dashboard);
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject("12")
                .build();

        ApiResponse<LibrarianDashboardResponse> response = controller.getDashboard(jwt);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(dashboard);
    }
}

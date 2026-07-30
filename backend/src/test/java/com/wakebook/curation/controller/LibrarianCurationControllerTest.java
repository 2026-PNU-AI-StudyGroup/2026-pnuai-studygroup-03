package com.wakebook.curation.controller;

import com.wakebook.common.PageResponse;
import com.wakebook.common.response.ApiResponse;
import com.wakebook.curation.dto.CurationBookRequest;
import com.wakebook.curation.dto.CurationBookResponse;
import com.wakebook.curation.dto.CurationGenerateRequest;
import com.wakebook.curation.dto.CurationGenerateResponse;
import com.wakebook.curation.dto.CurationResponse;
import com.wakebook.curation.dto.CurationSummaryResponse;
import com.wakebook.curation.dto.SaveCurationRequest;
import com.wakebook.curation.service.CurationGenerationService;
import com.wakebook.curation.service.CurationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LibrarianCurationControllerTest {

    @Test
    void 초안_생성_요청은_인증된_jwt_subject로_서비스_결과를_반환한다() {
        CurationGenerationService curationGenerationService = mock(CurationGenerationService.class);
        CurationService curationService = mock(CurationService.class);
        LibrarianCurationController controller =
                new LibrarianCurationController(curationGenerationService, curationService);
        CurationGenerateRequest request = new CurationGenerateRequest(
                "청년의 불안", "20대", "따뜻한", "인문", 5, List.of("취업"), "전시 큐레이션"
        );
        CurationGenerateResponse draft = new CurationGenerateResponse(
                "괜찮지 않아도 괜찮은 우리에게", "설명", List.of("#청년"),
                List.of(new CurationGenerateResponse.BookItem("9788960867450", "관계에도 연습이 필요합니다", "이유"))
        );
        when(curationGenerationService.generate("12", request)).thenReturn(draft);
        Jwt jwt = jwt("12");

        ResponseEntity<ApiResponse<CurationGenerateResponse>> response = controller.generate(jwt, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(draft);
    }

    @Test
    void 저장_요청은_생성된_큐레이션을_201로_반환한다() {
        CurationGenerationService curationGenerationService = mock(CurationGenerationService.class);
        CurationService curationService = mock(CurationService.class);
        LibrarianCurationController controller =
                new LibrarianCurationController(curationGenerationService, curationService);
        SaveCurationRequest request = new SaveCurationRequest(
                "괜찮지 않아도 괜찮은 우리에게", "설명", true,
                List.of(new CurationBookRequest("9788960867450", 1, "코멘트"))
        );
        CurationResponse created = new CurationResponse(
                5L, "괜찮지 않아도 괜찮은 우리에게", "설명", true, 1,
                List.of(new CurationBookResponse(9L, "9788960867450", "관계에도 연습이 필요합니다", "cover", 1, "코멘트")),
                LocalDateTime.now()
        );
        when(curationService.create("12", request)).thenReturn(created);

        ResponseEntity<ApiResponse<CurationResponse>> response = controller.create(jwt("12"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(created);
    }

    @Test
    void 목록_조회_요청은_페이지_파라미터를_그대로_전달한다() {
        CurationGenerationService curationGenerationService = mock(CurationGenerationService.class);
        CurationService curationService = mock(CurationService.class);
        LibrarianCurationController controller =
                new LibrarianCurationController(curationGenerationService, curationService);
        PageResponse<CurationSummaryResponse> page = PageResponse.of(
                List.of(new CurationSummaryResponse(5L, "제목", 1, true)), 1, 10, 1
        );
        when(curationService.getCurations("12", 1, 10)).thenReturn(page);

        ApiResponse<PageResponse<CurationSummaryResponse>> response =
                controller.getCurations(jwt("12"), 1, 10);

        assertThat(response.data()).isEqualTo(page);
        verify(curationService).getCurations("12", 1, 10);
    }

    @Test
    void 삭제_요청은_인증된_jwt_subject로_서비스에_위임한다() {
        CurationGenerationService curationGenerationService = mock(CurationGenerationService.class);
        CurationService curationService = mock(CurationService.class);
        LibrarianCurationController controller =
                new LibrarianCurationController(curationGenerationService, curationService);

        ApiResponse<Void> response = controller.delete(jwt("12"), 5L);

        assertThat(response.success()).isTrue();
        verify(curationService).delete("12", 5L);
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject(subject)
                .build();
    }
}

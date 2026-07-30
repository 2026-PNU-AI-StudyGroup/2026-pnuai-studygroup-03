package com.wakebook.book.controller;

import com.wakebook.book.dto.HiddenBookUploadResponse;
import com.wakebook.book.service.HiddenBookUploadService;
import com.wakebook.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LibrarianHiddenBookControllerTest {

    private HiddenBookUploadService hiddenBookUploadService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        hiddenBookUploadService = mock(HiddenBookUploadService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LibrarianHiddenBookController(hiddenBookUploadService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 업로드_요청은_서비스_결과를_그대로_반환한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "library.csv", "text/csv", "dummy".getBytes(StandardCharsets.UTF_8)
        );
        when(hiddenBookUploadService.upload(eq("121018"), eq("부산광역시 금정도서관"), any()))
                .thenReturn(new HiddenBookUploadResponse("121018", "부산광역시 금정도서관", 10, 3));

        mockMvc.perform(multipart("/librarian/hidden-books/upload")
                        .file(file)
                        .param("libraryCode", "121018")
                        .param("libraryName", "부산광역시 금정도서관"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.libraryCode").value("121018"))
                .andExpect(jsonPath("$.data.savedCount").value(3));
    }
}

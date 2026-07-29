package com.wakebook.book.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.dto.HiddenBookUploadResponse;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.book.support.HiddenBookCsvParser;
import com.wakebook.book.support.HiddenBookProperties;
import com.wakebook.common.ApiException;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.FakeBookDetailProvider;
import com.wakebook.external.openai.FakeOpenAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HiddenBookUploadServiceTest {

    private static final String CSV_HEADER =
        "번호,도서명,저자,출판사,발행년도,ISBN,세트 ISBN,부가기호,권,주제분류번호,도서권수,대출건수,등록일자\n";

    @Mock
    private HiddenBookRepository hiddenBookRepository;

    private FakeBookDetailProvider fakeBookDetailProvider;
    private FakeOpenAiClient fakeOpenAiClient;
    private HiddenBookUploadService hiddenBookUploadService;

    @BeforeEach
    void setUp() {
        fakeBookDetailProvider = new FakeBookDetailProvider();
        fakeOpenAiClient = new FakeOpenAiClient();
        HiddenBookProperties hiddenBookProperties = new HiddenBookProperties(2, 30);

        hiddenBookUploadService = new HiddenBookUploadService(
            new HiddenBookCsvParser(),
            fakeBookDetailProvider,
            fakeOpenAiClient,
            hiddenBookRepository,
            hiddenBookProperties,
            new ObjectMapper()
        );
    }

    @Test
    void 대출건수가_낮고_품질검증을_통과한_후보만_해당_도서관에_저장한다() {
        String csv = CSV_HEADER
            + "\"1\",\"관계에도 연습이 필요합니다\",\"박상미\",\"빌리버튼\",\"2018\",\"9788960867450\",\"\",\"\",\"\",\"\",\"1\",\"1\",\"2026-06-24\"\n"
            + "\"2\",\"대출많은책\",\"아무개\",\"출판사\",\"2020\",\"9999999999999\",\"\",\"\",\"\",\"\",\"1\",\"100\",\"2026-06-24\"\n";
        MockMultipartFile file = new MockMultipartFile(
            "file", "부산광역시 금정도서관 장서 대출목록 (2026년 06월).csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );
        fakeBookDetailProvider.setDetail(new BookDetail(
            "9788960867450", "관계에도 연습이 필요합니다", "박상미", "빌리버튼", 2018,
            "https://example.com/cover2.jpg",
            "나를 지키면서 타인과 건강하게 연결되는 연습을 다루는 책으로, 관계에 지친 이들에게 실질적인 도움을 준다."
        ));
        fakeOpenAiClient.setResponse("{\"reason\": \"추천 이유\", \"keywords\": [\"인간관계\", \"심리\"]}");

        HiddenBookUploadResponse response = hiddenBookUploadService.upload("121018", "부산광역시 금정도서관", file);

        verify(hiddenBookRepository).deleteAllByLibraryCode("121018");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HiddenBook>> captor = ArgumentCaptor.forClass(List.class);
        verify(hiddenBookRepository).saveAll(captor.capture());

        List<HiddenBook> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getIsbn()).isEqualTo("9788960867450");
        assertThat(saved.get(0).getLibraryCode()).isEqualTo("121018");
        assertThat(saved.get(0).getLibraryName()).isEqualTo("부산광역시 금정도서관");
        assertThat(saved.get(0).getReason()).isEqualTo("추천 이유");
        assertThat(response.libraryCode()).isEqualTo("121018");
        assertThat(response.totalRows()).isEqualTo(2);
        assertThat(response.savedCount()).isEqualTo(1);
    }

    @Test
    void libraryCode가_없으면_VALIDATION_001_예외() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.csv", "text/csv", CSV_HEADER.getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> hiddenBookUploadService.upload(" ", "부산광역시 금정도서관", file))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void 파일이_비어있으면_VALIDATION_001_예외() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> hiddenBookUploadService.upload("121018", "부산광역시 금정도서관", emptyFile))
            .isInstanceOf(ApiException.class);
    }
}

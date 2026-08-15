package com.wakebook.book.service;

import com.wakebook.book.domain.BookEnrichmentCache;
import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.dto.HiddenBookUploadResponse;
import com.wakebook.book.repository.BookEnrichmentCacheRepository;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.book.support.HiddenBookCsvParser;
import com.wakebook.book.support.HiddenBookProperties;
import com.wakebook.common.ApiException;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.FakeHiddenBookDetailProvider;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HiddenBookUploadServiceTest {

    private static final String CSV_HEADER =
        "번호,도서명,저자,출판사,발행년도,ISBN,세트 ISBN,부가기호,권,주제분류번호,도서권수,대출건수,등록일자\n";

    @Mock
    private HiddenBookRepository hiddenBookRepository;

    @Mock
    private BookEnrichmentCacheRepository bookEnrichmentCacheRepository;

    private FakeHiddenBookDetailProvider fakeBookDetailProvider;
    private FakeOpenAiClient fakeOpenAiClient;

    @BeforeEach
    void setUp() {
        fakeBookDetailProvider = new FakeHiddenBookDetailProvider();
        fakeOpenAiClient = new FakeOpenAiClient();
        // 대부분의 테스트가 "캐시 없음"을 전제로 하므로 기본값으로 깔아두고, 필요한 테스트에서만 덮어쓴다.
        lenient().when(bookEnrichmentCacheRepository.findAllByIsbnIn(any())).thenReturn(List.of());
    }

    private HiddenBookUploadService newService(int maxLoanCount, int candidatePoolSize) {
        return new HiddenBookUploadService(
            new HiddenBookCsvParser(),
            fakeBookDetailProvider,
            fakeOpenAiClient,
            hiddenBookRepository,
            bookEnrichmentCacheRepository,
            new HiddenBookProperties(maxLoanCount, candidatePoolSize),
            new ObjectMapper()
        );
    }

    private MockMultipartFile csvFile(String csv) {
        return new MockMultipartFile(
            "file", "부산광역시 금정도서관 장서 대출목록 (2026년 06월).csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Test
    void 대출건수가_낮고_품질검증을_통과한_후보만_해당_도서관에_저장한다() {
        HiddenBookUploadService service = newService(2, 30);
        String csv = CSV_HEADER
            + "\"1\",\"관계에도 연습이 필요합니다\",\"박상미\",\"빌리버튼\",\"2018\",\"9788960867450\",\"\",\"\",\"\",\"3\",\"1\",\"1\",\"2026-06-24\"\n"
            + "\"2\",\"대출많은책\",\"아무개\",\"출판사\",\"2020\",\"9999999999999\",\"\",\"\",\"\",\"3\",\"1\",\"100\",\"2026-06-24\"\n";
        fakeBookDetailProvider.setDetail(new BookDetail(
            "9788960867450", "관계에도 연습이 필요합니다", "박상미", "빌리버튼", 2018,
            "https://example.com/cover2.jpg",
            "나를 지키면서 타인과 건강하게 연결되는 연습을 다루는 책으로, 관계에 지친 이들에게 실질적인 도움을 준다."
        ));
        fakeOpenAiClient.setResponse(
            "{\"results\": [{\"isbn\": \"9788960867450\", \"reason\": \"추천 이유\", \"keywords\": [\"인간관계\", \"심리\"]}]}"
        );

        HiddenBookUploadResponse response = service.upload("121018", "부산광역시 금정도서관", csvFile(csv));

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
        // 대출 100건짜리는 loanCount 필터에서 걸러져서 표지소개 조회조차 안 됨(후보 1건만 fetch)
        assertThat(fakeBookDetailProvider.callCount()).isEqualTo(1);
        assertThat(fakeOpenAiClient.callCount()).isEqualTo(1);
    }

    @Test
    void libraryCode가_없으면_VALIDATION_001_예외() {
        HiddenBookUploadService service = newService(2, 30);
        MockMultipartFile file = csvFile(CSV_HEADER);

        assertThatThrownBy(() -> service.upload(" ", "부산광역시 금정도서관", file))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void 파일이_비어있으면_VALIDATION_001_예외() {
        HiddenBookUploadService service = newService(2, 30);
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> service.upload("121018", "부산광역시 금정도서관", emptyFile))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void 출판사가_비어있는_후보는_표지소개_조회를_호출하지_않는다() {
        HiddenBookUploadService service = newService(2, 30);
        String csv = CSV_HEADER
            + "\"1\",\"출판사없는책\",\"아무개\",\"\",\"\",\"9780000000001\",\"\",\"\",\"\",\"\",\"1\",\"0\",\"2026-06-24\"\n";

        HiddenBookUploadResponse response = service.upload("121018", "부산광역시 금정도서관", csvFile(csv));

        assertThat(fakeBookDetailProvider.callCount()).isZero();
        assertThat(fakeOpenAiClient.callCount()).isZero();
        assertThat(response.savedCount()).isZero();
    }

    @Test
    void 주제분류번호가_없는_후보는_표지소개_조회를_호출하지_않는다() {
        HiddenBookUploadService service = newService(2, 30);
        String csv = CSV_HEADER
            + "\"1\",\"분류없는책\",\"저자\",\"출판사\",\"2020\",\"9780000000077\",\"\",\"\",\"\",\"\",\"1\",\"0\",\"2026-06-24\"\n";

        HiddenBookUploadResponse response = service.upload("121018", "부산광역시 금정도서관", csvFile(csv));

        assertThat(fakeBookDetailProvider.callCount()).isZero();
        assertThat(fakeOpenAiClient.callCount()).isZero();
        assertThat(response.savedCount()).isZero();
    }

    @Test
    void 같은_ISBN이_여러_행이면_한_번만_후보로_취급한다() {
        HiddenBookUploadService service = newService(2, 30);
        String csv = CSV_HEADER
            + "\"1\",\"두권보유책\",\"저자\",\"출판사\",\"2020\",\"9780000000088\",\"\",\"\",\"\",\"3\",\"1\",\"0\",\"2026-06-24\"\n"
            + "\"2\",\"두권보유책\",\"저자\",\"출판사\",\"2020\",\"9780000000088\",\"\",\"\",\"\",\"3\",\"1\",\"0\",\"2026-06-24\"\n";
        fakeBookDetailProvider.setDetailForIsbn("9780000000088", new BookDetail(
            "9780000000088", "두권보유책", "저자", "출판사", 2020,
            "https://example.com/cover.jpg", "같은 책을 두 권 보유한 경우를 다루는 소개 문장을 30자 이상으로 작성합니다."
        ));
        fakeOpenAiClient.setResponse(
            "{\"results\": [{\"isbn\": \"9780000000088\", \"reason\": \"추천 이유\", \"keywords\": [\"키워드\"]}]}"
        );

        HiddenBookUploadResponse response = service.upload("121018", "부산광역시 금정도서관", csvFile(csv));

        assertThat(fakeBookDetailProvider.callCount()).isEqualTo(1);
        assertThat(response.savedCount()).isEqualTo(1);
    }

    @Test
    void 대출건수가_같아도_주제분류번호_대분류별로_후보를_고르게_섞어서_뽑는다() {
        HiddenBookUploadService service = newService(2, 2);
        // CSV 순서상 문학(8) 5권이 먼저 나오고 철학(1) 1권이 맨 뒤에 오지만,
        // 카테고리별 목표치(quota) 배분 로직이 있으면 철학 책도 상위 2권 안에 포함돼야 한다.
        StringBuilder csv = new StringBuilder(CSV_HEADER);
        for (int i = 1; i <= 5; i++) {
            csv.append("\"%d\",\"문학책%d\",\"저자%d\",\"출판사%d\",\"2020\",\"978000000000%d\",\"\",\"\",\"\",\"8\",\"1\",\"0\",\"2026-06-24\"\n"
                .formatted(i, i, i, i, i));
        }
        csv.append("\"6\",\"철학책1\",\"저자철\",\"출판사철\",\"2020\",\"9780000000009\",\"\",\"\",\"\",\"1\",\"1\",\"0\",\"2026-06-24\"\n");

        for (int i = 1; i <= 5; i++) {
            String isbn = "978000000000%d".formatted(i);
            fakeBookDetailProvider.setDetailForIsbn(isbn, new BookDetail(
                isbn, "문학책" + i, "저자" + i, "출판사" + i, 2020,
                "https://example.com/cover.jpg", "문학책 소개 문장을 30자 이상으로 충분히 길게 작성한 설명입니다."
            ));
        }
        fakeBookDetailProvider.setDetailForIsbn("9780000000009", new BookDetail(
            "9780000000009", "철학책1", "저자철", "출판사철", 2020,
            "https://example.com/cover.jpg", "철학책 소개 문장을 30자 이상으로 충분히 길게 작성한 설명입니다."
        ));
        fakeOpenAiClient.setResponse("""
            {"results": [
                {"isbn": "9780000000001", "reason": "문학 추천", "keywords": ["문학"]},
                {"isbn": "9780000000009", "reason": "철학 추천", "keywords": ["철학"]}
            ]}
            """);

        service.upload("121018", "부산광역시 금정도서관", csvFile(csv.toString()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HiddenBook>> captor = ArgumentCaptor.forClass(List.class);
        verify(hiddenBookRepository).saveAll(captor.capture());
        List<String> savedIsbns = captor.getValue().stream().map(HiddenBook::getIsbn).toList();

        assertThat(savedIsbns).hasSize(2);
        assertThat(savedIsbns).contains("9780000000009"); // 철학책이 후보군 상위 2권 안에 반드시 포함돼야 함
    }

    @Test
    void 한_카테고리의_품질검증_실패가_다른_카테고리_몫을_침범하지_않는다() {
        HiddenBookUploadService service = newService(2, 4);
        // 철학(1)은 3권 중 1권(101)이 품질검증에서 탈락하도록, 문학(8)은 정확히 quota만큼인
        // 2권만 두고 전부 통과하도록 구성. poolSize=4, 카테고리 2개 → quota는 카테고리당 2권.
        // 예전 라운드로빈 방식이면 철학의 탈락분을 문학이 대신 채웠겠지만, 지금은 카테고리별로
        // quota를 독립적으로 채우므로 철학은 나머지 후보(102, 103)로 스스로 quota를 채우고,
        // 문학은 그와 무관하게 정확히 자기 quota(2권)만 채워야 한다.
        String csv = CSV_HEADER
            + "\"1\",\"철학책1\",\"저자1\",\"출판사1\",\"2020\",\"9780000000101\",\"\",\"\",\"\",\"1\",\"1\",\"0\",\"2026-06-24\"\n"
            + "\"2\",\"철학책2\",\"저자2\",\"출판사2\",\"2020\",\"9780000000102\",\"\",\"\",\"\",\"1\",\"1\",\"0\",\"2026-06-24\"\n"
            + "\"3\",\"철학책3\",\"저자3\",\"출판사3\",\"2020\",\"9780000000103\",\"\",\"\",\"\",\"1\",\"1\",\"0\",\"2026-06-24\"\n"
            + "\"4\",\"문학책1\",\"저자4\",\"출판사4\",\"2020\",\"9780000000201\",\"\",\"\",\"\",\"8\",\"1\",\"0\",\"2026-06-24\"\n"
            + "\"5\",\"문학책2\",\"저자5\",\"출판사5\",\"2020\",\"9780000000202\",\"\",\"\",\"\",\"8\",\"1\",\"0\",\"2026-06-24\"\n";

        fakeBookDetailProvider.setDetailForIsbn("9780000000101", new BookDetail(
            "9780000000101", "철학책1", "저자1", "출판사1", 2020, "https://example.com/cover.jpg", "짧음"
        ));
        fakeBookDetailProvider.setDetailForIsbn("9780000000102", new BookDetail(
            "9780000000102", "철학책2", "저자2", "출판사2", 2020,
            "https://example.com/cover.jpg", "철학책 소개 문장을 30자 이상으로 충분히 길게 작성한 설명입니다."
        ));
        fakeBookDetailProvider.setDetailForIsbn("9780000000103", new BookDetail(
            "9780000000103", "철학책3", "저자3", "출판사3", 2020,
            "https://example.com/cover.jpg", "철학책 소개 문장을 30자 이상으로 충분히 길게 작성한 설명입니다."
        ));
        fakeBookDetailProvider.setDetailForIsbn("9780000000201", new BookDetail(
            "9780000000201", "문학책1", "저자4", "출판사4", 2020,
            "https://example.com/cover.jpg", "문학책 소개 문장을 30자 이상으로 충분히 길게 작성한 설명입니다."
        ));
        fakeBookDetailProvider.setDetailForIsbn("9780000000202", new BookDetail(
            "9780000000202", "문학책2", "저자5", "출판사5", 2020,
            "https://example.com/cover.jpg", "문학책 소개 문장을 30자 이상으로 충분히 길게 작성한 설명입니다."
        ));
        fakeOpenAiClient.setResponse("{\"results\": []}");

        HiddenBookUploadResponse response = service.upload("121018", "부산광역시 금정도서관", csvFile(csv));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HiddenBook>> captor = ArgumentCaptor.forClass(List.class);
        verify(hiddenBookRepository).saveAll(captor.capture());
        List<String> savedIsbns = captor.getValue().stream().map(HiddenBook::getIsbn).toList();

        // 문학은 항상 정확히 quota만큼(2권), 철학은 101이 탈락해도 102·103으로 스스로 quota(2권)를
        // 채운다 — 대출건수 동률인 후보 순서를 매 업로드마다 무작위로 섞으므로(HiddenBookUploadService
        // 클래스 주석 참고), 철학책1(101)이 실제로 시도되는지 여부는 실행마다 달라질 수 있지만
        // 최종 저장 결과는 아래처럼 항상 동일하다.
        assertThat(response.savedCount()).isEqualTo(4);
        assertThat(savedIsbns).containsExactlyInAnyOrder(
            "9780000000102", "9780000000103", "9780000000201", "9780000000202"
        );
        // 문학은 시도가 정확히 2번(항상 성공)이고, 철학은 101이 시도되느냐에 따라 2~3번이라
        // 전체 호출 수는 4~5번 사이여야 한다(무한정 더 훑지 않는다는 것도 함께 검증).
        assertThat(fakeBookDetailProvider.callCount()).isBetween(4, 5);
    }

    @Test
    void 이미_캐시된_ISBN은_상세조회와_AI_호출을_건너뛰고_캐시를_재사용한다() {
        HiddenBookUploadService service = newService(2, 30);
        String csv = CSV_HEADER
            + "\"1\",\"이미본책\",\"저자\",\"출판사\",\"2020\",\"9780000000099\",\"\",\"\",\"\",\"1\",\"1\",\"0\",\"2026-06-24\"\n";
        when(bookEnrichmentCacheRepository.findAllByIsbnIn(any())).thenReturn(List.of(
            new BookEnrichmentCache(
                "9780000000099", "캐시된 제목", "캐시저자", "https://example.com/cached.jpg",
                88, "캐시된 추천 이유", List.of("캐시키워드")
            )
        ));

        service.upload("121018", "부산광역시 금정도서관", csvFile(csv));

        assertThat(fakeBookDetailProvider.callCount()).isZero();
        assertThat(fakeOpenAiClient.callCount()).isZero();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HiddenBook>> captor = ArgumentCaptor.forClass(List.class);
        verify(hiddenBookRepository).saveAll(captor.capture());
        HiddenBook saved = captor.getValue().get(0);
        assertThat(saved.getTitle()).isEqualTo("캐시된 제목");
        assertThat(saved.getQualityScore()).isEqualTo(88);
        assertThat(saved.getReason()).isEqualTo("캐시된 추천 이유");
    }

    @Test
    void 후보가_배치_크기를_넘으면_OpenAI를_여러_번_나눠_호출한다() {
        int candidateCount = 16; // AI_BATCH_SIZE(15)를 넘겨서 2번 호출되는지 확인
        HiddenBookUploadService service = newService(2, candidateCount);

        StringBuilder csv = new StringBuilder(CSV_HEADER);
        List<String> isbns = IntStream.rangeClosed(1, candidateCount)
            .mapToObj(i -> "97800000%05d".formatted(i))
            .toList();
        for (int i = 0; i < candidateCount; i++) {
            String isbn = isbns.get(i);
            csv.append("\"%d\",\"책%d\",\"저자%d\",\"출판사%d\",\"2020\",\"%s\",\"\",\"\",\"\",\"5\",\"1\",\"0\",\"2026-06-24\"\n"
                .formatted(i + 1, i, i, i, isbn));
            fakeBookDetailProvider.setDetailForIsbn(isbn, new BookDetail(
                isbn, "책" + i, "저자" + i, "출판사" + i, 2020,
                "https://example.com/cover.jpg", "책 소개 문장을 30자 이상으로 충분히 길게 작성한 설명입니다."
            ));
        }
        String batchResults = isbns.stream()
            .map(isbn -> "{\"isbn\": \"%s\", \"reason\": \"이유\", \"keywords\": [\"키워드\"]}".formatted(isbn))
            .collect(Collectors.joining(",", "{\"results\": [", "]}"));
        fakeOpenAiClient.setResponse(batchResults);

        HiddenBookUploadResponse response = service.upload("121018", "부산광역시 금정도서관", csvFile(csv.toString()));

        assertThat(response.savedCount()).isEqualTo(candidateCount);
        assertThat(fakeOpenAiClient.callCount()).isEqualTo(2); // 15 + 1 => 2번 배치 호출
    }
}

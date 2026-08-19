package com.wakebook.curation.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.domain.HiddenBookSource;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.ApiException;
import com.wakebook.curation.dto.CurationGenerateRequest;
import com.wakebook.curation.dto.CurationGenerateResponse;
import com.wakebook.external.openai.FakeOpenAiClient;
import com.wakebook.user.domain.User;
import com.wakebook.user.domain.UserRole;
import com.wakebook.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurationGenerationServiceTest {

    private static final String LIBRARY_CODE = "121018";

    @Mock
    private UserRepository userRepository;

    @Mock
    private HiddenBookRepository hiddenBookRepository;

    private FakeOpenAiClient fakeOpenAiClient;
    private CurationGenerationService curationGenerationService;

    @BeforeEach
    void setUp() {
        fakeOpenAiClient = new FakeOpenAiClient();
        curationGenerationService = new CurationGenerationService(
                userRepository, hiddenBookRepository, fakeOpenAiClient, new ObjectMapper()
        );
    }

    @Test
    void AI가_고른_도서를_후보군_데이터와_매칭해_반환한다() {
        User librarian = librarian(LIBRARY_CODE);
        when(userRepository.findById(12L)).thenReturn(Optional.of(librarian));
        when(hiddenBookRepository.findAllByLibraryCode(LIBRARY_CODE)).thenReturn(List.of(
                hiddenBook("9788960867450", "관계에도 연습이 필요합니다", List.of("인간관계")),
                hiddenBook("9999999999999", "다른책", List.of("취업"))
        ));
        fakeOpenAiClient.setResponse("""
            {"title": "괜찮지 않아도 괜찮은 우리에게", "description": "설명", "hashtags": ["#청년", "#불안"],
             "books": [{"isbn": "9788960867450", "reason": "관계 불안을 다정하게 다룹니다."}]}
            """);

        CurationGenerateResponse response = curationGenerationService.generate(
                "12",
                new CurationGenerateRequest("청년의 불안", "20대", "따뜻한", "인문", 5, List.of("취업"), "전시 큐레이션")
        );

        assertThat(response.title()).isEqualTo("괜찮지 않아도 괜찮은 우리에게");
        assertThat(response.hashtags()).containsExactly("#청년", "#불안");
        assertThat(response.books()).singleElement().satisfies(book -> {
            assertThat(book.isbn()).isEqualTo("9788960867450");
            assertThat(book.title()).isEqualTo("관계에도 연습이 필요합니다");
            assertThat(book.reason()).isEqualTo("관계 불안을 다정하게 다룹니다.");
        });
    }

    @Test
    void 후보_소개는_description을_우선하고_reason을_대체값으로_사용한다() {
        User librarian = librarian(LIBRARY_CODE);
        when(userRepository.findById(12L)).thenReturn(Optional.of(librarian));
        when(hiddenBookRepository.findAllByLibraryCode(LIBRARY_CODE)).thenReturn(List.of(
                promptBook("9788960867450", null, "정보나루에서 수집한 소개글"),
                promptBook("9999999999999", "기존 추천 이유", null)
        ));
        fakeOpenAiClient.setResponse("""
            {"title": "큐레이션", "description": "설명", "hashtags": ["#주제"],
             "books": [{"isbn": "9788960867450", "reason": "선정 이유"}]}
            """);

        curationGenerationService.generate(
                "12", new CurationGenerateRequest("청년의 불안", null, null, null, 2, null, null)
        );

        assertThat(fakeOpenAiClient.lastUserPrompt())
                .contains("소개: 정보나루에서 수집한 소개글")
                .contains("소개: 기존 추천 이유")
                .doesNotContain("소개: null");
    }

    @Test
    void 제외_키워드와_겹치는_후보는_추천에서_빠진다() {
        User librarian = librarian(LIBRARY_CODE);
        when(userRepository.findById(12L)).thenReturn(Optional.of(librarian));
        when(hiddenBookRepository.findAllByLibraryCode(LIBRARY_CODE)).thenReturn(List.of(
                hiddenBook("9999999999999", "취업책", List.of("취업"))
        ));

        assertThatThrownBy(() -> curationGenerationService.generate(
                "12",
                new CurationGenerateRequest("청년의 불안", null, null, null, null, List.of("취업"), null)
        )).isInstanceOf(ApiException.class);
    }

    @Test
    void 후보군이_없으면_BOOK_001_예외() {
        User librarian = librarian(LIBRARY_CODE);
        when(userRepository.findById(12L)).thenReturn(Optional.of(librarian));
        when(hiddenBookRepository.findAllByLibraryCode(LIBRARY_CODE)).thenReturn(List.of());

        assertThatThrownBy(() -> curationGenerationService.generate(
                "12", new CurationGenerateRequest("청년의 불안", null, null, null, null, null, null)
        )).isInstanceOf(ApiException.class);
    }

    @Test
    void topic이_비어있으면_VALIDATION_001_예외() {
        assertThatThrownBy(() -> curationGenerationService.generate(
                "12", new CurationGenerateRequest(" ", null, null, null, null, null, null)
        )).isInstanceOf(ApiException.class);
    }

    private static User librarian(String libraryCode) {
        User user = new User(
                UserRole.LIBRARIAN, "김도서", "librarian@wakebook.kr", "encoded-password",
                "책지기", "부산광역시 금정도서관", libraryCode, "자료운영팀"
        );
        ReflectionTestUtils.setField(user, "id", 12L);
        return user;
    }

    private static HiddenBook hiddenBook(String isbn, String title, List<String> keywords) {
        return new HiddenBook(
                isbn, LIBRARY_CODE, "부산광역시 금정도서관", title, "저자",
                "cover", 1, 80, "이유", keywords
        );
    }

    private static HiddenBook promptBook(String isbn, String reason, String description) {
        return new HiddenBook(
                isbn, LIBRARY_CODE, "부산광역시 금정도서관", "후보 도서", "저자",
                "cover", 1, 80, reason, List.of("인간관계"),
                HiddenBookSource.LIBRARY_API, "100.1", "자료실", description
        );
    }
}

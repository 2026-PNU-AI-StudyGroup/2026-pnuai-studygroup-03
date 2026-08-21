package com.wakebook.external.library;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("도서 상세 공급자 우선순위")
class FallbackBookDetailProviderTest {
    private static final String ISBN = "9788996991342";
    private FakeBookDetailProvider kakao;
    private FakeBookDetailProvider aladin;
    private FakeBookDetailProvider data4Library;
    private FallbackBookDetailProvider provider;

    @BeforeEach
    void setUp() {
        kakao = new FakeBookDetailProvider();
        aladin = new FakeBookDetailProvider();
        data4Library = new FakeBookDetailProvider();
        kakao.makeEmpty(); aladin.makeEmpty(); data4Library.makeEmpty();
        provider = new FallbackBookDetailProvider(kakao, aladin, data4Library);
    }

    @Test
    void 카카오가_완전한_상세를_주면_다른_API를_부르지_않는다() {
        kakao.setDetail(detail("카카오 소개글입니다."));

        Optional<BookDetail> result = provider.fetch(ISBN);

        assertThat(result).isPresent();
        assertThat(result.get().description()).isEqualTo("카카오 소개글입니다.");
        assertThat(aladin.lastIsbn()).isNull();
        assertThat(data4Library.lastIsbn()).isNull();
    }

    @Test
    void 카카오가_비면_알라딘으로_넘어가고_정보나루는_부르지_않는다() {
        aladin.setDetail(detail("알라딘 소개글입니다."));

        Optional<BookDetail> result = provider.fetch(ISBN);

        assertThat(result.get().description()).isEqualTo("알라딘 소개글입니다.");
        assertThat(aladin.lastIsbn()).isEqualTo(ISBN);
        assertThat(data4Library.lastIsbn()).isNull();
    }

    @Test
    void 카카오와_알라딘이_모두_부족할_때만_정보나루를_호출한다() {
        kakao.setDetail(detail(null));
        aladin.setDetail(detail(null));
        data4Library.setDetail(detail("정보나루 소개글입니다."));

        Optional<BookDetail> result = provider.fetch(ISBN);

        assertThat(result.get().description()).isEqualTo("정보나루 소개글입니다.");
        assertThat(data4Library.lastIsbn()).isEqualTo(ISBN);
    }

    @Test
    void 셋_다_불완전하면_먼저_받은_카카오_응답을_유지한다() {
        kakao.setDetail(detail(null));
        aladin.setDetail(detail(null));
        data4Library.makeEmpty();

        Optional<BookDetail> result = provider.fetch(ISBN);

        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("미움받을 용기");
        assertThat(result.get().description()).isNull();
    }

    @Test
    void 세_공급자에_모두_없으면_빈_값이다() {
        assertThat(provider.fetch(ISBN)).isEmpty();
    }

    private BookDetail detail(String description) {
        return new BookDetail(ISBN, "미움받을 용기", "기시미 이치로", "인플루엔셜", 2014,
            "https://example.com/cover1.jpg", description);
    }
}

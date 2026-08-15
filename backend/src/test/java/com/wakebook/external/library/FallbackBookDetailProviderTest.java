package com.wakebook.external.library;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("도서 상세 폴백")
class FallbackBookDetailProviderTest {

    private static final String ISBN = "9788996991342";

    private FakeBookDetailProvider aladin;
    private FakeBookDetailProvider data4Library;
    private FakeBookDetailProvider kakao;
    private FallbackBookDetailProvider provider;

    @BeforeEach
    void setUp() {
        aladin = new FakeBookDetailProvider();
        data4Library = new FakeBookDetailProvider();
        kakao = new FakeBookDetailProvider();
        kakao.makeEmpty();
        provider = new FallbackBookDetailProvider(aladin, data4Library, kakao);
    }

    @Test
    void 알라딘이_소개글까지_주면_정보나루를_부르지_않는다() {
        aladin.setDetail(detail("알라딘 소개글입니다."));

        Optional<BookDetail> result = provider.fetch(ISBN);

        assertThat(result).isPresent();
        assertThat(result.get().description()).isEqualTo("알라딘 소개글입니다.");
        assertThat(data4Library.lastIsbn()).isNull();
    }

    @Test
    void 알라딘에_없으면_정보나루로_넘어간다() {
        aladin.makeEmpty();
        data4Library.setDetail(detail("정보나루 소개글입니다."));

        Optional<BookDetail> result = provider.fetch(ISBN);

        assertThat(result).isPresent();
        assertThat(result.get().description()).isEqualTo("정보나루 소개글입니다.");
        assertThat(data4Library.lastIsbn()).isEqualTo(ISBN);
    }

    /** 후보군 품질 기준이 소개글 유무라, 소개글 없는 알라딘 응답으로 후보를 잃으면 안 된다. */
    @Test
    void 알라딘에_소개글이_없으면_정보나루로_넘어간다() {
        aladin.setDetail(detail(null));
        data4Library.setDetail(detail("정보나루 소개글입니다."));

        Optional<BookDetail> result = provider.fetch(ISBN);

        assertThat(result.get().description()).isEqualTo("정보나루 소개글입니다.");
        assertThat(data4Library.lastIsbn()).isEqualTo(ISBN);
    }

    /** 소개글이 어디에도 없더라도 제목·저자는 살아 있어야 도서 상세 화면이 뜬다. */
    @Test
    void 정보나루에도_없으면_소개글_없는_알라딘_응답이라도_돌려준다() {
        aladin.setDetail(detail(null));
        data4Library.makeEmpty();

        Optional<BookDetail> result = provider.fetch(ISBN);

        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("미움받을 용기");
        assertThat(result.get().description()).isNull();
    }

    @Test
    void 둘_다_없으면_빈_값이다() {
        aladin.makeEmpty();
        data4Library.makeEmpty();

        assertThat(provider.fetch(ISBN)).isEmpty();
    }

    /** 정보나루 한도를 다 쓴 날에도 카카오가 후보를 건져야 한다. */
    @Test
    void 알라딘과_정보나루가_모두_비면_카카오로_넘어간다() {
        aladin.makeEmpty();
        data4Library.makeEmpty();
        kakao.setDetail(detail("카카오 소개글입니다."));

        Optional<BookDetail> result = provider.fetch(ISBN);

        assertThat(result).isPresent();
        assertThat(result.get().description()).isEqualTo("카카오 소개글입니다.");
        assertThat(kakao.lastIsbn()).isEqualTo(ISBN);
    }

    @Test
    void 앞선_두_곳에_소개글이_없으면_카카오_소개글을_쓴다() {
        aladin.setDetail(detail(null));
        data4Library.setDetail(detail(null));
        kakao.setDetail(detail("카카오 소개글입니다."));

        Optional<BookDetail> result = provider.fetch(ISBN);

        assertThat(result.get().description()).isEqualTo("카카오 소개글입니다.");
    }

    /** 카카오는 마지막 수단이라, 앞에서 이미 해결되면 호출하지 않아야 한다. */
    @Test
    void 알라딘이_해결하면_카카오를_부르지_않는다() {
        aladin.setDetail(detail("알라딘 소개글입니다."));

        provider.fetch(ISBN);

        assertThat(kakao.lastIsbn()).isNull();
    }

    /** 셋 다 소개글이 없어도 제목·저자는 살아 있어야 한다. */
    @Test
    void 셋_다_소개글이_없으면_정보나루_응답이라도_돌려준다() {
        aladin.setDetail(detail(null));
        data4Library.setDetail(detail(null));
        kakao.makeEmpty();

        Optional<BookDetail> result = provider.fetch(ISBN);

        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("미움받을 용기");
    }

    private BookDetail detail(String description) {
        return new BookDetail(
            ISBN, "미움받을 용기", "기시미 이치로", "인플루엔셜", 2014,
            "https://example.com/cover1.jpg", description
        );
    }
}

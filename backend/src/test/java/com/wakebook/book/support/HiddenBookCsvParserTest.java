package com.wakebook.book.support;

import com.wakebook.external.library.ItemUsageRecord;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HiddenBookCsvParserTest {

    private final HiddenBookCsvParser parser = new HiddenBookCsvParser();

    @Test
    void 정보나루_장서_대출목록_CSV를_ISBN_저자_대출건수_기준으로_파싱한다() {
        String csv = """
            번호,도서명,저자,출판사,발행년도,ISBN,세트 ISBN,부가기호,권,주제분류번호,도서권수,대출건수,등록일자,
            "1","English idioms in use","Michael McCarthy,Felicity O'Dell","CAMBRIDGE UNI PRESS ELT","2017","9781316629888","","","","","1","0","2026-06-24",
            "2","관계에도 연습이 필요합니다","박상미","빌리버튼","2018","9788960867450","","","","","1","3","2026-06-24",
            """;

        List<ItemUsageRecord> result = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isbn()).isEqualTo("9781316629888");
        assertThat(result.get(0).title()).isEqualTo("English idioms in use");
        assertThat(result.get(0).author()).isEqualTo("Michael McCarthy,Felicity O'Dell");
        assertThat(result.get(0).loanCount()).isZero();
        assertThat(result.get(1).isbn()).isEqualTo("9788960867450");
        assertThat(result.get(1).loanCount()).isEqualTo(3);
    }

    @Test
    void ISBN이_비어있는_행은_건너뛴다() {
        String csv = """
            번호,도서명,저자,출판사,발행년도,ISBN,세트 ISBN,부가기호,권,주제분류번호,도서권수,대출건수,등록일자,
            "1","제목없음","저자","출판사","2020","","","","","","1","0","2026-06-24",
            """;

        List<ItemUsageRecord> result = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result).isEmpty();
    }
}

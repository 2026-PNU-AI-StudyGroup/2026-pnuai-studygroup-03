package com.wakebook.external.library;

/**
 * @param className 정보나루 KDC 분류명(예: "문학 > 한국문학 > 소설"). AI 호출 없이 키워드를 만드는 데 쓴다.
 * @param callNumber 청구기호. 추천 카드에서 "지금 어디로 가면 되는지" 보여 주기 위해 함께 저장한다.
 * @param shelfName 자료실명(예: "어린이자료실").
 */
public record HoldingCatalogItem(
    String isbn,
    String title,
    String author,
    String cover,
    String className,
    String callNumber,
    String shelfName
) {
}

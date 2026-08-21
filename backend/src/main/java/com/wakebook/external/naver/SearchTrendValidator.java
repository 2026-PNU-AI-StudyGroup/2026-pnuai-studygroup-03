package com.wakebook.external.naver;

public interface SearchTrendValidator {
    SearchTrendValidation validate(String sourceKeyword, String displayTopic);
}

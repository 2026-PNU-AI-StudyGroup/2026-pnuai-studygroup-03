package com.wakebook.external.naver;

import com.wakebook.external.trend.NewsEvidence;
import java.util.List;

public interface NewsEvidenceProvider {
    List<NewsEvidence> search(String keyword, int limit);
}

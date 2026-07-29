package com.wakebook.external.aladin;

import java.util.List;

public interface TableOfContentsProvider {

    List<String> fetch(String isbn);
}

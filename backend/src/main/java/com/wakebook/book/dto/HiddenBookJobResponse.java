package com.wakebook.book.dto;

import com.wakebook.book.domain.HiddenBookJob;
import com.wakebook.book.domain.HiddenBookJobStatus;
import com.wakebook.book.domain.HiddenBookSource;

public record HiddenBookJobResponse(
    Long jobId,
    String libraryCode,
    String libraryName,
    HiddenBookSource source,
    HiddenBookJobStatus status,
    int totalCandidates,
    int processedCount,
    int savedCount,
    String message
) {

    public static HiddenBookJobResponse from(HiddenBookJob job) {
        return new HiddenBookJobResponse(
            job.getId(),
            job.getLibraryCode(),
            job.getLibraryName(),
            job.getSource(),
            job.getStatus(),
            job.getTotalCandidates(),
            job.getProcessedCount(),
            job.getSavedCount(),
            job.getMessage()
        );
    }
}

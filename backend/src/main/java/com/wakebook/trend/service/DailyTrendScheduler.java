package com.wakebook.trend.service;

import com.wakebook.book.repository.HiddenBookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
@ConditionalOnProperty(name = "trend.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class DailyTrendScheduler {
    private static final Logger log = LoggerFactory.getLogger(DailyTrendScheduler.class);
    private final HiddenBookRepository hiddenBookRepository;
    private final TrendRefreshService refreshService;
    public DailyTrendScheduler(HiddenBookRepository hiddenBookRepository, TrendRefreshService refreshService) {
        this.hiddenBookRepository = hiddenBookRepository; this.refreshService = refreshService;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void generateMissingTodayOnStartup() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        requestForLibraries(today, true);
    }
    @Scheduled(cron = "${trend.schedule-cron:0 0 5 * * *}", zone = "Asia/Seoul")
    public void generateDaily() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        requestForLibraries(today, false);
    }

    private void requestForLibraries(LocalDate today, boolean startup) {
        hiddenBookRepository.findDistinctLibraryCodes().forEach(code -> {
            try {
                if (startup) refreshService.requestOnStartup(code, today);
                else refreshService.requestScheduled(code, today);
            } catch (Exception exception) {
                log.error("도서관의 일일 트렌드 생성 요청에 실패했습니다. (libraryCode={})", code, exception);
            }
        });
    }
}

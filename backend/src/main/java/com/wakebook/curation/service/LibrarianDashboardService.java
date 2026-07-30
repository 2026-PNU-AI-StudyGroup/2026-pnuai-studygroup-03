package com.wakebook.curation.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.exception.AuthenticationRequiredException;
import com.wakebook.curation.dto.CurationSummaryResponse;
import com.wakebook.curation.dto.LibrarianDashboardResponse;
import com.wakebook.curation.repository.CurationRepository;
import com.wakebook.user.domain.User;
import com.wakebook.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class LibrarianDashboardService {

    private static final int PLACEHOLDER_EXHIBITION_LOAN_RATE = 0;
    private static final int POPULAR_KEYWORD_LIMIT = 3;

    private final UserRepository userRepository;
    private final HiddenBookRepository hiddenBookRepository;
    private final CurationRepository curationRepository;

    public LibrarianDashboardService(
            UserRepository userRepository,
            HiddenBookRepository hiddenBookRepository,
            CurationRepository curationRepository
    ) {
        this.userRepository = userRepository;
        this.hiddenBookRepository = hiddenBookRepository;
        this.curationRepository = curationRepository;
    }

    public LibrarianDashboardResponse getDashboard(String authenticatedUserId) {
        Long userId = parseUserId(authenticatedUserId);
        User user = userRepository.findById(userId)
                .orElseThrow(AuthenticationRequiredException::new);

        List<HiddenBook> pool = user.getLibraryCode() == null
                ? List.of()
                : hiddenBookRepository.findAllByLibraryCode(user.getLibraryCode());

        List<String> popularKeywords = pool.stream()
                .flatMap(book -> book.getKeywords().stream())
                .collect(Collectors.groupingBy(keyword -> keyword, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(POPULAR_KEYWORD_LIMIT)
                .map(Map.Entry::getKey)
                .toList();

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = monthStart.plusMonths(1);
        long monthlyCurationCount = curationRepository.countByUser_IdAndCreatedAtBetween(
                userId, monthStart, monthEnd
        );

        List<CurationSummaryResponse> recentCurations = curationRepository
                .findTop5ByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(CurationSummaryResponse::from)
                .toList();

        return new LibrarianDashboardResponse(
                pool.size(),
                monthlyCurationCount,
                PLACEHOLDER_EXHIBITION_LOAN_RATE,
                popularKeywords,
                recentCurations
        );
    }

    private static Long parseUserId(String authenticatedUserId) {
        try {
            long userId = Long.parseLong(authenticatedUserId);
            if (userId <= 0) {
                throw new AuthenticationRequiredException();
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new AuthenticationRequiredException();
        }
    }
}

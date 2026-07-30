package com.wakebook.curation.controller;

import com.wakebook.common.response.ApiResponse;
import com.wakebook.curation.dto.LibrarianDashboardResponse;
import com.wakebook.curation.service.LibrarianDashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/librarian/dashboard")
public class LibrarianDashboardController {

    private final LibrarianDashboardService librarianDashboardService;

    public LibrarianDashboardController(LibrarianDashboardService librarianDashboardService) {
        this.librarianDashboardService = librarianDashboardService;
    }

    @GetMapping
    public ApiResponse<LibrarianDashboardResponse> getDashboard(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(librarianDashboardService.getDashboard(jwt.getSubject()));
    }
}

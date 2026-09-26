package com.be.dashboard.controller;

import com.be.dashboard.dto.DashboardResponse;
import com.be.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService service;

    @GetMapping("/api/admin/dashboard")
    public DashboardResponse get() { return service.get(); }
}

package com.be.statistics.controller;

import com.be.statistics.dto.*;
import com.be.statistics.dto.StatisticsResponse.*;
import com.be.statistics.service.StatisticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/admin/statistics")
public class StatisticsController {
    private final StatisticsService service;
    @GetMapping("/overview")
    public Overview overview(@Valid @ModelAttribute StatisticsFilter filter) { return service.overview(filter); }
    @GetMapping("/trends")
    public Trends trends(@Valid @ModelAttribute StatisticsFilter filter) { return service.trends(filter); }
    @GetMapping("/courses")
    public Courses courses(@Valid @ModelAttribute StatisticsFilter filter, @Valid @ModelAttribute StatisticsPageRequest page) {
        return service.courses(filter, page);
    }
    @GetMapping("/departments")
    public Departments departments(@Valid @ModelAttribute StatisticsFilter filter, @Valid @ModelAttribute StatisticsPageRequest page) {
        return service.departments(filter, page);
    }
}

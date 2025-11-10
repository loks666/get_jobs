package com.getjobs.application.controller;

import com.getjobs.application.service.LiepinService;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/liepin")
public class LiepinAnalyticsController {

    private final LiepinService liepinService;

    public LiepinAnalyticsController(LiepinService liepinService) {
        this.liepinService = liepinService;
    }

    /**
     * 投递分析统计与图表（支持基础筛选条件）
     */
    @GetMapping("/stats")
    public LiepinService.StatsResponse getStats(
            @RequestParam(value = "statuses", required = false) String statuses,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "experience", required = false) String experience,
            @RequestParam(value = "degree", required = false) String degree,
            @RequestParam(value = "minK", required = false) Double minK,
            @RequestParam(value = "maxK", required = false) Double maxK,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        List<String> statusList = null;
        if (statuses != null && !statuses.trim().isEmpty()) {
            statusList = Arrays.stream(statuses.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return liepinService.getLiepinStats(
                statusList,
                location,
                experience,
                degree,
                minK,
                maxK,
                keyword
        );
    }

    /**
     * 岗位列表（分页 + 筛选）
     */
    @GetMapping("/list")
    public LiepinService.PagedResult list(
            @RequestParam(value = "statuses", required = false) String statuses,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "experience", required = false) String experience,
            @RequestParam(value = "degree", required = false) String degree,
            @RequestParam(value = "minK", required = false) Double minK,
            @RequestParam(value = "maxK", required = false) Double maxK,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size
    ) {
        List<String> statusList = null;
        if (statuses != null && !statuses.trim().isEmpty()) {
            statusList = Arrays.stream(statuses.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return liepinService.listLiepinJobs(
                statusList,
                location,
                experience,
                degree,
                minK,
                maxK,
                keyword,
                page,
                size
        );
    }
}
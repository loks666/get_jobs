package getjobs.controller;

import getjobs.repository.entity.JobEntity;
import getjobs.repository.JobRepository;
import getjobs.modules.boss.service.BossApiMonitorService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobRepository jobRepository;
    private final BossApiMonitorService bossApiMonitorService;

    public JobController(JobRepository jobRepository, BossApiMonitorService bossApiMonitorService) {
        this.jobRepository = jobRepository;
        this.bossApiMonitorService = bossApiMonitorService;
    }

    @GetMapping
    public Page<JobEntity> list(
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return jobRepository.search(platform, keyword, pageable);
    }

}

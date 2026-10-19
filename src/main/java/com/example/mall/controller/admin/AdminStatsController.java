package com.example.mall.controller.admin;

import com.example.mall.common.Result;
import com.example.mall.service.StatsService;
import com.example.mall.vo.StatsOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台数据看板（第17课时）。
 */
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final StatsService statsService;

    /** 看板概览：顶部数字卡片 + 四张图表的数据，一次请求全拿到 */
    @GetMapping("/overview")
    public Result<StatsOverviewVO> overview() {
        return Result.success(statsService.overview());
    }
}

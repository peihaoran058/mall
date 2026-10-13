package com.example.mall.service;

import com.example.mall.vo.StatsOverviewVO;

/**
 * 后台数据看板服务。
 */
public interface StatsService {

    /** 看板总览：数字卡片 + 订单状态分布 + 销量 TOP5 + 近 7 日趋势。 */
    StatsOverviewVO overview();

}

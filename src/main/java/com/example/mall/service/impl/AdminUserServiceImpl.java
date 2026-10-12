package com.example.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mall.common.PageResult;
import com.example.mall.common.ResultCode;
import com.example.mall.dto.UserQueryDTO;
import com.example.mall.entity.Orders;
import com.example.mall.entity.SysUser;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.OrdersMapper;
import com.example.mall.mapper.SysUserMapper;
import com.example.mall.service.AdminUserService;
import com.example.mall.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台用户管理实现。
 *
 * <p><b>课上重点一：模糊查询为什么要一层一层套条件？</b><br>
 * 后台列表的查询条件是"可选的"（用户可能只填了状态、不填关键字）。
 * 用 {@code QueryWrapper} 的 {@code .like(condition, column, value)} 重载，
 * 第一个参数是 boolean —— 条件不成立时这一段 SQL 直接不拼进去，
 * 比手写 {@code if} 拼接字符串安全得多（也天然防了 SQL 注入）。</p>
 *
 * <p><b>课上重点二：这里为什么要用 {@code AND (...)} 包住 or？</b><br>
 * 如果写成 {@code .like(username).or().like(nickname).or().like(phone)}
 * 再和 {@code .eq(status)} 拼起来，SQL 就变成：</p>
 * <pre>
 * where deleted=0 and username like ? or nickname like ? or phone like ? and status = ?
 * </pre>
 * <p>因为 {@code or} 的优先级低于 {@code and}，这个条件会被解析成
 * {@code (A) or (B) or (C and D)} —— <b>筛选状态直接失效</b>，
 * 查询结果莫名其妙变多。正确写法是用 {@code .and(w -> ...)} 把 or 组包起来，
 * 生成 {@code and (username like ? or nickname like ? or phone like ?) and status = ?}。</p>
 * <p>这是 SQL 条件拼接最经典的坑，写后台列表几乎必踩。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final SysUserMapper userMapper;
    private final OrdersMapper ordersMapper;

    @Override
    public PageResult<UserVO> page(UserQueryDTO query) {
        Page<SysUser> page = new Page<>(query.getPageNum(), query.getPageSize());

        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        // 关键字：账号、昵称、手机号 三选一命中即可，用 and(...) 把 or 组包起来
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like("username", kw)
                    .or().like("nickname", kw)
                    .or().like("phone", kw));
        }
        wrapper.eq(query.getStatus() != null, "status", query.getStatus());
        wrapper.orderByDesc("id");

        IPage<SysUser> result = userMapper.selectPage(page, wrapper);

        List<UserVO> records = new ArrayList<>(result.getRecords().size());
        // 一次性把这批用户的订单数查出来，避免 N+1（循环里逐个 selectCount）
        Map<Long, Long> orderCountMap = countOrdersByUser(result.getRecords());
        for (SysUser user : result.getRecords()) {
            UserVO vo = new UserVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setPhone(user.getPhone());
            vo.setEmail(user.getEmail());
            vo.setAvatar(user.getAvatar());
            vo.setStatus(user.getStatus());
            vo.setCreateTime(user.getCreateTime());
            vo.setOrderCount(orderCountMap.getOrDefault(user.getId(), 0L));
            records.add(vo);
        }

        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, Integer status) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXISTS);
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setStatus(status);
        userMapper.updateById(update);
        log.info("后台修改用户状态 | userId={} | status={}", userId, status);
    }

    /**
     * 批量统计每个用户的订单数：一条 GROUP BY SQL 解决，而不是循环查 N 次。
     */
    private Map<Long, Long> countOrdersByUser(List<SysUser> users) {
        if (CollectionUtils.isEmpty(users)) {
            return new HashMap<>(0);
        }
        List<Long> ids = users.stream().map(SysUser::getId).collect(Collectors.toList());
        List<Map<String, Object>> rows = ordersMapper.selectMaps(new QueryWrapper<Orders>()
                .select("user_id AS uid", "COUNT(*) AS cnt")
                .in("user_id", ids)
                .groupBy("user_id"));

        Map<Long, Long> map = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object uid = row.get("uid");
            Object cnt = row.get("cnt");
            if (uid == null || cnt == null) {
                continue;
            }
            map.put(((Number) uid).longValue(), ((Number) cnt).longValue());
        }
        return map;
    }
}

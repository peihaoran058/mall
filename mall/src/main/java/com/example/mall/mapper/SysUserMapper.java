package com.example.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mall.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 *
 * <p>继承 {@code BaseMapper<SysUser>} 就白拿到了 17 个常用方法
 * （insert / deleteById / updateById / selectById / selectList / selectPage ...），
 * 一行 SQL 都不用写。这就是 MyBatis-Plus 相对原生 MyBatis 最大的价值。</p>
 *
 * <p>复杂 SQL（多表 join、统计）还是写在 XML 或 {@code @Select} 里，
 * 不要硬套 Wrapper，可读性会急剧下降。</p>
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}

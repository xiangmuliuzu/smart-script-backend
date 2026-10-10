package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.Blacklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 黑名单Mapper接口
 *
 * @author smartscript
 */
@Mapper
public interface BlacklistMapper {

    /**
     * 查询黑名单列表
     */
    List<Blacklist> selectBlacklistList(Blacklist blacklist);

    /**
     * 查询黑名单详情
     */
    Blacklist selectBlacklistById(@Param("id") Long id);

    /**
     * 新增黑名单
     */
    int insertBlacklist(Blacklist blacklist);

    /**
     * 修改黑名单
     */
    int updateBlacklist(Blacklist blacklist);

    /**
     * 删除黑名单
     */
    int deleteBlacklistById(@Param("id") Long id);
}

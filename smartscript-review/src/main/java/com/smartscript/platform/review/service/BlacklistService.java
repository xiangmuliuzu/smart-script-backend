package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.Blacklist;
import com.smartscript.platform.review.mapper.BlacklistMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 黑名单Service业务层处理
 *
 * @author smartscript
 */
@Service
public class BlacklistService {

    @Autowired
    private BlacklistMapper blacklistMapper;

    /**
     * 查询黑名单列表
     */
    public List<Blacklist> selectBlacklistList(Blacklist blacklist) {
        return blacklistMapper.selectBlacklistList(blacklist);
    }

    /**
     * 查询黑名单详情
     */
    public Blacklist selectBlacklistById(Long id) {
        return blacklistMapper.selectBlacklistById(id);
    }

    /**
     * 新增黑名单
     */
    public int insertBlacklist(Blacklist blacklist) {
        return blacklistMapper.insertBlacklist(blacklist);
    }

    /**
     * 修改黑名单
     */
    public int updateBlacklist(Blacklist blacklist) {
        return blacklistMapper.updateBlacklist(blacklist);
    }

    /**
     * 删除黑名单
     */
    public int deleteBlacklistById(Long id) {
        return blacklistMapper.deleteBlacklistById(id);
    }
}

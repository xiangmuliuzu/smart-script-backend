package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.service.ISysBookstoreService;

/**
 * 书城作品管理 服务层处理
 *
 * 依据：云端 script_platform_dev 库 sys_work 表。
 * 反推处理点：书城与作品管理共享 sys_work 表，复用 SysContentWorkMapper，不单建 Mapper。
 * status/trade_enabled/ext_json 三处写操作均做防御性裁剪，避免调用方误传其他字段
 * 被动态 SQL 一并更新。
 *
 * @author xiangsipeng
 */
@Service
public class SysBookstoreServiceImpl implements ISysBookstoreService
{
    @Autowired
    private SysContentWorkMapper workMapper;

    @Override
    public List<SysWork> selectBookstoreList(SysWork query)
    {
        return workMapper.selectBookstoreList(query);
    }

    @Override
    public SysWork selectBookstoreById(Long workId)
    {
        return workMapper.selectBookstoreById(workId);
    }

    @Override
    public int updateWorkStatus(SysWork work)
    {
        // 防御性裁剪：只放行 status
        SysWork update = new SysWork();
        update.setWorkId(work.getWorkId());
        update.setStatus(work.getStatus());
        update.setUpdateBy(work.getUpdateBy());
        return workMapper.updateWorkStatus(update);
    }

    @Override
    public int updateWorkTradeEnabled(SysWork work)
    {
        // 防御性裁剪：只放行 tradeEnabled
        SysWork update = new SysWork();
        update.setWorkId(work.getWorkId());
        update.setTradeEnabled(work.getTradeEnabled());
        update.setUpdateBy(work.getUpdateBy());
        return workMapper.updateWorkTradeEnabled(update);
    }

    @Override
    public int updateWorkExtJson(SysWork work)
    {
        // 防御性裁剪：只放行 extJson
        SysWork update = new SysWork();
        update.setWorkId(work.getWorkId());
        update.setExtJson(work.getExtJson());
        update.setUpdateBy(work.getUpdateBy());
        return workMapper.updateWorkExtJson(update);
    }
}

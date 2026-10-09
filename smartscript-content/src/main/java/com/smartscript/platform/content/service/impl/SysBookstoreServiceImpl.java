package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
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
 * 业务联动：下架自动关闭交易；开启交易前置校验上架状态——保证「已下架作品不可交易」。
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

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateWorkStatus(SysWork work)
    {
        // 防御性裁剪：只放行 status
        SysWork update = new SysWork();
        update.setWorkId(work.getWorkId());
        update.setStatus(work.getStatus());
        update.setUpdateBy(work.getUpdateBy());
        int rows = workMapper.updateWorkStatus(update);
        // 业务联动：下架即关闭交易（trade_enabled=0），保证已下架作品不可交易
        if ("off_shelf".equals(work.getStatus()))
        {
            SysWork trade = new SysWork();
            trade.setWorkId(work.getWorkId());
            trade.setTradeEnabled("0");
            trade.setUpdateBy(work.getUpdateBy());
            workMapper.updateWorkTradeEnabled(trade);
        }
        return rows;
    }

    @Override
    public int updateWorkTradeEnabled(SysWork work)
    {
        // 前置校验：开启交易要求作品已上架，防止「已下架仍可交易」
        if ("1".equals(work.getTradeEnabled()))
        {
            SysWork db = workMapper.selectBookstoreById(work.getWorkId());
            if (db == null)
            {
                throw new ServiceException("作品不存在");
            }
            if (!"on_shelf".equals(db.getStatus()))
            {
                throw new ServiceException("作品未上架，不能开启交易");
            }
        }
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

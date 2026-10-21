package com.smartscript.platform.content.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.smartscript.platform.content.domain.SysCopyrightSeal;
import com.smartscript.platform.content.domain.SysCopyrightSealLog;
import com.smartscript.platform.content.mapper.SysCopyrightSealMapper;
import com.smartscript.platform.content.service.IAppCopyrightSealService;

/**
 * App 端用户印章服务实现。
 */
@Service
public class AppCopyrightSealServiceImpl implements IAppCopyrightSealService
{
    private static final int NAME_MAX_LENGTH = 100;
    private static final int URL_MAX_LENGTH = 2000;

    @Autowired
    private SysCopyrightSealMapper sealMapper;

    @Override
    public List<Map<String, Object>> mySeals(Long userId)
    {
        List<SysCopyrightSeal> seals = sealMapper.selectSealsByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysCopyrightSeal seal : seals)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sealId", seal.getSealId());
            item.put("sealName", seal.getSealName());
            item.put("sealImageUrl", seal.getSealImageUrl());
            item.put("reviewStatus", seal.getReviewStatus());
            item.put("sealStatus", seal.getSealStatus());
            item.put("rejectReason", seal.getRejectReason());
            item.put("createTime", seal.getCreateTime());
            item.put("reviewTime", seal.getReviewTime());
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> sealDetail(Long userId, Long sealId)
    {
        SysCopyrightSeal seal = sealMapper.selectSealById(sealId);
        if (seal == null || !userId.equals(seal.getUserId()))
        {
            return null;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sealId", seal.getSealId());
        detail.put("sealName", seal.getSealName());
        detail.put("sealImageUrl", seal.getSealImageUrl());
        detail.put("materialUrls", seal.getMaterialUrls());
        detail.put("reviewStatus", seal.getReviewStatus());
        detail.put("sealStatus", seal.getSealStatus());
        detail.put("rejectReason", seal.getRejectReason());
        detail.put("createTime", seal.getCreateTime());
        detail.put("reviewTime", seal.getReviewTime());

        // 附加操作日志
        List<SysCopyrightSealLog> logs = sealMapper.selectSealLogs(sealId);
        detail.put("logs", logs);

        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitSeal(Long userId, String sealName, String sealImageUrl, String materialUrls)
    {
        if (!StringUtils.hasText(sealName) || sealName.length() > NAME_MAX_LENGTH)
        {
            throw new IllegalArgumentException("印章名称不合法");
        }
        if (!StringUtils.hasText(sealImageUrl) || sealImageUrl.length() > URL_MAX_LENGTH)
        {
            throw new IllegalArgumentException("印章图片 URL 不合法");
        }

        // 移除限制：允许用户上传多个印章
        
        SysCopyrightSeal seal = new SysCopyrightSeal();
        seal.setUserId(userId);
        seal.setSealName(sealName.trim());
        seal.setSealImageUrl(sealImageUrl.trim());
        seal.setMaterialUrls(materialUrls == null ? "" : materialUrls.trim());
        seal.setReviewStatus("pending");
        seal.setSealStatus("disabled");
        seal.setCreateTime(new Date());

        sealMapper.insertSeal(seal);
        return seal.getSealId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addMaterials(Long userId, Long sealId, String additionalMaterialUrls)
    {
        if (!StringUtils.hasText(additionalMaterialUrls))
        {
            return false;
        }

        SysCopyrightSeal seal = sealMapper.selectSealById(sealId);
        if (seal == null || !userId.equals(seal.getUserId()))
        {
            return false;
        }

        String existing = seal.getMaterialUrls();
        String updated = StringUtils.hasText(existing) 
            ? existing + "," + additionalMaterialUrls.trim()
            : additionalMaterialUrls.trim();

        return sealMapper.updateMaterials(sealId, updated) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resubmitSeal(Long userId, Long sealId, String sealImageUrl, String materialUrls)
    {
        SysCopyrightSeal seal = sealMapper.selectSealById(sealId);
        if (seal == null || !userId.equals(seal.getUserId()))
        {
            return false;
        }

        // 只有驳回状态才能重新提交
        if (!"rejected".equals(seal.getReviewStatus()))
        {
            return false;
        }

        if (!StringUtils.hasText(sealImageUrl) || sealImageUrl.length() > URL_MAX_LENGTH)
        {
            return false;
        }

        seal.setSealImageUrl(sealImageUrl.trim());
        seal.setMaterialUrls(materialUrls == null ? "" : materialUrls.trim());
        seal.setReviewStatus("pending");
        seal.setRejectReason(null);

        return sealMapper.updateResubmit(seal) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disableSeal(Long userId, Long sealId)
    {
        SysCopyrightSeal seal = sealMapper.selectSealById(sealId);
        if (seal == null || !userId.equals(seal.getUserId()))
        {
            return false;
        }

        // 启用中即可停用（含「审核中但仍启用」的历史异常数据，便于用户先停用再改）
        if (!"enabled".equals(seal.getSealStatus()))
        {
            return false;
        }

        return sealMapper.updateSealStatusByUser(sealId, "disabled", userId.toString()) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateSeal(Long userId, Long sealId, String sealName, String sealImageUrl, String materialUrls)
    {
        SysCopyrightSeal seal = sealMapper.selectSealById(sealId);
        if (seal == null || !userId.equals(seal.getUserId()))
        {
            return false;
        }

        // 启用中的印章必须先停用，再允许编辑提交（避免审核中仍可对外使用）
        if ("enabled".equals(seal.getSealStatus()))
        {
            throw new IllegalArgumentException("启用中的印章请先停用后再编辑修改");
        }

        // 构建更新对象
        SysCopyrightSeal updateSeal = new SysCopyrightSeal();
        updateSeal.setSealId(sealId);
        
        boolean hasChanges = false;

        // 更新印章名称
        if (sealName != null && StringUtils.hasText(sealName))
        {
            if (sealName.length() > NAME_MAX_LENGTH)
            {
                throw new IllegalArgumentException("印章名称不合法");
            }
            updateSeal.setSealName(sealName.trim());
            hasChanges = true;
        }

        // 更新印章图片
        if (sealImageUrl != null && StringUtils.hasText(sealImageUrl))
        {
            if (sealImageUrl.length() > URL_MAX_LENGTH)
            {
                throw new IllegalArgumentException("印章图片 URL 不合法");
            }
            updateSeal.setSealImageUrl(sealImageUrl.trim());
            hasChanges = true;
        }

        // 更新审核材料
        if (materialUrls != null)
        {
            updateSeal.setMaterialUrls(materialUrls.trim());
            hasChanges = true;
        }

        if (!hasChanges)
        {
            return false;
        }

        // 修改后重置审核状态为 pending，清空驳回原因
        updateSeal.setReviewStatus("pending");
        updateSeal.setRejectReason(null);
        updateSeal.setReviewerId(null);
        updateSeal.setReviewTime(null);

        return sealMapper.updateResubmit(updateSeal) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSeal(Long userId, Long sealId)
    {
        SysCopyrightSeal seal = sealMapper.selectSealById(sealId);
        if (seal == null || !userId.equals(seal.getUserId()))
        {
            return false;
        }

        // 软删除
        return sealMapper.deleteSealById(sealId) > 0;
    }
}

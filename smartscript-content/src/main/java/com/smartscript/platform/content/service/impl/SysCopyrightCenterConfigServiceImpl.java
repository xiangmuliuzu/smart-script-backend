package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.DateUtils;
import com.smartscript.platform.content.domain.SysCopyrightCenterConfig;
import com.smartscript.platform.content.mapper.SysCopyrightCenterConfigMapper;
import com.smartscript.platform.content.service.ISysCopyrightCenterConfigService;
import com.smartscript.platform.content.service.ICopyrightCenterApiService;

/**
 * 版权中心配置Service业务层处理
 * 
 * @author SmartScript Team
 * @date 2024-01-15
 */
@Service
public class SysCopyrightCenterConfigServiceImpl implements ISysCopyrightCenterConfigService 
{
    @Autowired
    private SysCopyrightCenterConfigMapper copyrightCenterConfigMapper;

    @Autowired
    private ICopyrightCenterApiService copyrightCenterApiService;

    @Override
    public SysCopyrightCenterConfig selectCopyrightCenterConfigByConfigId(Long configId)
    {
        return copyrightCenterConfigMapper.selectCopyrightCenterConfigByConfigId(configId);
    }

    @Override
    public List<SysCopyrightCenterConfig> selectCopyrightCenterConfigList(SysCopyrightCenterConfig copyrightCenterConfig)
    {
        return copyrightCenterConfigMapper.selectCopyrightCenterConfigList(copyrightCenterConfig);
    }

    @Override
    public int insertCopyrightCenterConfig(SysCopyrightCenterConfig copyrightCenterConfig)
    {
        copyrightCenterConfig.setCreateTime(DateUtils.getNowDate());
        return copyrightCenterConfigMapper.insertCopyrightCenterConfig(copyrightCenterConfig);
    }

    @Override
    public int updateCopyrightCenterConfig(SysCopyrightCenterConfig copyrightCenterConfig)
    {
        copyrightCenterConfig.setUpdateTime(DateUtils.getNowDate());
        return copyrightCenterConfigMapper.updateCopyrightCenterConfig(copyrightCenterConfig);
    }

    @Override
    public int deleteCopyrightCenterConfigByConfigIds(Long[] configIds)
    {
        return copyrightCenterConfigMapper.deleteCopyrightCenterConfigByConfigIds(configIds);
    }

    @Override
    public int deleteCopyrightCenterConfigByConfigId(Long configId)
    {
        return copyrightCenterConfigMapper.deleteCopyrightCenterConfigByConfigId(configId);
    }

    @Override
    public SysCopyrightCenterConfig getActiveConfig()
    {
        return copyrightCenterConfigMapper.selectActiveConfig();
    }

    @Override
    public List<SysCopyrightCenterConfig> getActiveCenterList()
    {
        // 查询所有启用状态的版权中心（status = 'enabled'）
        SysCopyrightCenterConfig query = new SysCopyrightCenterConfig();
        query.setStatus("enabled");
        return copyrightCenterConfigMapper.selectCopyrightCenterConfigList(query);
    }

    @Override
    public boolean testConnection(Long configId)
    {
        SysCopyrightCenterConfig config = copyrightCenterConfigMapper.selectCopyrightCenterConfigByConfigId(configId);
        if (config == null)
        {
            throw new RuntimeException("配置不存在");
        }

        return copyrightCenterApiService.testConnection(
            config.getApiUrl(), 
            config.getApiKey(), 
            config.getApiSecret()
        );
    }
}

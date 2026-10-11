package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysCopyrightCenterConfig;

/**
 * 版权中心配置Service接口
 * 
 * @author SmartScript Team
 * @date 2024-01-15
 */
public interface ISysCopyrightCenterConfigService 
{
    /**
     * 查询版权中心配置
     * 
     * @param configId 版权中心配置主键
     * @return 版权中心配置
     */
    public SysCopyrightCenterConfig selectCopyrightCenterConfigByConfigId(Long configId);

    /**
     * 查询版权中心配置列表
     * 
     * @param copyrightCenterConfig 版权中心配置
     * @return 版权中心配置集合
     */
    public List<SysCopyrightCenterConfig> selectCopyrightCenterConfigList(SysCopyrightCenterConfig copyrightCenterConfig);

    /**
     * 新增版权中心配置
     * 
     * @param copyrightCenterConfig 版权中心配置
     * @return 结果
     */
    public int insertCopyrightCenterConfig(SysCopyrightCenterConfig copyrightCenterConfig);

    /**
     * 修改版权中心配置
     * 
     * @param copyrightCenterConfig 版权中心配置
     * @return 结果
     */
    public int updateCopyrightCenterConfig(SysCopyrightCenterConfig copyrightCenterConfig);

    /**
     * 批量删除版权中心配置
     * 
     * @param configIds 需要删除的版权中心配置主键集合
     * @return 结果
     */
    public int deleteCopyrightCenterConfigByConfigIds(Long[] configIds);

    /**
     * 删除版权中心配置信息
     * 
     * @param configId 版权中心配置主键
     * @return 结果
     */
    public int deleteCopyrightCenterConfigByConfigId(Long configId);

    /**
     * 获取启用中的配置
     * 
     * @return 版权中心配置
     */
    public SysCopyrightCenterConfig getActiveConfig();

    /**
     * 获取所有启用的版权中心配置列表
     * 
     * @return 版权中心配置列表
     */
    public List<SysCopyrightCenterConfig> getActiveCenterList();

    /**
     * 测试连接
     * 
     * @param configId 配置ID
     * @return 是否连接成功
     */
    public boolean testConnection(Long configId);
}

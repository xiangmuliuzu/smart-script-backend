package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysCopyrightApplication;

/**
 * 版权申请Service接口
 * 
 * @author SmartScript Team
 * @date 2024-01-15
 */
public interface ISysCopyrightApplicationService 
{
    /**
     * 查询版权申请
     * 
     * @param applyId 版权申请主键
     * @return 版权申请
     */
    public SysCopyrightApplication selectCopyrightApplicationByApplyId(Long applyId);

    /**
     * 查询版权申请列表
     * 
     * @param copyrightApplication 版权申请
     * @return 版权申请集合
     */
    public List<SysCopyrightApplication> selectCopyrightApplicationList(SysCopyrightApplication copyrightApplication);

    /**
     * 新增版权申请
     * 
     * @param copyrightApplication 版权申请
     * @return 结果
     */
    public int insertCopyrightApplication(SysCopyrightApplication copyrightApplication);

    /**
     * 修改版权申请
     * 
     * @param copyrightApplication 版权申请
     * @return 结果
     */
    public int updateCopyrightApplication(SysCopyrightApplication copyrightApplication);

    /**
     * 批量删除版权申请
     * 
     * @param applyIds 需要删除的版权申请主键集合
     * @return 结果
     */
    public int deleteCopyrightApplicationByApplyIds(Long[] applyIds);

    /**
     * 删除版权申请信息
     * 
     * @param applyId 版权申请主键
     * @return 结果
     */
    public int deleteCopyrightApplicationByApplyId(Long applyId);

    /**
     * 从申请管理页提交到版权中心配置页（分配config_id）
     * 
     * @param applyId 申请ID
     * @param configId 版权中心配置ID
     * @return 结果
     */
    public int submitToCenterConfig(Long applyId, Long configId);

    /**
     * 从版权中心配置页提交到第三方API
     * 
     * @param applyId 申请ID
     * @return 结果
     */
    public int submitApplicationToCenter(Long applyId);

    /**
     * 同步版权申请状态
     * 
     * @param applyId 申请ID
     * @return 结果
     */
    public int syncApplicationStatus(Long applyId);

    /**
     * 审核版权申请
     * 
     * @param applyId 申请ID
     * @param status 审核结果（approved/rejected）
     * @param rejectReason 驳回原因（当status为rejected时必填）
     * @return 结果
     */
    public int reviewApplication(Long applyId, String status, String rejectReason);
}

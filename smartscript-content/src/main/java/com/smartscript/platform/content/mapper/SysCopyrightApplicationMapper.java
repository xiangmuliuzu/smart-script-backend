package com.smartscript.platform.content.mapper;

import java.util.List;
import com.smartscript.platform.content.domain.SysCopyrightApplication;

/**
 * 版权申请Mapper接口
 * 
 * @author SmartScript Team
 * @date 2024-01-15
 */
public interface SysCopyrightApplicationMapper 
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
     * 删除版权申请
     * 
     * @param applyId 版权申请主键
     * @return 结果
     */
    public int deleteCopyrightApplicationByApplyId(Long applyId);

    /**
     * 批量删除版权申请
     * 
     * @param applyIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteCopyrightApplicationByApplyIds(Long[] applyIds);

    /**
     * 根据作品ID查询版权申请
     * 
     * @param workId 作品ID
     * @return 版权申请
     */
    public SysCopyrightApplication selectApplicationByWorkId(Long workId);
}

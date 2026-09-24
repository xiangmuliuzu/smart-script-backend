package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.AiQuotaRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * AI配额记录Mapper接口
 *
 * @author smartscript
 */
@Mapper
public interface AiQuotaRecordMapper {

    /**
     * 查询AI配额记录列表
     */
    List<AiQuotaRecord> selectAiQuotaRecordList(AiQuotaRecord aiQuotaRecord);

    /**
     * 查询AI配额记录详情
     */
    AiQuotaRecord selectAiQuotaRecordById(@Param("recordId") Long recordId);

    /**
     * 新增AI配额记录
     */
    int insertAiQuotaRecord(AiQuotaRecord aiQuotaRecord);

    /**
     * 修改AI配额记录
     */
    int updateAiQuotaRecord(AiQuotaRecord aiQuotaRecord);

    /**
     * 删除AI配额记录
     */
    int deleteAiQuotaRecordById(@Param("recordId") Long recordId);
}

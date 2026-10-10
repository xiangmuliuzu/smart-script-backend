package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.AiRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI调用请求Mapper
 *
 * @author smartscript
 */
@Mapper
public interface AiRequestMapper {

    /**
     * 新增AI调用请求
     */
    int insertAiRequest(AiRequest aiRequest);
}

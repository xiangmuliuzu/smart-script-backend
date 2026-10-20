package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.vo.WorkChapterVO;
import com.smartscript.platform.review.domain.vo.WorkContentVO;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 审核内容查询：人工审核时读取作品完整内容（简介/设定 + 章节正文）。
 * 直接查 sys_work / sys_work_chapter，避免 review 模块跨依赖 content 模块。
 */
public interface ReviewWorkContentMapper {

    /** 作品基础内容：标题、类型、简介、核心/角色设定 */
    WorkContentVO selectWorkContent(@Param("workId") Long workId);

    /** 章节列表（正文为原始存储，service 层转纯文本） */
    List<WorkChapterVO> selectWorkChapters(@Param("workId") Long workId);
}

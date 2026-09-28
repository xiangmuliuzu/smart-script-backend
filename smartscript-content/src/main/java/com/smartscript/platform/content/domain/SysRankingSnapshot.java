package com.smartscript.platform.content.domain;

import java.math.BigDecimal;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 排行榜快照对象 sys_ranking_snapshot
 *
 * 依据：云端 script_platform_dev 库 sys_ranking_snapshot 表（附件5.1 表3-86）。
 *
 * 说明（无文档依据，反推处理点）：
 * 1. 表无 created_at/updated_at，仅有若依5通用字段（继承 BaseEntity）。
 * 2. status 为 tinyint，Entity 用 String（"0"=失效 "1"=有效）。
 * 3. view_count/bookshelf_count 为 bigint，Entity 用 Long。
 * 4. 主键 ranking_id 为 bigint，Entity 用 Long。
 * 5. workTitle 为 JOIN 查询扩展字段（sys_work.title）。
 *
 * @author xiangsipeng
 */
public class SysRankingSnapshot extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 排行榜ID */
    private Long rankingId;

    /** 榜单类型 */
    private String rankingType;

    /** 周期开始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date periodStart;

    /** 周期结束日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date periodEnd;

    /** 作品ID */
    private Long workId;

    /** 排名 */
    private Integer rankNo;

    /** 分数 */
    private BigDecimal score;

    /** 浏览量 */
    private Long viewCount;

    /** 收藏量 */
    private Long bookshelfCount;

    /** 增长分数 */
    private BigDecimal growthScore;

    /** 快照时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date snapshotTime;

    /** 状态（0=失效 1=有效） */
    private String status;

    // ---- JOIN 扩展字段 ----

    /** 作品标题（JOIN sys_work.title） */
    private String workTitle;

    public SysRankingSnapshot()
    {
    }

    public Long getRankingId()
    {
        return rankingId;
    }

    public void setRankingId(Long rankingId)
    {
        this.rankingId = rankingId;
    }

    public String getRankingType()
    {
        return rankingType;
    }

    public void setRankingType(String rankingType)
    {
        this.rankingType = rankingType;
    }

    public Date getPeriodStart()
    {
        return periodStart;
    }

    public void setPeriodStart(Date periodStart)
    {
        this.periodStart = periodStart;
    }

    public Date getPeriodEnd()
    {
        return periodEnd;
    }

    public void setPeriodEnd(Date periodEnd)
    {
        this.periodEnd = periodEnd;
    }

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public Integer getRankNo()
    {
        return rankNo;
    }

    public void setRankNo(Integer rankNo)
    {
        this.rankNo = rankNo;
    }

    public BigDecimal getScore()
    {
        return score;
    }

    public void setScore(BigDecimal score)
    {
        this.score = score;
    }

    public Long getViewCount()
    {
        return viewCount;
    }

    public void setViewCount(Long viewCount)
    {
        this.viewCount = viewCount;
    }

    public Long getBookshelfCount()
    {
        return bookshelfCount;
    }

    public void setBookshelfCount(Long bookshelfCount)
    {
        this.bookshelfCount = bookshelfCount;
    }

    public BigDecimal getGrowthScore()
    {
        return growthScore;
    }

    public void setGrowthScore(BigDecimal growthScore)
    {
        this.growthScore = growthScore;
    }

    public Date getSnapshotTime()
    {
        return snapshotTime;
    }

    public void setSnapshotTime(Date snapshotTime)
    {
        this.snapshotTime = snapshotTime;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getWorkTitle()
    {
        return workTitle;
    }

    public void setWorkTitle(String workTitle)
    {
        this.workTitle = workTitle;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("rankingId", getRankingId())
                .append("rankingType", getRankingType())
                .append("periodStart", getPeriodStart())
                .append("periodEnd", getPeriodEnd())
                .append("workId", getWorkId())
                .append("workTitle", getWorkTitle())
                .append("rankNo", getRankNo())
                .append("score", getScore())
                .append("viewCount", getViewCount())
                .append("bookshelfCount", getBookshelfCount())
                .append("growthScore", getGrowthScore())
                .append("snapshotTime", getSnapshotTime())
                .append("status", getStatus())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}

package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.PointsAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 积分账户Mapper接口
 *
 * @author smartscript
 */
@Mapper
public interface PointsAccountMapper {

    List<PointsAccount> selectPointsAccountList(PointsAccount pointsAccount);

    /** 按用户查询账户（uk_user_id 唯一） */
    PointsAccount selectByUserId(@Param("userId") Long userId);

    /** 创建积分账户（balance=0） */
    int insertAccount(PointsAccount pointsAccount);

    /**
     * 增加积分：余额/累计获得/今日获得原子累加，并刷新最近变动日期。
     * 返回影响行数；用户不存在返回 0。
     */
    int increaseBalance(@Param("userId") Long userId,
                        @Param("points") int points,
                        @Param("date") String date);
}

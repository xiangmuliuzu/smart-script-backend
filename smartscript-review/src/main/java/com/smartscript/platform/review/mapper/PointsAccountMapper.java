package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.PointsAccount;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 积分账户Mapper接口
 *
 * @author smartscript
 */
@Mapper
public interface PointsAccountMapper {

    List<PointsAccount> selectPointsAccountList(PointsAccount pointsAccount);
}

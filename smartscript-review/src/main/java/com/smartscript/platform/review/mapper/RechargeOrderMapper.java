package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.RechargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 积分充值订单 Mapper。
 *
 * @author smartscript
 */
@Mapper
public interface RechargeOrderMapper {

    /** 新增订单（回填 orderId） */
    int insertOrder(RechargeOrder order);

    /** 按订单号查询（支付回调对账用） */
    RechargeOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /** 按用户最近的充值订单 */
    List<RechargeOrder> selectByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 待支付订单置为已支付（仅 status=PENDING 时生效，保证回调/模拟支付幂等）。
     * 返回影响行数：0 表示订单已支付或不存在。
     */
    int markPaid(@Param("orderId") Long orderId, @Param("payTime") Date payTime);
}

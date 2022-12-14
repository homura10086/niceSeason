package io.niceseason.gulimall.order.to;

import io.niceseason.gulimall.order.entity.OrderEntity;
import io.niceseason.gulimall.order.entity.OrderItemEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

//创建订单、订单项
// 抽取模型
@Data
public class OrderCreateTo {

    private OrderEntity order;

    private List<OrderItemEntity> orderItems;

    /** 订单计算的应付价格 **/
    private BigDecimal payPrice;

    /** 运费 **/
    private BigDecimal fare;

}

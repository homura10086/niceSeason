package io.niceseason.gulimall.order.vo;

import io.niceseason.gulimall.order.entity.OrderEntity;
import lombok.Data;

//成功后转发至支付页面携带数据
@Data
public class SubmitOrderResponseVo {

    private OrderEntity order;

    /** 错误状态码 **/
    private Integer code;
}

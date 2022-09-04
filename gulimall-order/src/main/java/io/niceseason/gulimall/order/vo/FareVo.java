package io.niceseason.gulimall.order.vo;

import lombok.Data;

import java.math.BigDecimal;

//运费收件信息获取
//数据封装
@Data
public class FareVo {
    private MemberAddressVo address;

    private BigDecimal fare;
}

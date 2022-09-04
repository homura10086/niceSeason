package io.niceseason.gulimall.product.feign.fallback;

import io.niceseason.common.exception.BizCodeEnum;
import io.niceseason.common.utils.R;
import io.niceseason.gulimall.product.feign.SeckillFeignService;
import org.springframework.stereotype.Component;

//在降级类中实现对应的feign接口,并重写降级方法
@Component
public class SeckillFallbackService implements SeckillFeignService {
    @Override
    public R getSeckillSkuInfo(Long skuId) {
        return R.error(BizCodeEnum.READ_TIME_OUT_EXCEPTION.getCode(), BizCodeEnum.READ_TIME_OUT_EXCEPTION.getMsg());
    }
}

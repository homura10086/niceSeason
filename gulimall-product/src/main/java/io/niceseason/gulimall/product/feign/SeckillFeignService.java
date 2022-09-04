package io.niceseason.gulimall.product.feign;

import io.niceseason.common.utils.R;
import io.niceseason.gulimall.product.feign.fallback.SeckillFallbackService;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

//feign的流控和降级
//feign的降级
// 在@FeignClient设置fallback属性
@FeignClient(value = "gulimall-seckill", fallback = SeckillFallbackService.class)
public interface SeckillFeignService {
    @ResponseBody
    @GetMapping(value = "/getSeckillSkuInfo/{skuId}")
    R getSeckillSkuInfo(@PathVariable("skuId") Long skuId);
}

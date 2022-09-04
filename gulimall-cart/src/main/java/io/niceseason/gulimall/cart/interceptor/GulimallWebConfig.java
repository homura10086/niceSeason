package io.niceseason.gulimall.cart.interceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

//用户身份鉴别方式
//在点击购物车时，会为临时用户生成一个name为user-key的cookie临时标识，过期时间为一个月，如果手动清除user-key，
// 那么临时购物车的购物项也被清除，所以user-key是用来标识和存储临时购物车数据的

// 使用ThreadLocal进行用户身份鉴别信息传递
// 在调用购物车的接口前，先通过session信息判断是否登录，并分别进行用户身份信息的封装，并把user-key放在cookie中
// 这个功能使用拦截器进行完成
@Configuration
public class GulimallWebConfig implements WebMvcConfigurer {
    //拦截所有请求
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CartInterceptor()).addPathPatterns("/**");
    }
}

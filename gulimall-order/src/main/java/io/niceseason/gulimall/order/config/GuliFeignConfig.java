package io.niceseason.gulimall.order.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

//在feign的调用过程中，会使用容器中的RequestInterceptor对RequestTemplate进行处理，
// 因此我们可以通过向容器中导入定制的RequestInterceptor为请求加上cookie
//RequestContextHolder为SpingMVC中共享request数据的上下文，底层由ThreadLocal实现
@Configuration
public class GuliFeignConfig {
    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            //1. 使用RequestContextHolder拿到老请求的请求数据
            ServletRequestAttributes requestAttributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (requestAttributes != null) {
                HttpServletRequest request = requestAttributes.getRequest();
                //2. 将老请求得到cookie信息放到feign请求上
                String cookie = request.getHeader("Cookie");
                template.header("Cookie", cookie);
            }
        };
    }
}

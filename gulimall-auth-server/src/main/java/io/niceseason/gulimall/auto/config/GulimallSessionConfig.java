package io.niceseason.gulimall.auto.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

//  session跨越共享问题
//   SpringSession整合redis
//   通过SpringSession修改session的作用域
//  SpringSession核心原理 - 装饰者模式
//    原生的获取session时是通过HttpServletRequest获取的
//    这里对request进行包装，并且重写了包装request的getSession()方法

//   自定义配置
//   由于默认使用jdk进行序列化，通过导入RedisSerializer修改为json序列化
//   并且通过修改CookieSerializer扩大session的作用域至**.gulimall.com
@Configuration
public class GulimallSessionConfig {

    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        return new GenericJackson2JsonRedisSerializer();
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("GULISESSIONID");
        serializer.setDomainName("gulimall.com");
        return serializer;
    }
}

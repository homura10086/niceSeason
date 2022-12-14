package io.niceseason.gulimall.seckill.schedule;

import io.niceseason.gulimall.seckill.Service.SecKillService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SecKillScheduled {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private SecKillService secKillService;


    /**
     * 定时任务
     * 每天三点上架最近三天的秒杀商品
     */
    //秒杀商品上架功能的锁
//    每天凌晨三点远程调用coupon服务上架最近三天的秒杀商品
//    由于在分布式情况下该方法可能同时被调用多次，因此加入分布式锁，同时只有一个服务可以调用该方法
    @Async
    @Scheduled(cron = "0 0 3 * * ?")
//    @Scheduled(cron = "* 33 17 * * ?")
    public void uploadSeckillSkuLatest3Days() {
        //为避免分布式情况下多服务同时上架的情况，使用分布式锁
        String upload_lock = "seckill:upload:lock";
        RLock lock = redissonClient.getLock(upload_lock);
        try {
            lock.lock(10, TimeUnit.SECONDS);
            secKillService.uploadSeckillSkuLatest3Days();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            lock.unlock();
        }
    }
}

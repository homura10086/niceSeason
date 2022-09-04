package io.niceseason.gulimall.product.web;

import io.niceseason.common.utils.R;
import io.niceseason.gulimall.product.entity.CategoryEntity;
import io.niceseason.gulimall.product.feign.SeckillFeignService;
import io.niceseason.gulimall.product.service.CategoryService;
import io.niceseason.gulimall.product.vo.Catalog2Vo;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class IndexController {
    @Autowired
    private CategoryService categoryService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Qualifier("io.niceseason.gulimall.product.feign.SeckillFeignService")
    @Autowired
    private SeckillFeignService seckillFeignService;

    //由于访问首页时就要加载一级目录,所以我们需要在加载首页时获取该数据
    @GetMapping({"/", "index.html"})
    public String getIndex(Model model) {
        //获取所有的一级分类
        List<CategoryEntity> catagories = categoryService.getLevel1Catagories();
        model.addAttribute("catagories", catagories);
        return "index";
    }

    // 渲染三级分类菜单
    @GetMapping("index/json/catalog.json")
    @ResponseBody
    public Map<String, List<Catalog2Vo>> getCategoryMap() {
        return categoryService.getCatalogJsonDbWithSpringCache();
    }

    //  读写锁（ReadWriteLock）
    //  含有写的过程都会被阻塞，只有读读不会被阻塞
    @GetMapping("/read")
    @ResponseBody
    public String read() throws InterruptedException {
        RReadWriteLock lock = redissonClient.getReadWriteLock("ReadWrite-Lock");
        RLock rLock = lock.readLock();
        String s = "";
        try {
            rLock.lock();
            System.out.println("读锁加锁"+Thread.currentThread().getId());
            Thread.sleep(5000);
            s = redisTemplate.opsForValue().get("lock-value");
        }finally {
            rLock.unlock();
        }
        return "读取完成:" + s;
    }

    @GetMapping("/write")
    @ResponseBody
    public String write() {
        RReadWriteLock lock = redissonClient.getReadWriteLock("ReadWrite-Lock");
        RLock wLock = lock.writeLock();
        String s = UUID.randomUUID().toString();
        try {
            wLock.lock();
            System.out.println("写锁加锁"+Thread.currentThread().getId());
            Thread.sleep(10000);
            redisTemplate.opsForValue().set("lock-value",s);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            wLock.unlock();
            return "写入完成:"+s;
        }
    }

    //  信号量（Semaphore）
    //  信号量为存储在redis中的一个数字，当这个数字大于0时，即可以调用acquire()方法增加数量，也可以调用release()方法减少数量，
    //  但是当调用release()之后小于0的话方法就会阻塞，直到数字大于0
    @GetMapping("/park")
    @ResponseBody
    public String park() {
        RSemaphore park = redissonClient.getSemaphore("park");
        try {
            park.acquire(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "停进2";
    }

    @GetMapping("/go")
    @ResponseBody
    public String go() {
        RSemaphore park = redissonClient.getSemaphore("park");
        park.release(2);
        return "开走2";
    }

    //   闭锁（CountDownLatch）
    //   可以理解为门栓，使用若干个门栓将当前方法阻塞，只有当全部门栓都被放开时，当前方法才能继续执行。
    //   以下代码只有offLatch()被调用5次后 setLatch()才能继续执行
    @GetMapping("/setLatch")
    @ResponseBody
    public String setLatch() {
        RCountDownLatch latch = redissonClient.getCountDownLatch("CountDownLatch");
        try {
            latch.trySetCount(5);
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "门栓被放开";
    }

    @GetMapping("/offLatch")
    @ResponseBody
    public String offLatch() {
        RCountDownLatch latch = redissonClient.getCountDownLatch("CountDownLatch");
        latch.countDown();
        return "门栓被放开1";
    }

    @ResponseBody
    @GetMapping("/getSeckillSkuInfo/{skuId}")
    public R getSeckillSkuInfo(@PathVariable("skuId") Long skuId) {
        return seckillFeignService.getSeckillSkuInfo(skuId);
    }

}

package io.niceseason.gulimall.product.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.niceseason.common.utils.PageUtils;
import io.niceseason.common.utils.Query;
import io.niceseason.gulimall.product.dao.CategoryDao;
import io.niceseason.gulimall.product.entity.CategoryEntity;
import io.niceseason.gulimall.product.service.CategoryService;
import io.niceseason.gulimall.product.vo.Catalog2Vo;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

    @Autowired
    private CategoryBrandRelationServiceImpl categoryBrandRelationService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    //测试本地缓存，通过hashmap
//    private Map<String,Object> cache=new HashMap<>();

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public List<CategoryEntity> listWithTree() {
        List<CategoryEntity> entities = baseMapper.selectList(null);
        List<CategoryEntity> collect = entities.stream()
                .filter(item->item.getParentCid()==0)
                .map(menu->{
                    menu.setChildren(getChildrens(menu,entities));
                    return menu;
                })
                .sorted((menu1,menu2)->{
                    return (menu1.getSort()==null?0:menu1.getSort()) - (menu2.getSort()==null?0:menu2.getSort());
                })
                .collect(Collectors.toList());
        return collect;
    }

    @Override
    public void removeMenuByIds(List<Long> asList) {
        baseMapper.deleteBatchIds(asList);
    }


    @Override
    public Long[] findCatelogPathById(Long categorygId) {
        List<Long> path = new LinkedList<>();
        findPath(categorygId, path);
        Collections.reverse(path);
        Long[] objects = path.toArray(new Long[path.size()]);
        return  objects;
    }

    /**
     * 级联更新所有数据
     * @param category
     */
    //调用该方法会删除缓存category下的所有cache
    @Transactional  //因为涉及到多次修改，因此要开启事务
    @Override
    @CacheEvict(value = {"category"}, allEntries = true)
    public void updateCascade(CategoryEntity category) {
        this.updateById(category);
        if (!StringUtils.isEmpty(category.getName())) {
            categoryBrandRelationService.updateCategory(category);
        }
    }

    @Override
    public List<CategoryEntity> getLevel1Catagories() {
        //        long start = System.currentTimeMillis();
        //        System.out.println("查询一级菜单时间:"+(System.currentTimeMillis()-start));
        return this.list(new QueryWrapper<CategoryEntity>().eq("parent_cid", 0));
    }

    //  Spring-Cache的不足之处
    //  读模式
    //  缓存穿透：查询一个null数据。解决方案：缓存空数据，可通过spring.cache.redis.cache-null-values=true
    //  缓存击穿：大量并发进来同时查询一个正好过期的数据。解决方案：加锁，使用sync = true来解决击穿问题
    //  缓存雪崩：大量的key同时过期。解决：加随机时间。加上过期时间

    //  写模式：（缓存与数据库一致）
    //  a、读写加锁。
    //  b、引入Canal,感知到MySQL的更新去更新Redis
    //  c 、读多写多，直接去数据库查询就行
    //  常规数据（读多写少，即时性，一致性要求不高的数据，完全可以使用Spring-Cache）：
    //  写模式(只要缓存的数据有过期时间就足够了)

    //调用该方法时会将结果缓存，缓存名为category，key为方法名
    //sync = true 表示该方法的缓存被读取时会加锁
    @Cacheable(value = {"category"}, key = "#root.methodName", sync = true)
    public Map<String, List<Catalog2Vo>> getCatalogJsonDbWithSpringCache() {
        return getCategoriesDb();
    }

    // 可重入锁（Reentrant Lock）
    // 如果负责储存这个分布式锁的Redisson节点宕机以后，而且这个锁正好处于锁住的状态时，这个锁会出现锁死的状态。为了避免这种情况的发生，
    // 所以就设置了过期时间，但是如果业务执行时间过长，业务还未执行完锁就已经过期，那么就会出现解锁时解了其他线程的锁的情况。
    // 所以Redisson内部提供了一个监控锁的看门狗，它的作用是在Redisson实例被关闭前，不断的延长锁的有效期。默认情况下，
    // 看门狗的检查锁的超时时间是30秒钟，也可以通过修改Config.lockWatchdogTimeout来另行指定
    public Map<String, List<Catalog2Vo>> getCatalogJsonDbWithRedisson() throws InterruptedException {
        Map<String, List<Catalog2Vo>> categoryMap = null;
        RLock lock = redissonClient.getLock("CatalogJson-Lock");
        // Redisson还通过加锁的方法提供了leaseTime的参数来指定加锁的时间。超过这个时间后锁便自动解开了。不会自动续期
        // 加锁以后10秒钟自动解锁
        // 无需调用unlock方法手动解锁,如果要手动解锁一定要确保业务执行时间小于锁的失效时间
        lock.lock(10, TimeUnit.SECONDS);
        // 尝试加锁，最多等待100秒，上锁以后10秒自动解锁
        boolean res = lock.tryLock(100, 10, TimeUnit.SECONDS);
        if(res) {
            try {
                Thread.sleep(15000);
                categoryMap = getCategoryMap();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
        return categoryMap;
    }

//    缓存穿透
//    指查询一个一定不存在的数据，由于缓存是不命中，将去查询数据库，但是数据库也无此记录，我们没有将这次查询的null写入缓存，
//    这将导致这个不存在的数据每次请求都要到存储层去查询，失去了缓存的意义
//    风险： 利用不存在的数据进行攻击，数据库瞬时压力增大，最终导致崩溃
//    解决： null结果缓存，并加入短暂过期时间
//
//    缓存雪崩
//    缓存雪崩是指在我们设置缓存时key采用了相同的过期时间，导致缓存在某一时刻同时失效，请求全部转发到DB，DB瞬时 压力过重雪崩。
//    解决： 原有的失效时间基础上增加一个随机值，比如1-5分钟随机，这样每一个缓存的过期时间的重复率就会降低，就很难引发集体失效的事件。
//
//    缓存击穿
//    对于一些设置了过期时间的key，如果这些key可能会在某些时间点被超高并发地访问，是一种非常“热点”的数据。
//    如果这个key在大量请求同时进来前正好失效，那么所有对这个key的数据查询都落到db，我们称为缓存击穿。
//    解决： 加锁。大量并发只让一个去查，其他人等待，查到以后释放锁，其他人获取到锁，先查缓存，就会有数据，不用去db

    //  加锁解决缓存击穿问题
    //  将查询db的方法加锁，这样在同一时间只有一个方法能查询数据库，就能解决缓存击穿的问题了
    //  分布式缓存
    //  当有多个服务存在时，每个服务的缓存仅能够为本服务使用，这样每个服务都要查询一次数据库，并且当数据更新时只会更新单个服务的缓存数据，
    //  就会造成数据不一致的问题所有的服务都到同一个redis进行获取数据，就可以避免这个问题
    //  分布式锁
    //  当分布式项目在高并发下也需要加锁，但本地锁只能锁住当前服务，这个时候就需要分布式锁
    @Override
    public Map<String, List<Catalog2Vo>> getCategoryMap() {
//        List<CategoryEntity> categoryEntities = this.list(new QueryWrapper<CategoryEntity>().eq("cat_level", 2));
//
//        List<Catalog2Vo> catalog2Vos = categoryEntities.stream().map(categoryEntity -> {
//            List<CategoryEntity> level3 = this.list(new QueryWrapper<CategoryEntity>().eq("parent_cid", categoryEntity.getCatId()));
//            List<Catalog2Vo.Catalog3Vo> catalog3Vos = level3.stream().map(cat -> {
//                return new Catalog2Vo.Catalog3Vo(cat.getParentCid().toString(), cat.getCatId().toString(), cat.getName());
//            }).collect(Collectors.toList());
//            Catalog2Vo catalog2Vo = new Catalog2Vo(categoryEntity.getParentCid().toString(), categoryEntity.getCatId().toString(), categoryEntity.getName(), catalog3Vos);
//            return catalog2Vo;
//        }).collect(Collectors.toList());
//        Map<String, List<Catalog2Vo>> catalogMap = new HashMap<>();
//        for (Catalog2Vo catalog2Vo : catalog2Vos) {
//            List<Catalog2Vo> list = catalogMap.getOrDefault(catalog2Vo.getCatalog1Id(), new LinkedList<>());
//            list.add(catalog2Vo);
//            catalogMap.put(catalog2Vo.getCatalog1Id(),list);
//        }
//        return catalogMap;

//        //缓存改写1：使用map作为本地缓存
//        Map<String, List<Catalog2Vo>> catalogMap = (Map<String, List<Catalog2Vo>>) cache.get("catalogMap");
//        if (catalogMap == null) {
//            catalogMap = getCategoriesDb();
//            cache.put("catalogMap",catalogMap);
//        }
//        return catalogMap;

//        //缓存改写2：使用redis作为本地缓存
//        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
//        String catalogJson = ops.get("catalogJson");
//        if (StringUtils.isEmpty(catalogJson)) {
//            Map<String, List<Catalog2Vo>> categoriesDb = getCategoriesDb();
//            String toJSONString = JSON.toJSONString(categoriesDb);
//            ops.set("catalogJson",toJSONString);
//            return categoriesDb;
//        }
//        Map<String, List<Catalog2Vo>> listMap = JSON.parseObject(catalogJson, new TypeReference<Map<String, List<Catalog2Vo>>>() {});
//        return listMap;

        //缓存改写3：加锁解决缓存穿透问题
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        String catalogJson = ops.get("catalogJson");
        if (StringUtils.isEmpty(catalogJson)) {
            System.out.println("缓存不命中，准备查询数据库。。。");
//            synchronized (this) {
//                String synCatalogJson = stringRedisTemplate.opsForValue().get("catalogJson");
//                if (StringUtils.isEmpty(synCatalogJson)) {
                    Map<String, List<Catalog2Vo>> categoriesDb = getCategoriesDb();
                    String toJSONString = JSON.toJSONString(categoriesDb);
                    ops.set("catalogJson", toJSONString);
                    return categoriesDb;
//                }else {
//                    Map<String, List<Catalog2Vo>> listMap = JSON.parseObject(synCatalogJson, new TypeReference<Map<String, List<Catalog2Vo>>>() {});
//                    return listMap;
//                }
//            }

        }
        System.out.println("缓存命中。。。。");
        return JSON.parseObject(catalogJson, new TypeReference<Map<String, List<Catalog2Vo>>>() {});
    }

    /**
     * 通过redis占坑来试下分布式锁
     * @return
     */
    public Map<String, List<Catalog2Vo>> getCatalogJsonDbWithRedisLock() {
        String uuid = UUID.randomUUID().toString();
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        Boolean lock = ops.setIfAbsent("lock", uuid,5, TimeUnit.SECONDS);
        if (lock) {
            Map<String, List<Catalog2Vo>> categoriesDb = getCategoryMap();
            String lockValue = ops.get("lock");
            String script = "if redis.call(\"get\",KEYS[1]) == ARGV[1] then\n" +
                    "    return redis.call(\"del\",KEYS[1])\n" +
                    "else\n" +
                    "    return 0\n" +
                    "end";
            stringRedisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class), Arrays.asList("lock"), lockValue);
            return categoriesDb;
        }else {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return getCatalogJsonDbWithRedisLock();
        }
    }

    //从数据库中查出三级分类
    //仅查询一次数据库，剩下的数据通过遍历得到并封装
    private Map<String, List<Catalog2Vo>> getCategoriesDb() {
            System.out.println("查询了数据库");
            //优化业务逻辑，仅查询一次数据库
            List<CategoryEntity> categoryEntities = this.list();
            //查出所有一级分类
            List<CategoryEntity> level1Categories = getCategoryByParentCid(categoryEntities, 0L);
        return level1Categories.stream().collect(Collectors.toMap(
                k->k.getCatId().toString(), v -> {
            //遍历查找出二级分类
            List<CategoryEntity> level2Categories = getCategoryByParentCid(categoryEntities, v.getCatId());
            List<Catalog2Vo> catalog2Vos = null;
            if (level2Categories != null){
                //封装二级分类到vo并且查出其中的三级分类
                catalog2Vos = level2Categories.stream().map(cat -> {
                    //遍历查出三级分类并封装
                    List<CategoryEntity> level3Catagories = getCategoryByParentCid(categoryEntities, cat.getCatId());
                    List<Catalog2Vo.Catalog3Vo> catalog3Vos = null;
                    if (level3Catagories != null) {
                        catalog3Vos = level3Catagories.stream().map(level3 ->
                                        new Catalog2Vo.Catalog3Vo(
                                                level3.getParentCid().toString(),
                                                level3.getCatId().toString(),
                                                level3.getName())).collect(Collectors.toList());
                    }
                    return new Catalog2Vo(
                            v.getCatId().toString(), cat.getCatId().toString(), cat.getName(), catalog3Vos);
                }).collect(Collectors.toList());
            }
            assert catalog2Vos != null;
            return catalog2Vos;
        }));
    }

    private List<CategoryEntity> getCategoryByParentCid(List<CategoryEntity> categoryEntities, long l) {
        List<CategoryEntity> collect = categoryEntities.stream().filter(cat -> cat.getParentCid() == l).collect(Collectors.toList());
        return collect;
    }

    private void findPath(Long categorygId, List<Long> path) {
        if (categorygId!=0){
            path.add(categorygId);
            CategoryEntity byId = getById(categorygId);
            findPath(byId.getParentCid(),path);
        }
    }

    private List<CategoryEntity> getChildrens(CategoryEntity categoryEntity, List<CategoryEntity> entities) {
        List<CategoryEntity> collect = entities.stream()
                .filter(item -> item.getParentCid() == categoryEntity.getCatId())
                .map(menu -> {
                    menu.setChildren(getChildrens(menu, entities));
                    return menu;
                })
                .sorted((menu1, menu2) -> {
                    return (menu1.getSort()==null?0:menu1.getSort()) - (menu2.getSort()==null?0:menu2.getSort());
                })
                .collect(Collectors.toList());
        return collect;
    }

//    public List<CategoryEntity> listWithTree(){
//        List<CategoryEntity> entities = baseMapper.selectList(null);
//        //找到所有的一级分类
//        List<CategoryEntity> level1Menus = entities.stream()
//                .filter(item -> item.getParentCid() == 0)
//                .map(menu->{
//                    menu.setChildrens(getChildrens(menu,entities));
//                    return menu;
//                })
//                .sorted((menu1, menu2) -> {
//
//                    return (menu1.getSort() ==null ? 0:menu1.getSort())- (menu2.getSort()==null?0:menu2.getSort());
//
//                })
//                .collect(Collectors.toList());
//
//
//
//        return level1Menus;
//    }
//
//    public List<CategoryEntity> getChildrens(CategoryEntity root,List<CategoryEntity> all){
//
//        List<CategoryEntity> childrens = all.stream().filter(item -> {
//            return item.getParentCid() == root.getCatId();
//        }).map(item -> {
//            item.setChildrens(getChildrens(item, all));
//            return item;
//        }).sorted((menu1, menu2) -> {
//            return (menu1.getSort() ==null ? 0:menu1.getSort())- (menu2.getSort()==null?0:menu2.getSort());
//        }).collect(Collectors.toList());
//
//        return childrens;
//    }
}

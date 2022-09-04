package io.niceseason.common.to.es;


import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

//SPU是商品信息聚合的最小单位，
// 是一组可复用、易检索的标准化信息的集合，该集合描述了一个产品的特性。
// 通俗点讲，属性值、特性相同的商品就可以称为一个SPU。
// SKU即库存进出计量的单位， 可以是以件、盒、托盘等为单位。
// SKU是物理上不可分割的最小存货单元。 在使用时要根据不同业态，不同管理模式来处理。
//商品上架需要在es中保存spu信息并更新spu的状态信息，
// 由于SpuInfoEntity与索引的数据模型并不对应，所以我们要建立专门的vo进行数据传输
@Data
public class SkuEsModel {
    private Long skuId;
    private Long spuId;
    private String skuTitle;
    private BigDecimal skuPrice;
    private String skuImg;
    private Long saleCount;
    private boolean hasStock;
    private Long hotScore;
    private Long brandId;
    private Long catalogId;
    private String brandName;
    private String brandImg;
    private String catalogName;
    private List<Attr> attrs;

    @Data
    public static class Attr{
        private Long attrId;
        private String attrName;
        private String attrValue;
    }
}

package io.niceseason.gulimall.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import io.niceseason.common.to.es.SkuEsModel;
import io.niceseason.common.utils.R;
import io.niceseason.gulimall.search.config.GulimallElasticSearchConfig;
import io.niceseason.gulimall.search.constant.EsConstant;
import io.niceseason.gulimall.search.feign.ProductFeignService;
import io.niceseason.gulimall.search.service.SearchService;
import io.niceseason.gulimall.search.vo.AttrResponseVo;
import io.niceseason.gulimall.search.vo.SearchParam;
import io.niceseason.gulimall.search.vo.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service("SearchService")
public class SearchServiceImpl implements SearchService {
    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private ProductFeignService productFeignService;

    @Override
    public SearchResult getSearchResult(SearchParam searchParam) {
        SearchResult searchResult= null;
        //????????????????????????????????????
        SearchRequest request = bulidSearchRequest(searchParam);
        try {
            SearchResponse searchResponse = restHighLevelClient.search(request, GulimallElasticSearchConfig.COMMON_OPTIONS);
            //???es???????????????????????????
            searchResult = bulidSearchResult(searchParam, searchResponse);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return searchResult;
    }

    // ??????????????????
    private SearchResult bulidSearchResult(SearchParam searchParam, SearchResponse searchResponse) {
        SearchResult result = new SearchResult();
        SearchHits hits = searchResponse.getHits();
        //1. ??????????????????????????????
        if (hits.getHits() != null && hits.getHits().length > 0){
            List<SkuEsModel> skuEsModels = new ArrayList<>();
            for (SearchHit hit : hits) {
                String sourceAsString = hit.getSourceAsString();
                SkuEsModel skuEsModel = JSON.parseObject(sourceAsString, SkuEsModel.class);
                //??????????????????
                if (!StringUtils.isEmpty(searchParam.getKeyword())) {
                    HighlightField skuTitle = hit.getHighlightFields().get("skuTitle");
                    String highLight = skuTitle.getFragments()[0].string();
                    skuEsModel.setSkuTitle(highLight);
                }
                skuEsModels.add(skuEsModel);
            }
            result.setProduct(skuEsModels);
        }

        //2. ??????????????????
        //2.1 ????????????
        result.setPageNum(searchParam.getPageNum());
        //2.2 ????????????
        long total = hits.getTotalHits().value;
        result.setTotal(total);
        //2.3 ?????????
        int totalPages = (int)total % EsConstant.PRODUCT_PAGESIZE == 0 ?
                (int)total / EsConstant.PRODUCT_PAGESIZE : (int)total / EsConstant.PRODUCT_PAGESIZE + 1;
        result.setTotalPages(totalPages);
        List<Integer> pageNavs = new ArrayList<>();
        for (int i = 1; i <= totalPages; i++) {
            pageNavs.add(i);
        }
        result.setPageNavs(pageNavs);

        //3. ??????????????????????????????
        List<SearchResult.BrandVo> brandVos = new ArrayList<>();
        Aggregations aggregations = searchResponse.getAggregations();
        //ParsedLongTerms????????????terms?????????????????????????????????key?????????Long???????????????
        ParsedLongTerms brandAgg = aggregations.get("brandAgg");
        for (Terms.Bucket bucket : brandAgg.getBuckets()) {
            //3.1 ????????????id
            Long brandId = bucket.getKeyAsNumber().longValue();

            Aggregations subBrandAggs = bucket.getAggregations();
            //3.2 ??????????????????
            ParsedStringTerms brandImgAgg = subBrandAggs.get("brandImgAgg");
            String brandImg = brandImgAgg.getBuckets().get(0).getKeyAsString();
            //3.3 ??????????????????
            Terms brandNameAgg = subBrandAggs.get("brandNameAgg");
            String brandName = brandNameAgg.getBuckets().get(0).getKeyAsString();
            SearchResult.BrandVo brandVo = new SearchResult.BrandVo(brandId, brandName, brandImg);
            brandVos.add(brandVo);
        }
        result.setBrands(brandVos);

        //4. ??????????????????????????????
        List<SearchResult.CatalogVo> catalogVos = new ArrayList<>();
        ParsedLongTerms catalogAgg = aggregations.get("catalogAgg");
        for (Terms.Bucket bucket : catalogAgg.getBuckets()) {
            //4.1 ????????????id
            Long catalogId = bucket.getKeyAsNumber().longValue();
            Aggregations subcatalogAggs = bucket.getAggregations();
            //4.2 ???????????????
            ParsedStringTerms catalogNameAgg = subcatalogAggs.get("catalogNameAgg");
            String catalogName = catalogNameAgg.getBuckets().get(0).getKeyAsString();
            SearchResult.CatalogVo catalogVo = new SearchResult.CatalogVo(catalogId, catalogName);
            catalogVos.add(catalogVo);
        }
        result.setCatalogs(catalogVos);

        //5 ??????????????????????????????
        List<SearchResult.AttrVo> attrVos = new ArrayList<>();
        //ParsedNested?????????????????????????????????
        ParsedNested parsedNested = aggregations.get("attrs");
        ParsedLongTerms attrIdAgg = parsedNested.getAggregations().get("attrIdAgg");
        for (Terms.Bucket bucket : attrIdAgg.getBuckets()) {
            //5.1 ????????????id
            Long attrId = bucket.getKeyAsNumber().longValue();

            Aggregations subAttrAgg = bucket.getAggregations();
            //5.2 ???????????????
            ParsedStringTerms attrNameAgg = subAttrAgg.get("attrNameAgg");
            String attrName = attrNameAgg.getBuckets().get(0).getKeyAsString();
            //5.3 ???????????????
            ParsedStringTerms attrValueAgg = subAttrAgg.get("attrValueAgg");
            List<String> attrValues = new ArrayList<>();
            for (Terms.Bucket attrValueAggBucket : attrValueAgg.getBuckets()) {
                String attrValue = attrValueAggBucket.getKeyAsString();
                attrValues.add(attrValue);
            }
            SearchResult.AttrVo attrVo = new SearchResult.AttrVo(attrId, attrName, attrValues);
            attrVos.add(attrVo);
        }
        result.setAttrs(attrVos);

        // 6. ?????????????????????
        List<String> attrs = searchParam.getAttrs();
        if (attrs != null && attrs.size() > 0) {
            List<SearchResult.NavVo> navVos = attrs.stream().map(attr -> {
                String[] split = attr.split("_");
                SearchResult.NavVo navVo = new SearchResult.NavVo();
                //6.1 ???????????????
                navVo.setNavValue(split[1]);
                //6.2 ????????????????????????
                try {
                    R r = productFeignService.info(Long.parseLong(split[0]));
                    if (r.getCode() == 0) {
                        AttrResponseVo attrResponseVo = JSON.parseObject(JSON.toJSONString(r.get("attr")),
                                new TypeReference<AttrResponseVo>() {});
                        navVo.setNavName(attrResponseVo.getAttrName());
                    }
                } catch (Exception e) {
                    log.error("??????????????????????????????????????????", e);
                }
                //6.3 ???????????????????????????
                String queryString = searchParam.get_queryString();
                String replace = queryString.replace("&attrs=" + attr, "").replace(
                        "attrs=" + attr+"&", "").replace("attrs=" + attr, "");
                navVo.setLink("https://search.gulimall.com/search.html" + (replace.isEmpty() ? "" : "?" + replace));
                return navVo;
            }).collect(Collectors.toList());
            result.setNavs(navVos);
        }
        return result;
    }


    // ??????????????????,?????????????????????????????????
    private SearchRequest bulidSearchRequest(SearchParam searchParam) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        //1. ??????bool query
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        //1.1 bool must
        if (!StringUtils.isEmpty(searchParam.getKeyword())) {
            boolQueryBuilder.must(QueryBuilders.matchQuery("skuTitle", searchParam.getKeyword()));
        }

        //1.2 bool filter
        //1.2.1 catalog
        if (searchParam.getCatalog3Id() != null){
            boolQueryBuilder.filter(QueryBuilders.termQuery("catalogId", searchParam.getCatalog3Id()));
        }
        //1.2.2 brand
        if (searchParam.getBrandId() !=null && searchParam.getBrandId().size() > 0) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", searchParam.getBrandId()));
        }
        //1.2.3 hasStock
        if (searchParam.getHasStock() != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("hasStock", searchParam.getHasStock() == 1));
        }
        //1.2.4 priceRange
        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("skuPrice");
        if (!StringUtils.isEmpty(searchParam.getSkuPrice())) {
            String[] prices = searchParam.getSkuPrice().split("_");
            if (prices.length == 1) {
                if (searchParam.getSkuPrice().startsWith("_")) {
                    rangeQueryBuilder.lte(Integer.parseInt(prices[0]));
                }else {
                    rangeQueryBuilder.gte(Integer.parseInt(prices[0]));
                }
            } else if (prices.length == 2) {
                //_6000????????????["","6000"]
                if (!prices[0].isEmpty()) {
                    rangeQueryBuilder.gte(Integer.parseInt(prices[0]));
                }
                rangeQueryBuilder.lte(Integer.parseInt(prices[1]));
            }
            boolQueryBuilder.filter(rangeQueryBuilder);
        }
        //1.2.5 attrs-nested
        //attrs=1_5???:8???&2_16G:8G
        List<String> attrs = searchParam.getAttrs();
        BoolQueryBuilder queryBuilder = new BoolQueryBuilder();
        if (attrs !=null && attrs.size() > 0) {
            attrs.forEach(attr -> {
                String[] attrSplit = attr.split("_");
                queryBuilder.must(QueryBuilders.termQuery("attrs.attrId", attrSplit[0]));
                String[] attrValues = attrSplit[1].split(":");
                queryBuilder.must(QueryBuilders.termsQuery("attrs.attrValue", attrValues));
            });
        }
        NestedQueryBuilder nestedQueryBuilder = QueryBuilders.nestedQuery("attrs", queryBuilder, ScoreMode.None);
        boolQueryBuilder.filter(nestedQueryBuilder);
        //1. bool query????????????
        searchSourceBuilder.query(boolQueryBuilder);

        //2. sort  eg:sort=saleCount_desc/asc
        if (!StringUtils.isEmpty(searchParam.getSort())) {
            String[] sortSplit = searchParam.getSort().split("_");
            searchSourceBuilder.sort(sortSplit[0],
                    sortSplit[1].equalsIgnoreCase("asc") ? SortOrder.ASC : SortOrder.DESC);
        }

        //3. ??????
        searchSourceBuilder.from((searchParam.getPageNum() - 1) * EsConstant.PRODUCT_PAGESIZE);
        searchSourceBuilder.size(EsConstant.PRODUCT_PAGESIZE);

        //4. ??????highlight
        if (!StringUtils.isEmpty(searchParam.getKeyword())) {
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            highlightBuilder.field("skuTitle");
            highlightBuilder.preTags("<b style='color:red'>");
            highlightBuilder.postTags("</b>");
            searchSourceBuilder.highlighter(highlightBuilder);
        }

        //5. ??????
        //5.1 ??????brand??????
        TermsAggregationBuilder brandAgg = AggregationBuilders.terms("brandAgg").field("brandId");
        TermsAggregationBuilder brandNameAgg = AggregationBuilders.terms("brandNameAgg").field("brandName");
        TermsAggregationBuilder brandImgAgg = AggregationBuilders.terms("brandImgAgg").field("brandImg");
        brandAgg.subAggregation(brandNameAgg);
        brandAgg.subAggregation(brandImgAgg);
        searchSourceBuilder.aggregation(brandAgg);

        //5.2 ??????catalog??????
        TermsAggregationBuilder catalogAgg = AggregationBuilders.terms("catalogAgg").field("catalogId");
        TermsAggregationBuilder catalogNameAgg = AggregationBuilders.terms("catalogNameAgg").field("catalogName");
        catalogAgg.subAggregation(catalogNameAgg);
        searchSourceBuilder.aggregation(catalogAgg);

        //5.3 ??????attrs??????
        NestedAggregationBuilder nestedAggregationBuilder = new NestedAggregationBuilder("attrs", "attrs");
        //??????attrId??????
        TermsAggregationBuilder attrIdAgg = AggregationBuilders.terms("attrIdAgg").field("attrs.attrId");
        //??????attrId?????????????????????attrName???attrValue??????
        TermsAggregationBuilder attrNameAgg = AggregationBuilders.terms("attrNameAgg").field("attrs.attrName");
        TermsAggregationBuilder attrValueAgg = AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue");
        attrIdAgg.subAggregation(attrNameAgg);
        attrIdAgg.subAggregation(attrValueAgg);

        nestedAggregationBuilder.subAggregation(attrIdAgg);
        searchSourceBuilder.aggregation(nestedAggregationBuilder);

        log.debug("?????????DSL?????? {}", searchSourceBuilder);

        return new SearchRequest(new String[]{EsConstant.PRODUCT_INDEX}, searchSourceBuilder);
    }
}

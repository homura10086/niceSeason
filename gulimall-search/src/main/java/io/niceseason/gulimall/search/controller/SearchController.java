package io.niceseason.gulimall.search.controller;

import io.niceseason.gulimall.search.service.SearchService;
import io.niceseason.gulimall.search.vo.SearchParam;
import io.niceseason.gulimall.search.vo.SearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
public class SearchController {

    @Autowired
    private SearchService searchService;

//    主要逻辑在service层进行，service层将封装好的SearchParam组建查询条件，再将返回后的结果封装成SearchResult
    @GetMapping(value = {"/search.html","/"})
    public String getSearchPage(SearchParam searchParam, Model model, HttpServletRequest request) {
        searchParam.set_queryString(request.getQueryString());
        SearchResult result = searchService.getSearchResult(searchParam);
        model.addAttribute("result", result);
        return "search";
    }
}

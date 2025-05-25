package com.example.demo.controller;

import com.example.demo.service.DataFetchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataFetchController {

    @Autowired
    private DataFetchService dataFetchService;

    @GetMapping("/fetchData")
    public String fetchData(
        @RequestParam(name = "startPage", defaultValue = "1") int startPage,
        @RequestParam(name = "endPage", defaultValue = "4") int endPage
    ) {
        dataFetchService.fetchData(startPage, endPage);
        return "数据获取完成";
    }
}
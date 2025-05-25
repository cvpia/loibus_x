package com.example.demo.controller;

import com.example.demo.service.ItemDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ItemDetailController {

    @Autowired
    private ItemDetailService itemDetailService;

    @GetMapping("/processItems")
    public String processItems(
        @RequestParam(name = "startId", defaultValue = "1") Long startId,
        @RequestParam(name = "endId", defaultValue = "6000") Long endId
    ) {
        itemDetailService.processItems(startId, endId);
        return "项目处理完成";
    }
}
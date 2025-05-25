package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.demo.service.DownloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@RestController
public class DownloadController {

    private static final Logger log = LoggerFactory.getLogger(DownloadController.class);
    @Autowired
    private  DownloadService downloadService;
    
    @GetMapping("/down")    
    public String download(@RequestParam(name = "startId", defaultValue = "1") Long 
                                            startId,@RequestParam(name = "endId", defaultValue = "6000") Long endId) {
        log.info("接收到下单范围任务，开始id: {}，结束id: {}", startId);

            downloadService.processItems(startId,endId);
        
        return ("执行下载任务"+startId+"到"+endId);
    }

    @GetMapping("/iddown")    
    public String iddown(@RequestParam(name = "startId", defaultValue = "1") Long 
                                            startId) {
        log.info("接收到下单范围任务，开始id: {}，结束id: {}", startId);

            downloadService.downloadItem(startId);
        
        return ("执行下载任务"+startId);
    }

}


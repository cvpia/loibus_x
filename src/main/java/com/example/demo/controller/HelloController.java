package com.example.demo.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.TokenService;
import com.example.demo.service.UserService;


@RestController
public class HelloController {
    private static final Logger logger = LogManager.getLogger(HelloController.class);



    @GetMapping("/hello")
    public String sayHello() {
        return "Hello, Loibus的工作目录在："+System.getProperty("user.dir");
    }
}
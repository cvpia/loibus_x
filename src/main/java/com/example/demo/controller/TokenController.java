package com.example.demo.controller;

import com.example.demo.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TokenController {

    @Autowired
    private TokenService tokenService;

    @GetMapping("/getToken")
    public String getToken() {
        System.out.println("开始执行getToken()...");
        String token = tokenService.getToken();
        System.out.println("获取到的token: " + token);
        return token;
    }
}
package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class cardController {

    @GetMapping("/card")
    public String card() {
        return "card";
    }
    @GetMapping("/api")
    public String api() {
        System.out.println("api-demo");
        return "api-demo";
    }
}

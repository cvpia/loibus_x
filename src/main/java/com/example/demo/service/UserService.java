package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    @Value("${api.user.info.url}")
    private String userInfoUrl;

    @Value("${token.file.path}")
    private String tokenFilePath;

    private final TokenService tokenService;

    public UserService(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public String loadToken() {
        try {
            // 读取token文件
            ObjectMapper mapper = new ObjectMapper();
            File tokenFile = new File(tokenFilePath);
            
            if (tokenFile.exists()) {
                Map<String, Object> tokenData = mapper.readValue(tokenFile, Map.class);
                System.out.println("获取到本地token啦");
                return (String) tokenData.get("token");
            } else {
                System.out.println("token文件不存在");
                return null;
            }
        } catch (Exception e) {
            System.out.println("加载token失败: " + e.getMessage());
            return null;
        }
    }

    public Map<String, Object> getUserInfo() {
        // 加载token
        String token = loadToken();
        
        if (token == null) {
            System.out.println("未找到有效token，请先获取token");
            // 尝试自动获取新token
            token = tokenService.getToken();
            if (token == null) {
                return null;
            }
        }

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<?> request = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            System.out.println("正在获取用户信息...");
            
            // 发送请求
            ResponseEntity<Map> response = restTemplate.exchange(
                userInfoUrl,
                HttpMethod.GET,
                request,
                Map.class
            );

            System.out.println("收到响应，状态码: " + response.getStatusCodeValue());

            // 处理响应
            Map<String, Object> userData = response.getBody();
            
            // 打印用户信息
            printUserInfo(userData);
            
            return userData;

        } catch (HttpClientErrorException e) {
            System.out.println("HTTP错误: " + e.getStatusCode());
            System.out.println("错误详情: " + e.getResponseBodyAsString());
            
            // 如果是token过期，尝试刷新token并重试
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                System.out.println("Token已过期，正在获取新token...");
                token = tokenService.getToken();
                if (token != null) {
                    return getUserInfo();
                }
            }
            
            return null;
        } catch (Exception e) {
            System.out.println("获取用户信息失败: " + e.getMessage());
            return null;
        }
    }

    private void printUserInfo(Map<String, Object> userData) {
        if (userData != null && userData.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) userData.get("data");
            
            System.out.println("\n用户详细信息:");
            System.out.println("ID: " + data.get("id"));
            System.out.println("用户名: " + data.get("username"));
            System.out.println("邮箱: " + data.get("email"));
            System.out.println("VIP等级: " + data.get("vip_id"));
            System.out.println("到期时间: " + data.get("expiry_at"));
        }
    }
}
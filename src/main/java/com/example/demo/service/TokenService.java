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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Service
public class TokenService {
    private static final Logger logger = LogManager.getLogger(TokenService.class);

    @Value("${api.login.url}")
    private String loginUrl;

    @Value("${api.email:}")
    private String email;

    @Value("${api.password:}")
    private String password;

    @Value("${token.file.path}")
    private String tokenFilePath;

    public String getToken() {
        logger.info("正在发送登录请求...");

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 设置请求体
        Map<String, String> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("password", password);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            // 发送登录请求
            ResponseEntity<Map> response = restTemplate.exchange(
                loginUrl,
                HttpMethod.POST,
                request,
                Map.class
            );

            logger.info("收到响应，状态码: {}", response.getStatusCodeValue());

            // 处理响应
            Map<String, Object> data = response.getBody();
            logger.debug("登录响应JSON: {}", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(data));

            // 获取token
            String token = (String) data.get("access_token");
            if (token == null) {
                logger.warn("警告: 响应中未找到token字段");
                throw new IllegalArgumentException("响应中未找到token字段");
            }

            // 保存token到文件
            saveTokenToFile(token, data);
            logger.info("token已保存到: {}", tokenFilePath);

            return token;

        } catch (HttpClientErrorException e) {
            logger.error("HTTP错误: {}", e.getStatusCode());
            logger.error("错误详情: {}", e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            logger.error("获取token失败", e);
            return null;
        }
    }

    private void saveTokenToFile(String token, Map<String, Object> data) throws IOException {
        // 创建保存token的Map
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("token", token);
        tokenData.put("data", data);

        // 写入JSON文件
        ObjectMapper mapper = new ObjectMapper();
        File tokenFile = new File(tokenFilePath);
        mapper.writerWithDefaultPrettyPrinter().writeValue(tokenFile, tokenData);
    }
}
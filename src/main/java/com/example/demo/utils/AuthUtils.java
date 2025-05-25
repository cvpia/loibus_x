package com.example.demo.utils;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AuthUtils {

	public static void main(String[] args) {
        getToken(new RestTemplate()); // 示例用法，实际使用时应根据实际情况进行调整和补充
    }

    private static final Logger logger = LogManager.getLogger(AuthUtils.class);
    private static final String LOGIN_URL = "https://api.moegoat.com/api/user/login";
    private static final String TOKEN_FILE = "token.json";
    
    public static String getToken(RestTemplate restTemplate) {
        try {
            logger.info("正在发送登录请求...");
            
            // 如果传入的RestTemplate为null，则创建一个新的实例
            if (restTemplate == null) {
                logger.info("RestTemplate为null，创建新实例");
                restTemplate = new RestTemplate();
            }
            
            Map<String, String> payload = new HashMap<>();
            payload.put("email", "1977791013@qq.com");
            payload.put("password", "5201314.");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(LOGIN_URL, request, String.class);
            logger.info("收到响应，状态码: {}", response.getStatusCodeValue());
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("HTTP请求失败: " + response.getStatusCodeValue());
            }
            
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> data = mapper.readValue(response.getBody(), Map.class);
            
            logger.debug("登录响应JSON: {}", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
            
            String token = (String) data.get("access_token");
            if (token == null) {
                logger.warn("警告: 响应中未找到token字段");
                throw new RuntimeException("响应中未找到token字段");
            }
            
            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("token", token);
            tokenData.put("data", data);
            
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(TOKEN_FILE), tokenData);
            logger.info("token已保存到: {}", TOKEN_FILE);
            logger.info("token:"+tokenData.toString());
            
            return token;
            
        } catch (Exception e) {
            logger.error("获取token失败: {}", e.getMessage());
            return null;
        }
    }
}
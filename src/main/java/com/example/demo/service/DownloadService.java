package com.example.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class DownloadService {
    private static final Logger logger = LoggerFactory.getLogger(DownloadService.class);

    @Value("${base.dir}")
    private String baseDir;

    @Value("${api.download.url}")
    private String downloadApiUrl;

    private final String AES_KEY = "7811595744111520";  // 16字节密钥
    private final String AES_IV = "RgaXNyE5aWckBwI7";   // 16字节初始向量

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    // AES解密函数
    public String aesDecrypt(String encryptedData) {
        try {
            byte[] keyBytes = AES_KEY.getBytes(StandardCharsets.UTF_8);
            byte[] ivBytes = AES_IV.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec iv = new IvParameterSpec(ivBytes);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, iv);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("AES解密失败", e);
            return encryptedData;
        }
    }

    // 检查项目是否已下载（仅检查JSON文件）
    public boolean isDownloaded(Long itemId) {
        String jsonPath = Paths.get(baseDir, "down", itemId + ".json").toString();
        return Files.exists(Paths.get(jsonPath));
    }

    // 发送预检请求（带重试机制）
    public boolean sendPreflightRequest(String url, int maxRetries) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Origin", "https://www.loibus006.top");
                headers.set("Access-Control-Request-Method", "GET");
                headers.set("Access-Control-Request-Headers", "authorization,content-type");

                ResponseEntity<Void> response = restTemplate.exchange(
                    url, HttpMethod.OPTIONS, new HttpEntity<>(headers), Void.class
                );

                return response.getStatusCode() == HttpStatus.NO_CONTENT;

            } catch (Exception e) {
                if (attempt < maxRetries - 1) {
                    try { TimeUnit.SECONDS.sleep(2); } catch (InterruptedException ignored) {}
                }
            }
        }
        logger.error("预检请求失败: 达到最大重试次数");
        return false;
    }

    // 获取下载信息（仅保存JSON，去除图片下载）
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDownloadInfo(Long itemId, int maxRetries) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            if (isDownloaded(itemId)) {
                logger.info("项目 {} 已存在，跳过下载", itemId);
                return null;
            }

            String token = userService.loadToken();
            if (token == null) {
                logger.error("未获取到token");
                return null;
            }

            String url = downloadApiUrl + "/" + itemId;



            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/134.0.0.0 Safari/537.36");

            try {
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class
                );

                if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    logger.error("认证失败");
                    if (attempt < maxRetries - 1) {
                        try { TimeUnit.SECONDS.sleep(2); } catch (InterruptedException ignored) {}
                    }
                    continue;
                }

                Map<String, Object> data = response.getBody();
                if (!"success".equals(data.get("status"))) {
                    logger.error("错误：{}", data.getOrDefault("message", "未知错误"));
                    if (attempt < maxRetries - 1) {
                        try { TimeUnit.SECONDS.sleep(2); } catch (InterruptedException ignored) {}
                    }
                    continue;
                }

                // 解密数据
                Map<String, Object> resultData = (Map<String, Object>) data.get("data");
                decryptData(resultData);

                // 保存JSON文件
                saveJsonData(itemId, data);

                logger.info("下载信息已保存至 {}", getJsonPath(itemId));
                return resultData;

            } catch (Exception e) {
                logger.error("获取下载信息失败(尝试 {}/{}): {}", attempt + 1, maxRetries, e.getMessage());
                if (attempt < maxRetries - 1) {
                    try { TimeUnit.SECONDS.sleep(2); } catch (InterruptedException ignored) {}
                }
            }
        }
        return null;
    }

    // 执行下载（仅处理JSON）
    public boolean downloadItem(Long itemId) {
        if (isDownloaded(itemId)) {
            logger.info("项目 {} 已存在，跳过下载", itemId);
            return true;
        }

        Map<String, Object> downloadInfo = getDownloadInfo(itemId, 3);
        if (downloadInfo == null) {
            logger.error("无法获取下载信息{}",downloadApiUrl+"/"+itemId);
            return false;
        }

        return true;
    }

    // 批量处理项目
    public void processItems(Long startId, Long endId) {
        for (long itemId = startId; itemId <= endId; itemId++) {
            logger.info("==== 处理项目 {} ====", itemId);
            
            // 假设get_item_detail已实现（此处可根据实际情况调用）
            // if (!getItemDetail(itemId)) {
            //     System.out.println("详情获取失败，跳过");
            //     continue;
            // }

            if (downloadItem(itemId)) {
                logger.info("项目 {} 处理成功", itemId);
            } else {
                logger.error("项目 {} 处理失败", itemId);
            }

            try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException ignored) {}
        }
        logger.info("所有项目处理完成");
    }

    // 辅助方法：解密数据字段
    private void decryptData(Map<String, Object> data) {
        if (data == null) return;
        data.put("bdp", aesDecrypt((String) data.getOrDefault("bdp", "")));
        data.put("bdp_key", aesDecrypt((String) data.getOrDefault("bdp_key", "")));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> svList = (List<Map<String, Object>>) data.get("sv_list");
        if (svList != null) {
            svList.forEach(server -> 
                server.put("sv_url", aesDecrypt((String) server.getOrDefault("sv_url", "")))
            );
        }
    }

    // 辅助方法：保存JSON文件
    private void saveJsonData(Long itemId, Map<String, Object> data) throws IOException {
        String saveDir = Paths.get(baseDir, "down").toString();
        Files.createDirectories(Paths.get(saveDir));
        String jsonPath = getJsonPath(itemId);
        try (OutputStream os = new FileOutputStream(jsonPath)) {
            objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(os, data);
        }
    }

    // 辅助方法：获取JSON文件路径
    private String getJsonPath(Long itemId) {
        return Paths.get(baseDir, "down", itemId + ".json").toString();
    }


}
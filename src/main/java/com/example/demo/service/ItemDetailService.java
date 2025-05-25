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
import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ItemDetailService {
    private static final Logger logger = LoggerFactory.getLogger(ItemDetailService.class);

    @Value("${base.dir}")
    private String baseDir;

    @Value("${api.item.detail.url}")
    private String itemDetailApiUrl;

    @Value("${request.max.retries}")
    private int maxRetries;

    @Value("${request.wait.time}")
    private long requestWaitTime;

    @Value("${error.wait.time}")
    private long errorWaitTime;

    private final UserService userService;

    @Autowired
    public ItemDetailService(UserService userService) {
        this.userService = userService;
    }

    public List<String> downloadItemImages(Long itemId, List<String> imageUrls) {
        try {
            // 创建保存目录
            String saveDir = Paths.get(baseDir, "items", itemId.toString(), "images").toString();
            Files.createDirectories(Paths.get(saveDir));

            List<String> downloaded = new ArrayList<>();
            for (String url : imageUrls) {
                int retryCount = 0;
                boolean success = false;
                
                while (retryCount < maxRetries && !success) {
                    try {
                        // 解析文件名
                        String filename = URLDecoder.decode(url.substring(url.lastIndexOf('/') + 1), StandardCharsets.UTF_8);
                        String savePath = Paths.get(saveDir, filename).toString();

                        // 检查文件是否已存在
                        File file = new File(savePath);
                        if (file.exists()) {
                            downloaded.add(savePath);
                            success = true;
                            break;
                        }

                        // 下载图片
                        try (InputStream in = new URL(url).openStream();
                        OutputStream out = new FileOutputStream(savePath)) {

                            byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                        }

                        downloaded.add(savePath);
                        success = true;
                    } catch (Exception e) {
                        retryCount++;
                        if (retryCount >= maxRetries) {
                            logger.error("图片下载失败(尝试{}次)", maxRetries, e);
                        } else {
                            // 等待2秒后重试
                            TimeUnit.SECONDS.sleep(2);
                        }
                    }
                }
            }

            return downloaded;
        } catch (Exception e) {
            logger.error("创建下载目录失败", e);
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public String saveItemData(Long itemId, Map<String, Object> itemData) {
        try {
            // 创建保存目录
            String saveDir = Paths.get(baseDir, "items", itemId.toString()).toString();
            Files.createDirectories(Paths.get(saveDir));

            // 更新images字段为相对路径
            if (itemData.containsKey("images") && itemData.get("images") != null) {
                List<String> imageUrls = (List<String>) itemData.get("images");
                List<String> relativePaths = imageUrls.stream()
                    .map(url -> Paths.get("images", URLDecoder.decode(url.substring(url.lastIndexOf('/') + 1), StandardCharsets.UTF_8)).toString())
                    .collect(Collectors.toList());
                itemData.put("images", relativePaths);
            }

            // 保存JSON数据
            String jsonFile = Paths.get(saveDir, "data.json").toString();
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(jsonFile), itemData);

            return jsonFile;
        } catch (Exception e) {
            logger.error("保存数据失败", e);
            return null;
        }
    }

    public boolean getItemDetail(Long itemId) {
        // 获取token
        String token = userService.loadToken();
        if (token == null) {
            logger.warn("未获取到有效token");
            return false;
        }

        String url = itemDetailApiUrl + "/" + itemId;
        
        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36");

        HttpEntity<?> request = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        for (int retryCount = 0; retryCount < maxRetries; retryCount++) {
            try {
                // 发送请求
                ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    Map.class
                );

                Map<String, Object> data = response.getBody();
                if (data == null || !"success".equals(data.get("status"))) {
                    String message = data != null ? (String) data.getOrDefault("message", "未知错误") : "未知错误";
                    logger.error("获取详情失败: {}", message);
                    return false;
                }

                // 下载图片
                @SuppressWarnings("unchecked")
                Map<String, Object> itemData = (Map<String, Object>) data.get("data");
                if (itemData != null && itemData.containsKey("images")) {
                    @SuppressWarnings("unchecked")
                    List<String> images = (List<String>) itemData.get("images");
                    if (images != null && !images.isEmpty()) {
                        List<String> downloaded = downloadItemImages(itemId, images);
                        logger.info("下载完成 {}/{} 张图片", downloaded.size(), images.size());
                    }
                }

                // 保存JSON数据
                String jsonPath = saveItemData(itemId, itemData);
                if (jsonPath != null) {
                    logger.info("项目数据已保存至: {}", jsonPath);
                }

                return true;
            } catch (HttpClientErrorException e) {
                logger.error("HTTP错误: {}", e.getStatusCode());
                logger.error("错误详情: {}", e.getResponseBodyAsString());
                return false;
            } catch (Exception e) {
                if (retryCount >= maxRetries - 1) {
                    logger.error("获取详情失败(尝试{}次)", maxRetries, e);
                    return false;
                } else {
                    // 指数退避策略
                    long waitTime = (long) (0.01 * Math.pow(2, retryCount));
                    logger.warn("请求失败，等待{}秒后重试...", waitTime);
                    try {
                        TimeUnit.SECONDS.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        return false;
    }

    public Set<Long> getExistingIds() {
        Path itemsDir = Paths.get(baseDir, "items");
        if (!Files.exists(itemsDir)) {
            return Collections.emptySet();
        }

        try {
            return Files.list(itemsDir)
                .filter(Files::isDirectory)
                .map(path -> {
                    try {
                        return Long.parseLong(path.getFileName().toString());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        } catch (IOException e) {
            logger.error("获取已存在项目ID失败: " + e.getMessage());
            return Collections.emptySet();
        }
    }

    public void processItems(Long startId, Long endId) {
        // 获取已存在的项目ID
        Set<Long> existingIds = getExistingIds();
        System.out.println("已存在 " + existingIds.size() + " 个项目");

        for (long itemId = startId; itemId <= endId; itemId++) {
            if (existingIds.contains(itemId)) {
                System.out.println("项目 " + itemId + " 已存在，跳过");
                continue;
            }

            try {
                System.out.println("正在获取项目 " + itemId + "...");
                boolean success = getItemDetail(itemId);
                if (success) {
                    System.out.println("项目 " + itemId + " 获取成功");
                    existingIds.add(itemId); // 更新已存在ID集合
                } else {
                    logger.warn("项目 " + itemId + " 获取失败");
                }

                // 添加请求间隔，避免被封禁
                TimeUnit.MILLISECONDS.sleep(requestWaitTime);
            } catch (Exception e) {
                try {
                    // 出错时延长等待时间
                    TimeUnit.MILLISECONDS.sleep(errorWaitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }

        }
    }
}
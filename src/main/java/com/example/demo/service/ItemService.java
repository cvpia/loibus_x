package com.example.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
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
public class ItemService {
    private static final Logger logger = LoggerFactory.getLogger(ItemService.class);

    private final String baseDir; // 项目根目录自动获取
    private final UserService userService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ItemService(UserService userService, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.userService = userService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        // 自动获取项目根目录（运行时工作目录）
        this.baseDir = System.getProperty("user.dir");
    }

    /**
     * 下载项目图片（带重试机制）
     * @param itemId 项目ID
     * @param imageUrls 图片URL列表
     * @param maxRetries 最大重试次数
     * @return 下载成功的本地路径列表
     */
    public List<String> downloadItemImages(Long itemId, List<String> imageUrls, int maxRetries) {
        List<String> downloaded = new ArrayList<>();
        String saveDir = Paths.get(baseDir, "items", itemId.toString(), "images").toString();
        
        try {
            Files.createDirectories(Paths.get(saveDir)); // 自动创建目录
            
            for (String url : imageUrls) {
                int retryCount = 0;
                while (retryCount < maxRetries) {
                    try {
                        String filename = URLDecoder.decode(url.substring(url.lastIndexOf('/') + 1), StandardCharsets.UTF_8);
                        String savePath = Paths.get(saveDir, filename).toString();
                        
                        if (Files.exists(Paths.get(savePath))) { // 检查文件是否存在
                            downloaded.add(savePath);
                            break;
                        }
                        
                        // 流式下载图片
                        try (InputStream in = new URL(url).openStream();
                             OutputStream out = new FileOutputStream(savePath)) {
                                
                            byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                        }
                        
                        downloaded.add(savePath);
                        break;
                        
                    } catch (Exception e) {
                        retryCount++;
                        if (retryCount >= maxRetries) {
                            logger.error("图片下载失败(尝试{}次)", maxRetries, e);
                        } else {
                            TimeUnit.SECONDS.sleep(2); // 重试等待2秒
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("创建下载目录失败", e);
        }
        return downloaded;
    }

    /**
     * 保存项目JSON数据（自动处理相对路径）
     * @param itemId 项目ID
     * @param itemData 项目数据
     * @return 保存成功的JSON文件路径
     */
    public String saveItemData(Long itemId, Map<String, Object> itemData) {
        String saveDir = Paths.get(baseDir, "items", itemId.toString()).toString();
        
        try {
            Files.createDirectories(Paths.get(saveDir)); // 自动创建目录
            
            // 处理图片相对路径
            if (itemData.containsKey("images")) {
                @SuppressWarnings("unchecked")
                List<String> imageUrls = (List<String>) itemData.get("images");
                
                List<String> relativePaths = imageUrls.stream()
                    .map(url -> {
                        String filename = URLDecoder.decode(url.substring(url.lastIndexOf('/') + 1), StandardCharsets.UTF_8);
                        return Paths.get("images", filename).toString(); // 生成相对路径
                    })
                    .collect(Collectors.toList());
                
                itemData.put("images", relativePaths);
            }
            
            // 保存JSON数据
            String jsonPath = Paths.get(saveDir, "data.json").toString();
            objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(new File(jsonPath), itemData);
            
            return jsonPath;
        } catch (Exception e) {
            logger.error("保存数据失败", e);
            return null;
        }
    }

    /**
     * 获取并保存单个项目详情（带重试机制）
     * @param itemId 项目ID
     * @param maxRetries 最大重试次数
     * @return 获取是否成功
     */
    public boolean getItemDetail(Long itemId, int maxRetries) {
        String token = userService.loadToken(); // 从UserService获取Token
        if (token == null) {
            logger.warn("未获取到有效token");
            return false;
        }

        String url = "https://api.moegoat.com/api/lois/" + itemId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36");

        int retryCount = 0;
        while (retryCount < maxRetries) {
            try {
                // 发送请求
                ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
                );

                Map<String, Object> data = response.getBody();
                if (!"success".equals(data.get("status"))) {
                    logger.error("获取详情失败: {}", data.getOrDefault("message", "未知错误"));
                    return false;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> itemData = (Map<String, Object>) data.get("data");
                
                // 下载图片
                if (itemData.containsKey("images")) {
                    @SuppressWarnings("unchecked")
                    List<String> imageUrls = (List<String>) itemData.get("images");
                    List<String> downloaded = downloadItemImages(itemId, imageUrls, maxRetries);
                    logger.info("下载完成 {}/{} 张图片", downloaded.size(), imageUrls.size());
                }

                // 保存数据
                String jsonPath = saveItemData(itemId, itemData);
                if (jsonPath != null) {
                    logger.info("项目数据已保存至: {}", jsonPath);
                }

                return true;

            } catch (HttpClientErrorException e) {
                logger.error("HTTP错误: {}, 详情: {}", e.getStatusCode(), e.getResponseBodyAsString());
                retryCount = handleRetry(retryCount, maxRetries); // 处理重试逻辑
            } catch (Exception e) {
                logger.error("处理详情时出错", e);
                retryCount = handleRetry(retryCount, maxRetries); // 处理重试逻辑
            }
        }
        return false;
    }

    /**
     * 获取已存在项目ID集合（自动扫描目录）
     * @return 项目ID集合
     */
    public Set<Long> getExistingIds() {
        Path itemsDir = Paths.get(baseDir, "items");
        Set<Long> existingIds = new HashSet<>();
        
        if (Files.notExists(itemsDir)) return existingIds; // 目录不存在直接返回空集合
        
        try {
            Files.list(itemsDir)
                 .filter(Files::isDirectory)
                 .map(dir -> dir.getFileName().toString())
                 .filter(folder -> folder.matches("\\d+")) // 只匹配数字文件夹
                 .map(Long::valueOf)
                 .forEach(existingIds::add);
        } catch (IOException e) {
            System.out.println("扫描已存在项目失败: " + e.getMessage());
        }
        return existingIds;
    }

    /**
     * 批量处理项目（自动跳过已存在ID）
     * @param startId 起始ID
     * @param endId 结束ID
     */
    public void processItems(Long startId, Long endId) {
        Set<Long> existingIds = getExistingIds();
        System.out.println("已存在项目数量: " + existingIds.size());

        for (long itemId = startId; itemId <= endId; itemId++) {
            if (existingIds.contains(itemId)) {
                System.out.println("项目 " + itemId + " 已存在，跳过");
                continue;
            }

            try {
                System.out.println("正在获取项目 " + itemId + "...");
                boolean success = getItemDetail(itemId, 3); // 最多重试3次
                
                if (success) {
                    existingIds.add(itemId);
                    System.out.println("项目 " + itemId + " 获取成功");
                } else {
                    System.out.println("项目 " + itemId + " 获取失败");
                }

                TimeUnit.MILLISECONDS.sleep(1000); // 间隔1秒防封禁

            } catch (Exception e) {
                System.out.println("处理项目 " + itemId + " 时出错: " + e.getMessage());
                try {
                    TimeUnit.SECONDS.sleep(5); // 出错后等待5秒
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println("-" + "=".repeat(50));
        }
    }

    /**
     * 处理重试逻辑（指数退避）
     * @param currentRetry 当前重试次数
     * @param maxRetries 最大重试次数
     * @return 新的重试次数
     */
    private int handleRetry(int currentRetry, int maxRetries) {
        currentRetry++;
        if (currentRetry < maxRetries) {
            double waitTime = 0.01 * Math.pow(2, currentRetry); // 指数退避策略
            System.out.println("请求失败，等待" + waitTime + "秒后重试...");
            try {
                TimeUnit.SECONDS.sleep((long) waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return currentRetry;
    }
}
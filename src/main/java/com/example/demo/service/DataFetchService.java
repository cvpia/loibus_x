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
import java.util.stream.Collectors;

@Service
public class DataFetchService {
    private static final Logger logger = LoggerFactory.getLogger(DataFetchService.class);

    @Value("${base.dir}")
    private String baseDir;

    @Value("${page.dir}")
    private String pageDir;

    @Value("${combined.file}")
    private String combinedFile;

    @Value("${image.save.dir}")
    private String imageSaveDir;

    @Value("${api.lois.url}")
    private String loisApiUrl;

    private final UserService userService;

    @Autowired
    public DataFetchService(UserService userService) {
        this.userService = userService;
    }

    public String downloadImage(String url) {
        try {
            // 创建保存目录
            File saveDir = new File(imageSaveDir);
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }

            // 解析文件名
            String filename = URLDecoder.decode(url.substring(url.lastIndexOf('/') + 1), StandardCharsets.UTF_8);
            String savePath = Paths.get(imageSaveDir, filename).toString();

            // 检查文件是否已存在
            File file = new File(savePath);
            if (file.exists()) {
                return file.getAbsolutePath();
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

            return file.getAbsolutePath();
        } catch (Exception e) {
            logger.error("图片下载失败", e);
            return null;
        }
    }

    public Map<String, Object> loadExistingData() {
        try {
            File file = new File(combinedFile);
            if (file.exists()) {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(file, Map.class);
            }
        } catch (Exception e) {
            logger.error("加载现有数据失败", e);
        }
        return new HashMap<>() {{ put("data", new ArrayList<>()); }};
    }

    public void saveCombinedData(Map<String, Object> data) {
        try {
            File file = new File(combinedFile);
            File parentDir = file.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
        } catch (Exception e) {
            logger.error("保存合并数据失败", e);
        }
    }

    public Map<String, Object> fetchPageData(int pageNum, String token) {
        String url = loisApiUrl + "?page=" + pageNum;
        
        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<?> request = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            // 发送请求
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Map.class
            );

            return response.getBody();
        } catch (HttpClientErrorException e) {
            logger.error("HTTP错误: {}", e.getStatusCode());
            logger.error("错误详情: {}", e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            logger.error("第{}页请求失败", pageNum, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> processPageData(Map<String, Object> pageData, Map<String, Object> existingData) {
        if (pageData == null || !"success".equals(pageData.get("status"))) {
            return existingData;
        }

        List<Map<String, Object>> existingItems = (List<Map<String, Object>>) existingData.get("data");
        if (existingItems == null) {
            existingItems = new ArrayList<>();
            existingData.put("data", existingItems);
        }

        // 已存在的ID集合
        Set<String> existingIds = existingItems.stream()
            .map(item -> String.valueOf(item.get("id")))
            .collect(Collectors.toSet());

        List<Map<String, Object>> newItems = new ArrayList<>();
        List<Map<String, Object>> pageItems = (List<Map<String, Object>>) pageData.get("data");

        if (pageItems != null) {
            for (Map<String, Object> item : pageItems) {
                String itemId = String.valueOf(item.get("id"));
                if (!existingIds.contains(itemId)) {
                    // 处理封面图片
                    if (item.containsKey("cover") && item.get("cover") != null) {
                        String localPath = downloadImage(item.get("cover").toString());
                        if (localPath != null) {
                            item.put("cover", localPath);
                        }
                    }
                    newItems.add(item);
                    existingIds.add(itemId);
                }
            }
        }

        if (!newItems.isEmpty()) {
            existingItems.addAll(newItems);
        }

        return existingData;
    }

    public void fetchData(int startPage, int endPage) {
        logger.info("项目根目录: {}", baseDir);
        logger.info("数据将保存至: {}", combinedFile);

        // 获取token
        String token = userService.loadToken();
        if (token == null) {
            logger.warn("未获取到有效token");
            return;
        }

        // 加载现有数据
        Map<String, Object> combinedData = loadExistingData();

        // 分页获取数据
        for (int page = startPage; page <= endPage; page++) {
            logger.info("正在处理第{}页...", page);
            Map<String, Object> pageData = fetchPageData(page, token);
            if (pageData != null) {
                combinedData = processPageData(pageData, combinedData);
            }
        }

        // 保存合并后的数据
        saveCombinedData(combinedData);
        
        // 打印统计信息
        List<?> dataList = (List<?>) combinedData.get("data");
        int count = dataList != null ? dataList.size() : 0;
        logger.info("数据处理完成，共获取{}条不重复数据", count);
        logger.info("数据已保存至: {}", combinedFile);
    }
}
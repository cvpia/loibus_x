# 哆啦A梦 - 资源管理系统

## 项目介绍

这是一个基于 Spring Boot 开发的资源管理系统，主要用于从外部 API 采集数据、处理文件下载、管理用户认证，并通过 Web 界面展示数据。系统集成了 Swiper 轮播图和 Font Awesome 图标库，提供了美观的用户界面。

## 功能特点

- **数据采集**：从外部 API 批量获取数据，处理后保存到本地
- **文件下载**：支持从外部 API 下载文件并保存到本地
- **用户认证**：管理用户认证 Token，支持 Token 的获取、存储和使用
- **数据展示**：通过 Web 界面展示采集的数据，支持分页、搜索和详情查看
- **轮播图**：集成 Swiper 轮播图，提供流畅的图片展示效果
- **响应式设计**：适配不同屏幕尺寸的设备
- **离线资源**：所有静态资源已本地化，不依赖外部 CDN

## 技术栈

- **后端**：Spring Boot 3.2.0
- **前端**：HTML5、CSS3、JavaScript
- **模板引擎**：Thymeleaf
- **数据库**：H2
- **API 客户端**：RestTemplate
- **轮播图**：Swiper 8
- **图标库**：Font Awesome 6
- **构建工具**：Maven

## 项目结构

```
├── src/
│   ├── main/
│   │   ├── java/com/example/demo/
│   │   │   ├── config/         # 配置类
│   │   │   ├── controller/      # 控制器
│   │   │   ├── service/         # 服务层
│   │   │   ├── utils/           # 工具类
│   │   │   └── DemoApplication.java  # 应用入口
│   │   └── resources/
│   │       ├── static/          # 静态资源
│   │       │   ├── css/          # CSS 文件
│   │       │   └── js/           # JS 文件
│   │       ├── templates/        # Thymeleaf 模板
│   │       ├── application.properties  # 应用配置
│   │       └── log4j2.xml       # 日志配置
├── page/                        # 页面数据存储
├── logs/                        # 日志文件
├── target/                      # 构建输出
├── pom.xml                      # Maven 配置
└── README.md                    # 项目说明
```

## 安装部署

### 环境要求

- JDK 17+
- Maven 3.6+

### 构建项目

1. **克隆项目**

2. **构建项目**
   ```bash
   mvn clean package
   ```

3. **运行项目**
   ```bash
   java -jar target/demo-0.0.1-SNAPSHOT.jar
   ```

4. **访问项目**
   打开浏览器访问：`http://localhost:8080`

## 配置说明

主要配置文件：`src/main/resources/application.properties`

### 核心配置项

| 配置项 | 说明 | 默认值 |
|-------|------|-------|
| api.login.url | 登录 API 地址 | https://api.moegoat.com/api/user/login |
| api.user.info.url | 用户信息 API 地址 | https://api.moegoat.com/api/user/info |
| token.file.path | Token 存储文件 | token.json |
| api.lois.url | 数据 API 地址 | https://api.moegoat.com/api/lois |
| api.item.detail.url | 详情 API 地址 | https://api.moegoat.com/api/lois |
| api.download.url | 下载 API 地址 | https://api.moegoat.com/api/user/loi/download_v6 |
| page.dir | 页面数据目录 | ${base.dir}/page |
| combined.file | 合并数据文件 | ${page.dir}/all_data.json |
| image.save.dir | 图片保存目录 | ${base.dir}/images |
| down.dir | 下载文件目录 | ./data |

## 使用方法

### 1. 数据采集

- **通过 HTTP 请求**：
  ```
  GET /fetchData?startPage=1&endPage=5
  ```

- **参数说明**：
  - `startPage`：起始页码（默认：1）
  - `endPage`：结束页码（默认：4）

### 2. 数据展示

- **首页**：`http://localhost:8080/`
  - 展示所有采集的数据
  - 支持分页浏览
  - 支持关键词搜索

- **详情页**：`http://localhost:8080/card?id=123`
  - 展示资源详细信息
  - 提供轮播图展示图片
  - 显示下载选项

### 3. 认证管理

系统会自动管理认证 Token，首次使用时会自动获取并存储 Token。

## 本地资源

所有静态资源已本地化，不依赖外部 CDN：

- **Swiper CSS**：`/static/css/swiper-bundle.min.css`
- **Swiper JS**：`/static/js/swiper-bundle.min.js`
- **Font Awesome**：`/static/css/all.min.css`

## 免责声明

1. 本项目仅供学习交流使用，不存储任何数据
2. 继续使用本项目视为同意本声明
3. 请勿传播本项目内容
4. 部分内容可能涉及成人内容，18岁以下请勿使用

## 许可证

本项目采用 MIT 许可证。

## 开发说明

### 代码规范

- 遵循 Spring Boot 编码规范
- 使用注解进行依赖注入
- 分层架构，职责清晰
- 异常处理和日志记录

### 开发流程

1. **环境准备**：JDK 17+，Maven 3.6+
2. **代码修改**：在对应的模块中修改代码
3. **构建验证**：运行 `mvn clean package` 验证构建
4. **测试运行**：启动应用并测试功能

## 常见问题

### 1. 数据采集失败

- **原因**：网络问题或 API 限制
- **解决方案**：检查网络连接，查看日志详情

### 2. 文件下载失败

- **原因**：文件不存在或权限不足
- **解决方案**：检查文件 ID 是否正确，确保有足够权限

### 3. 应用启动失败

- **原因**：端口被占用或配置错误
- **解决方案**：检查端口占用情况，验证配置文件

## 联系方式
- **TG**：[@loibus_x](https://t.me/loibus_x)
如有问题或建议，欢迎联系项目维护者。

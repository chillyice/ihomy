# ihomy

面向家庭成员的内容共享平台，支持 PC 浏览器 / 安卓 / iOS（均为 PWA）。为家庭提供一个私密、温暖的数字空间：记录生活、共享回忆、协作日常。

## 功能概览

- **内容**：博客、日记（含手绘涂鸦）、相册、放映厅、照片瀑布流、愿望单、书架（epub 阅读器）
- **生活**：纪念日、提醒、家庭计划、任务悬赏、记账、家谱、菜单菜谱与食材、物品定位（户型图）
- **互动**：点赞、评论、通知、聊天室（WebSocket 实时）
- **系统**：签到积分、多家庭切换、RBAC 权限（OWNER/MEMBER/CHILD/GUEST/OPS）、i18n 中英双语、明暗主题、运维后台

首页模块化可扩展；光影系统按真实太阳位置渲染室内光照，并与和风天气联动。工具箱内置可视化脑图设计（多人协同编辑）。

## 技术栈

| 端 | 技术 |
|----|------|
| 前端 | Vue 3 + Vite + Element Plus + Pinia + vue-i18n + GSAP + PWA |
| 后端 | Spring Boot 3（JDK 21 虚拟线程）+ MyBatis-Plus + JWT 双 token |
| 数据 | MySQL 8 + Redis（缓存 / 验证码 / 令牌黑名单） |
| 通信 | REST + WebSocket（聊天室） |

## 快速开始

```bash
# 前端
cd frontend && npm install && npm run dev        # http://localhost:5173

# 后端（先复制 backend/src/main/resources/external.yml.template 为 external.yml
#       并设环境变量 IHOMY_CONFIG_PATH 指向它，数据库/JWT 密钥等敏感配置一律走外挂文件）
cd backend && ./mvnw spring-boot:run             # http://localhost:8080/api
```

> 说明：详细部署文档与新人上手指南由维护者本地保管，不入仓库；`schema.sql` 为开发安全版随仓库提供（仅含本机 Docker 开发凭证，生产凭证一律走 external.yml 外挂文件，不入仓库）。

## License

GPL-3.0

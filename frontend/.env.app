# Android/iOS 客户端构建专用(公开信息,非凭证):WebView 内页面 origin 是 https://localhost,
# 相对路径 /api 会打到壳自己,必须用服务端绝对基址。构建方式:npm run app:build(npm run build -- --mode app)。
# ⚠ 不要改用 .env.local:那份会被内联进生产 Web bundle(AGENTS 硬性禁令)。
VITE_API_BASE=https://ihomy.top/api

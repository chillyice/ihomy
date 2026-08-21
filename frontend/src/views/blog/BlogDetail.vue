<!-- 博客详情页:目录导航 + 正文(Markdown) + 点赞 + 评论树 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('blog.title'), to: '/blog' }, { label: blog?.title || $t('blog.detail') }]" />
    <div v-if="blog" class="blog-layout">
      <!-- 正文卡片:居中 -->
      <div class="card detail">
        <h1>{{ blog.title }}</h1>
        <div class="meta">
          <span>{{ formatDate(blog.createdAt) }} · {{ blog.viewCount }} {{ $t('blog.views') }}</span>
          <span v-if="blog.tags" class="tags">
            <span v-for="t in tagList" :key="t" class="tag">#{{ t }}</span>
          </span>
        </div>
        <img v-if="blog.coverImage" :src="blog.coverImage" class="cover" />
        <div class="content markdown-body" ref="contentRef" v-html="renderedContent"></div>

        <div class="like-bar">
          <el-button :type="likeState.liked ? 'primary' : 'default'" round @click="onLike">
            <el-icon><Star /></el-icon>
            <span>{{ likeState.liked ? $t('blog.liked') : $t('blog.like') }}</span>
            <span v-if="likeState.likeCount">({{ likeState.likeCount }})</span>
          </el-button>
        </div>
      </div>

      <!-- 目录导航:右侧 sticky -->
      <aside v-if="toc.length" class="toc-aside">
        <nav class="toc">
          <div class="toc-title">目录</div>
          <ul>
            <li v-for="h in toc" :key="h.id" :class="'toc-l' + h.level">
              <a :href="'#' + h.id" :class="{ active: activeHeading === h.id }" @click.prevent="scrollTo(h.id)">{{ h.text }}</a>
            </li>
          </ul>
        </nav>
      </aside>
    </div>

    <div v-if="blog" class="card comments">
      <div class="comments-title">{{ $t('blog.comment') }} ({{ comments.length }})</div>

      <div v-if="userStore.isLoggedIn" class="comment-input">
        <el-input
          v-model="commentText"
          :placeholder="replyTarget ? $t('blog.replyPlaceholder', { name: replyTarget }) : $t('blog.commentPlaceholder')"
          @keyup.enter="submitComment"
        />
        <div class="comment-actions">
          <el-button v-if="replyTarget" text size="small" @click="cancelReply">{{ $t('blog.cancelReply') }}</el-button>
          <el-button type="primary" size="small" :loading="submitting" @click="submitComment">{{ $t('blog.postComment') }}</el-button>
        </div>
      </div>
      <el-empty v-else :description="$t('blog.loginToComment')" :image-size="60" />

      <div v-if="comments.length" class="comment-list">
        <div v-for="c in comments" :key="c.id" class="comment-item">
          <div class="comment-head">
            <span class="c-author">{{ c.authorName }}</span>
            <span class="c-time">{{ formatTime(c.createdAt) }}</span>
          </div>
          <div class="c-body">{{ c.content }}</div>
          <div class="c-ops">
            <el-button v-if="userStore.isLoggedIn" text size="small" @click="setReply(c)">{{ $t('blog.reply') }}</el-button>
            <el-button
              v-if="canDelete(c)"
              text
              size="small"
              type="danger"
              @click="delComment(c)"
            >{{ $t('common.delete') }}</el-button>
          </div>

          <div v-if="c.replies?.length" class="reply-list">
            <div v-for="r in c.replies" :key="r.id" class="reply-item">
              <span class="c-author">{{ r.authorName }}</span>
              <span v-if="r.replyToName" class="reply-to">{{ $t('blog.replyTo', { name: r.replyToName }) }}</span>
              <span class="reply-content">{{ r.content }}</span>
              <el-button
                v-if="canDelete(r)"
                text
                size="small"
                type="danger"
                class="reply-del"
                @click="delComment(r)"
              >{{ $t('common.delete') }}</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else :description="$t('blog.notFound') || '博客不存在或无权查看'" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { blogApi, likeApi, commentApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Star } from '@element-plus/icons-vue'
import { marked } from 'marked'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()
const route = useRoute()
const userStore = useUserStore()
const blog = ref(null)
const comments = ref([])
const commentText = ref('')
const replyTarget = ref(null)
const replyTargetId = ref(null)
const replyToUserId = ref(null)
const submitting = ref(false)
const likeState = ref({ liked: false, likeCount: 0 })
const contentRef = ref(null)
const toc = ref([])
const activeHeading = ref('')

// 标签字符串按逗号拆分
const tagList = computed(() =>
  blog.value?.tags ? String(blog.value.tags).split(',').filter(Boolean) : [],
)

// Markdown 渲染:用 marked.lexer + walkTokens 把 h1 降级为 h2(避免与博客标题的 h1 重复),
// 同时给每个标题加 id 用于目录跳转
marked.use({
  renderer: {
    heading({ text, depth }) {
      // h1 降级为 h2,避免与页面标题重复;最大 depth=4
      const level = Math.min(depth + (depth === 1 ? 1 : 0), 4)
      const id = 'h-' + text.replace(/[^\w\u4e00-\u9fa5]+/g, '-').replace(/^-|-$/g, '').toLowerCase().slice(0, 50)
      return `<h${level} id="${id}">${text}</h${level}>`
    },
  },
})

// Markdown 渲染为 HTML
const renderedContent = computed(() =>
  blog.value?.content ? marked.parse(blog.value.content) : '',
)

// 从渲染后的 DOM 提取标题列表(用于目录导航)
const extractToc = () => {
  nextTick(() => {
    if (!contentRef.value) { toc.value = []; return }
    const headings = contentRef.value.querySelectorAll('h2, h3, h4')
    toc.value = Array.from(headings).map(h => ({
      id: h.id,
      text: h.textContent || '',
      level: parseInt(h.tagName.slice(1)),
    }))
  })
}

// 点击目录跳转
const scrollTo = (id) => {
  const el = document.getElementById(id)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

// 博客内容变化后提取目录 + 初始化 scroll spy
watch(renderedContent, () => {
  extractToc()
  nextTick(() => initScrollSpy())
})

// Scroll spy:监听滚动高亮当前章节
let spyObserver = null
const initScrollSpy = () => {
  if (spyObserver) spyObserver.disconnect()
  if (!toc.value.length) return
  const headings = toc.value.map(h => document.getElementById(h.id)).filter(Boolean)
  if (!headings.length) return
  spyObserver = new IntersectionObserver(
    (entries) => {
      const visible = entries.filter(e => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
      if (visible.length) activeHeading.value = visible[0].target.id
    },
    { rootMargin: '-80px 0px -70% 0px', threshold: 0 }
  )
  headings.forEach(h => spyObserver.observe(h))
}

onBeforeUnmount(() => {
  if (spyObserver) spyObserver.disconnect()
})

// 删除权限:家长或评论作者本人
const canDelete = (c) =>
  userStore.isLoggedIn && (userStore.isOwner || c.authorId === userStore.userInfo?.id)

// 并行拉取博客详情、评论树与当前用户点赞状态
const loadAll = async () => {
  try {
    blog.value = await blogApi.detail(route.params.id)
  } catch (e) {
    // 博客详情加载失败(可能不存在或无权查看),保持 blog=null 显示空状态
  }
  try {
    comments.value = await commentApi.list('blog', route.params.id)
  } catch (e) {
    // 评论加载失败不影响博客展示
  }
  if (userStore.isLoggedIn) {
    try {
      likeState.value = await likeApi.state('blog', route.params.id)
    } catch (e) {
      // 忽略
    }
  }
}

// 点赞/取消点赞:结果同步回博客浏览数的点赞字段
const onLike = async () => {
  if (!userStore.isLoggedIn) return ElMessage.warning(t('blog.needLogin'))
  likeState.value = await likeApi.toggle({ contentType: 'blog', contentId: route.params.id })
  if (blog.value) blog.value.likeCount = likeState.value.likeCount
}

// 进入回复模式:记录被回复评论的 id 与作者,提交时作为 parentId/replyToUserId
const setReply = (c) => {
  replyTarget.value = c.authorName
  replyTargetId.value = c.id
  replyToUserId.value = c.authorId
  commentText.value = ''
}

const cancelReply = () => {
  replyTarget.value = null
  replyTargetId.value = null
  replyToUserId.value = null
}

// 提交评论/回复:成功后刷新评论树
const submitComment = async () => {
  if (!commentText.value.trim()) return ElMessage.warning(t('blog.inputContent'))
  submitting.value = true
  try {
    await commentApi.create({
      contentType: 'blog',
      contentId: route.params.id,
      content: commentText.value.trim(),
      parentId: replyTargetId.value,
      replyToUserId: replyToUserId.value,
    })
    commentText.value = ''
    cancelReply()
    ElMessage.success(t('blog.commentSuccess'))
    comments.value = await commentApi.list('blog', route.params.id)
  } finally {
    submitting.value = false
  }
}

const delComment = async (c) => {
  await commentApi.remove(c.id)
  ElMessage.success(t('common.deleted'))
  comments.value = await commentApi.list('blog', route.params.id)
}

const formatTime = (d) => (d ? new Date(d).toLocaleString('zh-CN') : '')
const formatDate = (d) => (d ? new Date(d).toLocaleDateString('zh-CN') : '')

onMounted(loadAll)
</script>

<style scoped>
/* 布局:目录 + 正文 左右排列 */
.blog-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

/* 目录导航:sticky 固定在视口,不受页面滚动干扰 */
.toc-aside {
  width: 200px;
  flex-shrink: 0;
  position: sticky;
  top: 42px;
}
.toc {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px 14px;
  box-shadow: var(--shadow);
  max-height: calc(100vh - 120px);
  overflow-y: auto;
}
.toc-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-primary);
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--color-border);
}
.toc ul { list-style: none; padding: 0; margin: 0; }
.toc li { margin: 0; }
.toc a {
  display: block;
  padding: 4px 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
  text-decoration: none;
  border-radius: 4px;
  transition: all 0.2s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.toc a:hover, .toc a.active { background: rgba(168, 72, 58, 0.12); color: var(--color-accent); font-weight: 600; }
.toc-l2 { padding-left: 0; }
.toc-l3 { padding-left: 12px; }
.toc-l4 { padding-left: 24px; }

/* 正文卡片 */
.detail { flex: 1; min-width: 0; padding: 24px 28px; }
.detail h1 { color: var(--color-primary); margin-bottom: 8px; line-height: 1.4; }
.meta { font-size: 13px; color: var(--color-text-secondary); margin-bottom: 16px; display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
.tags { display: flex; gap: 6px; }
.tag { background: rgba(46, 116, 181, 0.08); color: var(--color-accent); padding: 1px 8px; border-radius: 10px; font-size: 12px; }
.cover { width: 100%; max-height: 300px; object-fit: cover; border-radius: 8px; margin-bottom: 16px; }

/* Markdown 正文:行间距 1.9,字号 15px;层级:段落间距 > 小节间距 > 正文行间距 */
.content { font-size: 15px; line-height: 1.9; }
.markdown-body { white-space: normal; word-wrap: break-word; }
.markdown-body h2, .markdown-body h3, .markdown-body h4 { color: var(--color-primary); line-height: 1.4; }
/* 小节标题:小节间距 */
.markdown-body h2 { font-size: 20px; margin: 22px 0 14px; border-bottom: 1px solid var(--color-border); padding-bottom: 6px; }
.markdown-body h3 { font-size: 17px; margin: 20px 0 12px; }
.markdown-body h4 { font-size: 15px; margin: 18px 0 10px; }
/* 段落:段落间距(最大),拉大阅读呼吸感 */
.markdown-body p { margin: 36px 0; }
/* ul/ol 加大缩进,凸显列表子内容的层级 */
.markdown-body ul, .markdown-body ol { margin: 24px 0; padding-left: 44px; }
.markdown-body li { margin: 10px 0; }
/* 粗体列表项(- **xx**)额外缩进 */
.markdown-body li > strong:first-child { display: inline-block; margin-left: 8px; }
.markdown-body blockquote { margin: 24px 0; padding: 10px 18px; border-left: 4px solid var(--color-accent); background: rgba(168,72,58,0.05); color: var(--color-text-secondary); border-radius: 0 8px 8px 0; }
.markdown-body code { background: rgba(58,46,34,0.08); padding: 2px 6px; border-radius: 4px; font-size: 13px; font-family: 'Consolas', 'Monaco', monospace; }
.markdown-body pre { background: rgba(58,46,34,0.06); padding: 14px 18px; border-radius: 8px; overflow-x: auto; margin: 14px 0; }
.markdown-body pre code { background: none; padding: 0; }
.markdown-body table { border-collapse: collapse; margin: 14px 0; width: 100%; }
.markdown-body th, .markdown-body td { border: 1px solid var(--color-border); padding: 8px 12px; text-align: left; }
.markdown-body th { background: rgba(58,46,34,0.05); font-weight: 600; }
.markdown-body img { max-width: 100%; border-radius: 8px; margin: 14px 0; }
.markdown-body a { color: var(--color-accent); text-decoration: underline; }
.markdown-body hr { border: none; border-top: 1px solid var(--color-border); margin: 40px 0; }

.like-bar { margin-top: 24px; padding-top: 16px; border-top: 1px solid rgba(31, 58, 95, 0.08); }
.comments { margin-top: 16px; margin-right: calc(200px + 24px); padding: 20px 28px; }
.comments-title { font-weight: 600; color: var(--color-primary); margin-bottom: 12px; }
.comment-input { display: flex; flex-direction: column; gap: 8px; margin-bottom: 16px; }
.comment-actions { display: flex; justify-content: flex-end; gap: 8px; }
.comment-list { display: flex; flex-direction: column; gap: 14px; }
.comment-item { border-bottom: 1px solid rgba(31, 58, 95, 0.06); padding-bottom: 12px; }
.comment-head { display: flex; gap: 10px; align-items: center; margin-bottom: 4px; }
.c-author { font-weight: 600; font-size: 13px; color: var(--color-accent); }
.c-time { font-size: 12px; color: var(--color-text-secondary); }
.c-body { font-size: 14px; line-height: 1.6; }
.c-ops { margin-top: 4px; }
.reply-list { margin-top: 10px; padding-left: 16px; border-left: 2px solid rgba(46, 116, 181, 0.15); display: flex; flex-direction: column; gap: 6px; }
.reply-item { font-size: 13px; }
.reply-to { color: var(--color-text-secondary); margin: 0 4px; }
.reply-content { color: var(--color-text); }
.reply-del { margin-left: 6px; }

/* 目录滚动条 */
.toc::-webkit-scrollbar { width: 4px; }
.toc::-webkit-scrollbar-thumb { background: rgba(58, 46, 34, 0.15); border-radius: 2px; }

/* 移动端:目录隐藏,只显示正文 */
@media (max-width: 900px) {
  .blog-layout { flex-direction: column; }
  .toc-aside { display: none; }
  .detail { padding: 20px; }
  .comments { margin-right: 0; }
}
</style>
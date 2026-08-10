<!-- 博客详情页:正文 + 点赞 + 评论树(支持回复,家长或作者可删) -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('blog.title'), to: '/blog' }, { label: blog?.title || $t('blog.detail') }]" />
    <div v-if="blog" class="card detail">
      <h1>{{ blog.title }}</h1>
      <div class="meta">
        <span>{{ formatDate(blog.createdAt) }} · {{ blog.viewCount }} {{ $t('blog.views') }}</span>
        <span v-if="blog.tags" class="tags">
          <span v-for="t in tagList" :key="t" class="tag">#{{ t }}</span>
        </span>
      </div>
      <img v-if="blog.coverImage" :src="blog.coverImage" class="cover" />
      <div class="content">{{ blog.content }}</div>

      <div class="like-bar">
        <el-button :type="likeState.liked ? 'primary' : 'default'" round @click="onLike">
          <el-icon><Star /></el-icon>
          <span>{{ likeState.liked ? $t('blog.liked') : $t('blog.like') }}</span>
          <span v-if="likeState.likeCount">({{ likeState.likeCount }})</span>
        </el-button>
      </div>
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { blogApi, likeApi, commentApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Star } from '@element-plus/icons-vue'
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

// 标签字符串按逗号拆分
const tagList = computed(() =>
  blog.value?.tags ? String(blog.value.tags).split(',').filter(Boolean) : [],
)

// 删除权限:家长或评论作者本人
const canDelete = (c) =>
  userStore.isLoggedIn && (userStore.isOwner || c.authorId === userStore.userInfo?.id)

// 并行拉取博客详情、评论树与当前用户点赞状态
const loadAll = async () => {
  blog.value = await blogApi.detail(route.params.id)
  comments.value = await commentApi.list('blog', route.params.id)
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

onMounted(loadAll)
</script>

<style scoped>
.detail h1 { color: var(--color-primary); margin-bottom: 8px; }
.meta { font-size: 13px; color: var(--color-text-secondary); margin-bottom: 16px; display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
.tags { display: flex; gap: 6px; }
.tag { background: rgba(46, 116, 181, 0.08); color: var(--color-accent); padding: 1px 8px; border-radius: 10px; font-size: 12px; }
.cover { width: 100%; max-height: 300px; object-fit: cover; border-radius: 8px; margin-bottom: 16px; }
.content { white-space: pre-wrap; line-height: 1.8; font-size: 15px; }
.like-bar { margin-top: 24px; padding-top: 16px; border-top: 1px solid rgba(31, 58, 95, 0.08); }
.comments { margin-top: 16px; }
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
</style>
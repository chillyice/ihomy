<!-- 放映厅页:视频库(搜索/上传/内嵌播放)与想看列表(提交/标记入库)两个标签页 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: '放映厅' }]" />

    <div class="list-header">
      <h2>放映厅</h2>
      <div class="header-actions">
        <el-input v-model="keyword" placeholder="搜索片名" clearable style="width: 200px" @keyup.enter="load" @clear="load" />
        <el-button v-if="userStore.isLoggedIn" @click="openWishDialog">想看</el-button>
        <el-button v-if="userStore.isLoggedIn" type="primary" @click="openEditor()">上传视频</el-button>
      </div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="视频库" name="library">
        <div v-loading="loading">
          <div v-if="list.length" class="video-grid">
            <div v-for="v in list" :key="v.id" class="video-card card">
              <div class="video-poster" @click="play(v)">
                <img v-if="v.poster" :src="v.poster" class="poster-img" alt="海报" />
                <div v-else class="poster-placeholder">🎬</div>
                <div class="play-overlay">▶ 播放</div>
              </div>
              <div class="video-info">
                <div class="video-title">{{ v.title }}</div>
                <div v-if="v.originalTitle" class="video-original">{{ v.originalTitle }}</div>
                <div v-if="v.genres" class="video-genres">
                  <span v-for="g in String(v.genres).split(',').filter(Boolean)" :key="g" class="tag">#{{ g }}</span>
                </div>
                <div class="video-meta">
                  <span v-if="v.year">{{ v.year }}</span>
                  <span v-if="v.region">· {{ v.region }}</span>
                  <span v-if="v.rating">· ⭐ {{ v.rating }}</span>
                  <span v-if="v.mediaType === 'series'">· {{ v.episodes || '?' }}集</span>
                  <span v-else-if="v.duration">· {{ v.duration }}分钟</span>
                </div>
                <div v-if="v.director" class="video-credit">导演:{{ v.director }}</div>
                <div v-if="v.actors" class="video-credit">主演:{{ v.actors }}</div>
                <div class="video-footer">
                  <span class="video-uploader">{{ v.uploaderName }}</span>
                  <span v-if="userStore.isLoggedIn" class="video-actions">
                    <el-button size="small" text @click="openEditor(v)">编辑</el-button>
                    <el-button size="small" text type="danger" @click="onDel(v)">删除</el-button>
                  </span>
                </div>
              </div>
            </div>
          </div>
          <el-empty v-else :description="userStore.isGuest ? '暂无影片' : '库存还没有影片，上传第一部吧'" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="想看列表" name="wish">
        <div v-loading="wishLoading">
          <div v-if="wishes.length" class="wish-list">
            <div v-for="w in wishes" :key="w.id" class="wish-item card">
              <div class="wish-main">
                <div class="wish-title">
                  {{ w.title }}
                  <el-tag v-if="w.status === 1" size="small" type="success">已入库</el-tag>
                  <el-tag v-else size="small" type="info">待入库</el-tag>
                </div>
                <div v-if="w.genres" class="wish-genres">
                  <span v-for="g in String(w.genres).split(',').filter(Boolean)" :key="g" class="tag">#{{ g }}</span>
                </div>
                <div v-if="w.reason" class="wish-reason">{{ w.reason }}</div>
                <div class="wish-meta">{{ w.requesterName }} · {{ formatDate(w.createdAt) }}</div>
              </div>
              <div class="wish-actions">
                <el-button v-if="userStore.isLoggedIn && w.status === 0" size="small" type="primary" plain @click="onWishDone(w)">标记已入库</el-button>
                <el-button v-if="userStore.isLoggedIn" size="small" text type="danger" @click="onWishDel(w)">删除</el-button>
              </div>
            </div>
          </div>
          <el-empty v-else :description="userStore.isGuest ? '暂无想看' : '还没有想看请求，点右上角「想看」提交'" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="wishDialog.visible" title="提交想看" width="460px">
      <el-form :model="wishDialog.form" label-position="top">
        <el-form-item label="片名">
          <el-input v-model="wishDialog.form.title" placeholder="想看的电影/剧集名称" />
        </el-form-item>
        <el-form-item label="题材分类">
          <el-select v-model="wishDialog.form.genres" multiple filterable allow-create default-first-option placeholder="选择或输入题材" style="width: 100%">
            <el-option v-for="g in genresOptions" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="wishDialog.form.reason" type="textarea" :rows="3" placeholder="为什么想看？在哪里看到过？" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="wishDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="onWishSave">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editor.visible" :title="editor.form.id ? '编辑影片' : '上传影片'" width="640px" top="5vh">
      <el-form :model="editor.form" label-position="top">
        <div class="form-row">
          <el-form-item label="片名">
            <el-input v-model="editor.form.title" placeholder="必填" />
          </el-form-item>
          <el-form-item label="原名">
            <el-input v-model="editor.form.originalTitle" placeholder="外语片原名" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="类型">
            <el-select v-model="editor.form.mediaType" style="width: 100%">
              <el-option label="电影" value="movie" />
              <el-option label="剧集" value="series" />
              <el-option label="视频" value="other" />
            </el-select>
          </el-form-item>
          <el-form-item label="题材分类（豆瓣）">
            <el-select v-model="editor.form.genres" multiple filterable allow-create default-first-option placeholder="选择或输入" style="width: 100%">
              <el-option v-for="g in genresOptions" :key="g" :label="g" :value="g" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="地区">
            <el-select v-model="editor.form.region" filterable allow-create default-first-option placeholder="选择或输入" style="width: 100%">
              <el-option v-for="r in regionOptions" :key="r" :label="r" :value="r" />
            </el-select>
          </el-form-item>
          <el-form-item label="年份">
            <el-input-number v-model="editor.form.year" :min="1900" :max="2100" controls-position="right" style="width: 100%" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="语言">
            <el-input v-model="editor.form.language" placeholder="如：国语、粤语、英语" />
          </el-form-item>
          <el-form-item :label="editor.form.mediaType === 'series' ? '总集数' : '片长（分钟）'">
            <el-input-number v-model="editor.form.duration" :min="0" :max="99999" controls-position="right" style="width: 100%" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="导演">
            <el-input v-model="editor.form.director" />
          </el-form-item>
          <el-form-item label="豆瓣评分">
            <el-input-number v-model="editor.form.rating" :min="0" :max="10" :precision="1" :step="0.1" controls-position="right" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="主演">
          <el-input v-model="editor.form.actors" placeholder="多个主演用逗号分隔" />
        </el-form-item>
        <el-form-item label="剧情简介">
          <el-input v-model="editor.form.intro" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="海报">
          <el-upload :show-file-list="false" :http-request="uploadPoster" accept="image/*">
            <img v-if="editor.form.poster" :src="editor.form.poster" class="poster-upload-preview" alt="海报" />
            <el-button v-else>上传海报图片</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="视频文件">
          <el-upload :show-file-list="false" :http-request="uploadVideo" accept="video/*">
            <el-button type="primary" plain>{{ editor.form.videoUrl ? '重新上传' : '上传视频文件' }}</el-button>
          </el-upload>
          <div v-if="editor.form.videoUrl" class="video-uploaded">已上传:{{ editor.form.videoUrl }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="player.visible" :title="player.video?.title" width="800px" top="5vh" destroy-on-close>
      <video v-if="player.video?.videoUrl" :src="player.video.videoUrl" controls autoplay class="player-video" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { videoApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'

const userStore = useUserStore()
const tab = ref('library')
const keyword = ref('')
const list = ref([])
const wishes = ref([])
const loading = ref(false)
const wishLoading = ref(false)

const genresOptions = [
  '剧情', '喜剧', '动作', '爱情', '科幻', '动画', '悬疑', '惊悚', '恐怖', '纪录片',
  '音乐', '犯罪', '冒险', '奇幻', '家庭', '历史', '战争', '武侠', '灾难', '运动',
  '歌舞', '西部', '儿童', '短片', '经典', '文艺', '枪战', '写实', '实验', '戏曲',
]

const regionOptions = [
  '中国大陆', '香港', '台湾', '美国', '英国', '日本', '韩国', '法国', '德国', '意大利',
  '西班牙', '印度', '泰国', '俄罗斯', '加拿大', '澳大利亚', '巴西', '瑞典', '丹麦', '其他',
]

// 新影片/编辑共用的空表单
const emptyForm = () => ({
  id: null, title: '', originalTitle: '', mediaType: 'movie', genres: [],
  region: '', year: null, language: '', duration: null, episodes: null,
  director: '', actors: '', rating: null, intro: '', poster: '', videoUrl: '',
})

const editor = reactive({ visible: false, form: emptyForm() })
const wishDialog = reactive({ visible: false, form: { title: '', genres: [], reason: '' } })
const player = reactive({ visible: false, video: null })

const formatDate = (d) => (d ? new Date(d).toLocaleDateString('zh-CN') : '')

// 拉取视频库,带 keyword 时按片名搜索
const load = async () => {
  loading.value = true
  try {
    const params = {}
    if (keyword.value) params.keyword = keyword.value
    list.value = await videoApi.list(params)
  } finally {
    loading.value = false
  }
}

// 拉取想看列表
const loadWishes = async () => {
  wishLoading.value = true
  try {
    wishes.value = await videoApi.wishList()
  } finally {
    wishLoading.value = false
  }
}

// 点击海报弹出播放器
const play = (v) => {
  player.video = v
  player.visible = true
}

// 打开编辑框:编辑时把 genres 字符串拆回数组,新增用空表单
const openEditor = (v) => {
  if (v) {
    editor.form = {
      ...v,
      genres: v.genres ? String(v.genres).split(',').filter(Boolean) : [],
    }
  } else {
    editor.form = emptyForm()
  }
  editor.visible = true
}

const uploadPoster = async ({ file }) => {
  const r = await videoApi.upload(file)
  editor.form.poster = r.url
  ElMessage.success('海报上传成功')
}

const uploadVideo = async ({ file }) => {
  const r = await videoApi.upload(file)
  editor.form.videoUrl = r.url
  ElMessage.success('视频上传成功')
}

// 保存影片:剧集把"片长"字段存到 episodes,电影反之;空值转 null 落库
const onSave = async () => {
  if (!editor.form.title) return ElMessage.warning('请输入片名')
  if (!editor.form.videoUrl) return ElMessage.warning('请上传视频文件')
  const data = {
    title: editor.form.title,
    originalTitle: editor.form.originalTitle || null,
    mediaType: editor.form.mediaType,
    genres: editor.form.genres.length ? editor.form.genres.join(',') : null,
    region: editor.form.region || null,
    year: editor.form.year || null,
    language: editor.form.language || null,
    duration: editor.form.mediaType === 'series' ? null : editor.form.duration,
    episodes: editor.form.mediaType === 'series' ? editor.form.duration : null,
    director: editor.form.director || null,
    actors: editor.form.actors || null,
    rating: editor.form.rating || null,
    intro: editor.form.intro || null,
    poster: editor.form.poster || null,
    videoUrl: editor.form.videoUrl,
  }
  if (editor.form.id) await videoApi.update(editor.form.id, data)
  else await videoApi.create(data)
  ElMessage.success('保存成功')
  editor.visible = false
  load()
}

const onDel = async (v) => {
  await ElMessageBox.confirm(`确认删除「${v.title}」？`, '提示', { type: 'warning' })
  await videoApi.remove(v.id)
  ElMessage.success('已删除')
  load()
}

const openWishDialog = () => {
  wishDialog.form = { title: '', genres: [], reason: '' }
  wishDialog.visible = true
}

// 提交想看:题材数组转逗号分隔字符串
const onWishSave = async () => {
  if (!wishDialog.form.title) return ElMessage.warning('请输入想看的片名')
  await videoApi.addWish({
    title: wishDialog.form.title,
    genres: wishDialog.form.genres.length ? wishDialog.form.genres.join(',') : null,
    reason: wishDialog.form.reason || null,
  })
  ElMessage.success('想看请求已提交')
  wishDialog.visible = false
  loadWishes()
}

const onWishDone = async (w) => {
  await videoApi.wishDone(w.id)
  ElMessage.success('已标记入库')
  loadWishes()
}

const onWishDel = async (w) => {
  await ElMessageBox.confirm(`确认删除想看「${w.title}」？`, '提示', { type: 'warning' })
  await videoApi.wishRemove(w.id)
  ElMessage.success('已删除')
  loadWishes()
}

onMounted(() => {
  load()
  loadWishes()
})
</script>

<style scoped>
.list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.list-header h2 { color: var(--color-primary); }
.header-actions { display: flex; gap: 10px; }
.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}
.video-card { overflow: hidden; display: flex; flex-direction: column; }
.video-poster {
  position: relative;
  height: 170px;
  background: #1c2b3a;
  cursor: pointer;
  overflow: hidden;
}
.poster-img { width: 100%; height: 100%; object-fit: cover; }
.poster-placeholder {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  font-size: 48px;
  color: rgba(255, 255, 255, 0.25);
}
.play-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  color: #fff;
  background: rgba(0, 0, 0, 0.35);
  opacity: 0;
  transition: opacity 0.15s;
}
.video-poster:hover .play-overlay { opacity: 1; }
.video-info { padding: 14px 16px 12px; display: flex; flex-direction: column; gap: 6px; flex: 1; }
.video-title { font-size: 16px; font-weight: 600; color: var(--color-text); }
.video-original { font-size: 12px; color: var(--color-text-secondary); }
.video-genres { display: flex; gap: 6px; flex-wrap: wrap; }
.video-genres .tag { background: rgba(46, 116, 181, 0.08); color: var(--color-accent); padding: 1px 8px; border-radius: 10px; font-size: 12px; }
.video-meta { font-size: 12px; color: var(--color-text-secondary); }
.video-credit { font-size: 12px; color: var(--color-text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.video-footer {
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px solid rgba(31, 58, 95, 0.06);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.video-uploader { font-size: 12px; color: var(--color-text-secondary); }
.video-actions { display: flex; }
.wish-list { display: flex; flex-direction: column; gap: 12px; max-width: 720px; }
.wish-item { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 16px 20px; }
.wish-title { font-size: 15px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.wish-genres { display: flex; gap: 6px; margin-top: 6px; flex-wrap: wrap; }
.wish-genres .tag { background: rgba(46, 116, 181, 0.08); color: var(--color-accent); padding: 1px 8px; border-radius: 10px; font-size: 12px; }
.wish-reason { font-size: 13px; color: var(--color-text); margin-top: 6px; }
.wish-meta { font-size: 12px; color: var(--color-text-secondary); margin-top: 6px; }
.wish-actions { display: flex; flex-direction: column; gap: 6px; flex-shrink: 0; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.poster-upload-preview { width: 120px; height: 170px; object-fit: cover; border-radius: 8px; display: block; }
.video-uploaded { font-size: 12px; color: var(--color-text-secondary); margin-top: 6px; word-break: break-all; }
.player-video { width: 100%; max-height: 70vh; background: #000; border-radius: 8px; }

@media (max-width: 768px) {
  .video-grid { grid-template-columns: 1fr; }
  .form-row { grid-template-columns: 1fr; }
  .header-actions { flex-wrap: wrap; }
}
</style>

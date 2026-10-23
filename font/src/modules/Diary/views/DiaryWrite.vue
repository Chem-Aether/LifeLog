<template>
  <main class="write-page">
    <header class="write-header">
      <div class="heading-copy">
        <button class="back-button" title="返回" aria-label="返回" @click="goBack"><el-icon><ArrowLeft /></el-icon></button>
        <div><span class="eyebrow">LIFE JOURNAL</span><h1>{{ entryId ? '编辑日记' : '写日记' }}</h1><p>把值得记住的片刻，慢慢写下来。</p></div>
      </div>
      <el-button type="primary" :loading="saving" :disabled="uploadingImages" :icon="Check" @click="saveEntry">保存日记</el-button>
    </header>

    <form class="editor-layout" @submit.prevent="saveEntry">
      <section class="editor-main">
        <el-input v-model.trim="form.title" class="title-input" maxlength="200" show-word-limit placeholder="给这篇日记起个标题" />
        <el-input v-model="form.content" class="content-input" type="textarea" :autosize="{ minRows: 16, maxRows: 36 }" maxlength="20000" show-word-limit placeholder="今天发生了什么？写下你的所见、所闻与感受……" />
        <div class="photo-section">
          <div class="section-title"><el-icon><Picture /></el-icon><span>照片和视频</span></div>
          <MediaUploadEditor v-model="mediaItems" :upload="uploadDiaryAsset" allow-video :disabled="saving" @busy-change="uploadingImages = $event" />
        </div>
      </section>

      <aside class="editor-aside">
        <section class="aside-section"><h2>记录信息</h2>
          <el-form-item label="日期"><el-date-picker v-model="form.entryDate" type="date" value-format="YYYY-MM-DD" :clearable="false" /></el-form-item>
          <el-form-item label="心情"><el-select v-model="form.mood" clearable placeholder="选择此刻心情"><el-option v-for="mood in moods" :key="mood.value" :label="mood.label" :value="mood.value" /></el-select></el-form-item>
          <el-form-item label="天气"><div class="weather-picker"><img v-if="form.weatherCode != null" :src="weatherIconUrl(form.weatherCode)" :alt="weatherOption(form.weatherCode)?.name" /><el-select v-model="form.weatherCode" clearable placeholder="记录当天的天气"><el-option-group v-for="group in weatherGroups" :key="group.category" :label="group.category"><el-option v-for="weather in group.options" :key="weather.code" :label="weather.name" :value="weather.code"><div class="weather-option"><img :src="weatherIconUrl(weather.code)" alt="" /><span>{{ weather.name }}</span></div></el-option></el-option-group></el-select></div></el-form-item>
          <el-form-item label="地点"><el-input v-model.trim="form.location" maxlength="160" placeholder="今天在哪里" :prefix-icon="LocationInformation" /></el-form-item>
        </section>
        <section class="aside-section tag-section"><h2>标签</h2>
          <div class="tag-editor"><el-tag v-for="tag in form.tags" :key="tag" closable effect="plain" @close="removeTag(tag)">{{ tag }}</el-tag></div>
          <div class="tag-entry"><el-input v-model.trim="tagDraft" maxlength="40" placeholder="输入标签" @keyup.enter.prevent="addTag" /><el-button :icon="Plus" title="添加标签" aria-label="添加标签" @click="addTag" /></div>
        </section>
        <div class="aside-hint"><el-icon><Notebook /></el-icon><span>记录不必完美，真实就好。</span></div>
      </aside>
    </form>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, LocationInformation, Notebook, Picture, Plus } from '@element-plus/icons-vue'
import { createDiaryEntry, getDiaryEntry, updateDiaryEntry, uploadDiaryMedia, uploadDiaryVideo } from '../apis/diary.js'
import { diaryFeedState } from '../utils/feedState.js'
import { weatherGroups, weatherIconUrl, weatherOption } from '../utils/weather.js'
import MediaUploadEditor from '@/components/MediaUploadEditor.vue'

const route = useRoute()
const router = useRouter()
const entryId = computed(() => route.params.id || '')
const moods = [
  { value: '😊 开心', label: '😊 开心' }, { value: '😌 平静', label: '😌 平静' },
  { value: '🥰 感恩', label: '🥰 感恩' }, { value: '🤔 思考', label: '🤔 思考' },
  { value: '😔 低落', label: '😔 低落' }, { value: '😤 忙碌', label: '😤 忙碌' },
]
const form = reactive({ title: '', content: '', entryDate: today(), mood: '', weatherCode: null, location: '', tags: [], mediaUrls: [], mediaTypes: {} })
const mediaItems = ref([])
const tagDraft = ref('')
const saving = ref(false)
const uploadingImages = ref(false)
const ready = ref(false)
const saved = ref(false)
const original = ref('')
const isDirty = computed(() => ready.value && JSON.stringify({ ...form, tags: [...form.tags], mediaUrls: [...form.mediaUrls] }) !== original.value)

watch(mediaItems, items => {
  form.mediaUrls = items.map(item => item.url)
  form.mediaTypes = Object.fromEntries(items.map(item => [item.url, item.contentType]))
}, { deep: true })

function today() {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
function snapshot() { original.value = JSON.stringify({ ...form, tags: [...form.tags], mediaUrls: [...form.mediaUrls] }) }
function backPath() {
  const source = route.query.from
  if (source === 'feed') {
    const query = {}
    if (route.query.year) query.year = route.query.year
    if (route.query.keyword) query.keyword = route.query.keyword
    return { path: '/diary/feed', query }
  }
  if (source === 'manage') {
    const query = {}
    for (const key of ['keyword', 'year', 'page', 'size']) {
      if (route.query[key]) query[key] = route.query[key]
    }
    return { path: '/diary/manage', query }
  }
  return ({ home: '/diary/home', manage: '/diary/manage' })[source] || '/diary/feed'
}
function goBack() { router.push(backPath()) }
function addTag() {
  const value = tagDraft.value.trim()
  if (value && !form.tags.includes(value) && form.tags.length < 20) form.tags.push(value)
  tagDraft.value = ''
}
function removeTag(tag) { form.tags = form.tags.filter(value => value !== tag) }
function uploadDiaryAsset(file, mediaType) { return mediaType === 'video' ? uploadDiaryVideo(file) : uploadDiaryMedia(file) }
async function loadEntry() {
  if (entryId.value) {
    try {
      const entry = await getDiaryEntry(entryId.value)
      Object.assign(form, {
        title: entry.title || '', content: entry.content || '', entryDate: String(entry.entryDate).slice(0, 10),
        mood: entry.mood || '', weatherCode: entry.weatherCode ?? null, location: entry.location || '', tags: [...(entry.tags || [])], mediaUrls: [...(entry.mediaUrls || [])], mediaTypes: { ...(entry.mediaTypes || {}) },
      })
      mediaItems.value = form.mediaUrls.map(url => ({ url, contentType: form.mediaTypes[url] || 'image/jpeg', name: url.split('/').pop() }))
    } catch { router.replace(backPath()); return }
  }
  snapshot()
  ready.value = true
}

async function saveEntry() {
  if (saving.value) return
  if (!form.title.trim()) { ElMessage.warning('请填写日记标题'); return }
  if (uploadingImages.value) { ElMessage.warning('照片上传完成后再保存'); return }
  saving.value = true
  try {
    const payload = { title: form.title, content: form.content, entryDate: form.entryDate, mood: form.mood, weatherCode: form.weatherCode, location: form.location, tags: [...form.tags], mediaUrls: [...new Set(form.mediaUrls)] }
    if (entryId.value) {
      await updateDiaryEntry(entryId.value, payload)
      diaryFeedState.entries = diaryFeedState.entries.map(entry => String(entry.id) === String(entryId.value)
        ? { ...entry, ...payload, mediaTypes: { ...entry.mediaTypes, ...form.mediaTypes } }
        : entry)
    } else {
      await createDiaryEntry(payload)
      diaryFeedState.savedAt = 0
    }
    saved.value = true
    ElMessage.success(entryId.value ? '日记已更新' : '日记已保存')
    await router.push(backPath())
  } catch (error) {
    const message = error?.response?.data?.msg || error?.response?.data?.message || error?.response?.data?.detail
    if (message) ElMessage.error(message)
  } finally { saving.value = false }
}

onBeforeRouteLeave(async () => {
  if (saved.value || (!isDirty.value && !uploadingImages.value)) return true
  try {
    await ElMessageBox.confirm('日记尚未保存，确定离开吗？', '离开编辑', { type: 'warning', confirmButtonText: '离开', cancelButtonText: '继续编辑' })
    return true
  } catch { return false }
})
onMounted(loadEntry)
</script>

<style scoped>
.write-page { --ink:#293b49; --muted:#7c8b95; --line:#e2e9e9; --accent:#258b88; min-height:100%; color:var(--ink); }
.write-header { display:flex; align-items:center; justify-content:space-between; gap:18px; margin-bottom:18px; }
.heading-copy { display:flex; align-items:center; gap:13px; }.back-button { display:grid; width:38px; height:38px; flex:0 0 auto; place-items:center; border:1px solid var(--line); border-radius:6px; background:#fff; color:#5d7278; font-size:17px; cursor:pointer; }.back-button:hover { border-color:#b7d6d2; color:var(--accent); }
.eyebrow { color:#839398; font-size:9px; font-weight:700; letter-spacing:1px; }.write-header h1 { margin:3px 0 3px; font-size:24px; font-weight:700; line-height:1.2; }.write-header p { margin:0; color:var(--muted); font-size:11px; }.write-header :deep(.el-button) { min-height:38px; padding:0 16px; border-color:var(--accent); border-radius:5px; background:var(--accent); }
.editor-layout { display:grid; grid-template-columns:minmax(0,1fr) 270px; align-items:start; gap:15px; }
.editor-main,.editor-aside { min-width:0; border:1px solid var(--line); border-radius:7px; background:#fff; }
.editor-main { padding:22px 24px 18px; }.title-input :deep(.el-input__wrapper) { padding:0 0 12px; border-radius:0; box-shadow:0 1px 0 #e9eeee; }.title-input :deep(.el-input__inner) { height:48px; color:#30474d; font-size:23px; font-weight:680; }.title-input :deep(.el-input__count) { background:transparent; }
.content-input { margin-top:15px; }.content-input :deep(.el-textarea__inner) { padding:8px 0; border:0; box-shadow:none; color:#42585e; font-size:14px; line-height:1.95; resize:vertical; }.content-input :deep(.el-input__count) { right:0; bottom:-4px; background:transparent; }
.photo-section { margin-top:20px; padding-top:16px; border-top:1px solid #edf1f1; }.section-title { display:flex; align-items:center; gap:7px; margin-bottom:12px; color:#536b70; font-size:12px; font-weight:650; }.section-title .el-icon { color:var(--accent); font-size:15px; }
.editor-aside { padding:18px 17px; }.aside-section h2 { margin:0 0 15px; color:#40575d; font-size:13px; font-weight:680; }.aside-section :deep(.el-form-item) { display:block; margin-bottom:14px; }.aside-section :deep(.el-form-item__label) { padding:0; color:#74858a; font-size:10px; line-height:22px; }.aside-section :deep(.el-date-editor),.aside-section :deep(.el-select),.aside-section :deep(.el-input) { width:100%; }.aside-section :deep(.el-input__wrapper),.aside-section :deep(.el-select__wrapper) { min-height:35px; box-shadow:0 0 0 1px #dce5e6 inset; }
.weather-picker { display:flex; width:100%; align-items:center; gap:7px; }.weather-picker>img { width:28px; height:28px; flex:0 0 auto; object-fit:contain; }.weather-picker :deep(.el-select) { width:auto; min-width:0; flex:1; }.weather-option { display:flex; align-items:center; gap:8px; }.weather-option img { width:25px; height:25px; object-fit:contain; }.weather-option small { margin-left:auto; color:#a0aaa4; font-size:10px; }
.tag-section { padding-top:16px; border-top:1px solid #edf1f1; }.tag-editor { display:flex; flex-wrap:wrap; gap:6px; }.tag-editor :deep(.el-tag) { border-color:#d7e8e4; background:#f3f8f7; color:#557b75; }.tag-entry { display:flex; gap:6px; margin-top:9px; }.tag-entry :deep(.el-button) { width:35px; flex:0 0 auto; padding:0; }.aside-hint { display:flex; align-items:center; gap:7px; margin-top:18px; padding:10px; border-radius:5px; background:#f3f8f7; color:#829491; font-size:10px; }.aside-hint .el-icon { color:var(--accent); }
@media(max-width:780px) { .editor-layout { grid-template-columns:1fr; }.editor-aside { display:grid; grid-template-columns:1fr 1fr; gap:0 18px; }.aside-hint { align-self:end; }.editor-main { padding:18px; } }
@media(max-width:480px) { .write-header h1 { font-size:21px; }.write-header p { max-width:210px; }.write-header :deep(.el-button) { padding:0 11px; }.heading-copy { gap:9px; }.back-button { width:34px; height:34px; }.editor-aside { grid-template-columns:1fr; }.aside-hint { margin-top:10px; } }
</style>

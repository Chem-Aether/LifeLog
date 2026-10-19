<template>
  <section v-if="place" class="place-panel">
    <header>
      <div><span>PLACE RECORD</span><h2>地点详情</h2></div>
      <el-button circle text aria-label="关闭地点详情" @click="$emit('close')">×</el-button>
    </header>
    <div class="place-scroll">
      <div class="place-cover" :class="{ empty: !coverUrl }">
        <el-image
          v-if="coverUrl && !imageFailed"
          :src="coverUrl"
          :preview-src-list="imageUrls"
          :initial-index="activeIndex"
          :alt="`${place.placeName}，第 ${activeIndex + 1} 张图片`"
          fit="contain"
          hide-on-click-modal
          @error="imageFailed = true"
        />
        <span v-if="!coverUrl || imageFailed">{{ (place.placeName || '地').slice(0, 1) }}</span>
        <template v-if="imageUrls.length > 1">
          <el-button circle aria-label="上一张图片" class="cover-nav previous" @click="changeImage(-1)"><el-icon><ArrowLeft /></el-icon></el-button>
          <el-button circle aria-label="下一张图片" class="cover-nav next" @click="changeImage(1)"><el-icon><ArrowRight /></el-icon></el-button>
          <span class="cover-count">{{ activeIndex + 1 }} / {{ imageUrls.length }}</span>
        </template>
      </div>
      <div class="place-title">
        <div><h3>{{ place.placeName || '未命名地点' }}</h3><p>{{ regionName }}</p></div>
        <el-tag :type="place.visitType === 'transit' ? 'warning' : 'success'" effect="dark">
          {{ place.visitType === 'transit' ? '途经地' : '旅行地' }}
        </el-tag>
      </div>
      <el-descriptions :column="1" border class="place-fields">
        <el-descriptions-item label="地点类型"><span class="category-detail"><img :src="categoryIcon.iconUrl" alt="">{{ categoryLabel }}</span></el-descriptions-item>
        <el-descriptions-item label="到访日期">{{ place.visitDate || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="地图坐标">{{ coordinateText }}</el-descriptions-item>
      </el-descriptions>
      <section class="travel-note">
        <h4>旅行心得</h4>
        <p>{{ place.note || '还没有记录旅行心得' }}</p>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { mediaUrl } from '@/modules/travel/apis/media.js'
import { getFootprintRegion } from '@/modules/travel/utils/footprintRegion.js'
import { getFootprintCategoryIcon, getFootprintCategoryLabel } from '@/modules/travel/stores/footprintCategories.js'

const props = defineProps({ place: { type: Object, default: null } })
defineEmits(['close'])
const activeIndex = ref(0)
const imageFailed = ref(false)
const regionName = computed(() => getFootprintRegion(props.place).regionName || '地区未知')
const categoryLabel = computed(() => getFootprintCategoryLabel(props.place?.locationType))
const categoryIcon = computed(() => getFootprintCategoryIcon(props.place?.locationType))
const imageUrls = computed(() => (Array.isArray(props.place?.mediaIds) ? props.place.mediaIds : [])
  .slice(0, 9).map(mediaUrl).filter(Boolean))
const coverUrl = computed(() => imageUrls.value[activeIndex.value] || '')
function changeImage(direction) {
  activeIndex.value = (activeIndex.value + direction + imageUrls.value.length) % imageUrls.value.length
  imageFailed.value = false
}
const coordinateText = computed(() => {
  if (props.place?.longitude == null || props.place?.longitude === '' || props.place?.latitude == null || props.place?.latitude === '') {
    return '未定位（已计入统计）'
  }
  const longitude = Number(props.place?.longitude)
  const latitude = Number(props.place?.latitude)
  return Number.isFinite(longitude) && Number.isFinite(latitude)
    ? `${longitude.toFixed(6)}, ${latitude.toFixed(6)}`
    : '未定位（已计入统计）'
})
watch(() => [props.place?.footprintId, imageUrls.value.join('|')], () => {
  activeIndex.value = 0
  imageFailed.value = false
})
</script>

<style scoped>
.place-panel { height: 100%; color: #dcecef; }
header { height: 78px; padding: 0 18px 0 22px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid rgba(142, 210, 220, .14); box-sizing: border-box; }
header span { color: #53e2b4; font-size: 10px; }
header h2 { margin: 3px 0 0; font-size: 20px; letter-spacing: 0; }
header :deep(.el-button) { color: #aac4ca; font-size: 24px; }
.place-scroll { height: calc(100% - 78px); padding: 18px; overflow-x: hidden; overflow-y: auto; box-sizing: border-box; }
.place-cover { position: relative; width: 100%; aspect-ratio: 16 / 10; display: grid; overflow: hidden; place-items: center; border: 1px solid rgba(111, 196, 209, .18); border-radius: 5px; background: #061b20; }
.place-cover :deep(.el-image) { width: 100%; height: 100%; cursor: zoom-in; }
.place-cover :deep(.el-image__inner) { object-fit: contain; }
.place-cover span { color: #8be7ca; font-size: 48px; font-weight: 700; }
.place-cover .cover-count { position: absolute; right: 9px; bottom: 9px; padding: 4px 7px; border-radius: 3px; background: rgba(3, 17, 20, .72); color: #f1fafb; font-size: 11px; }
.place-cover .cover-nav { position: absolute; top: 50%; z-index: 1; color: #f1fafb; background: rgba(3, 17, 20, .68); border-color: rgba(255,255,255,.18); transform: translateY(-50%); }
.place-cover .cover-nav.previous { left: 9px; }
.place-cover .cover-nav.next { right: 9px; }
.place-title { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin: 18px 0 14px; }
.place-title h3 { margin: 0; color: #f1fafb; font-size: 22px; letter-spacing: 0; }
.place-title p { margin: 5px 0 0; color: #7f9da5; font-size: 12px; }
.place-fields :deep(.el-descriptions__body), .place-fields :deep(.el-descriptions__table) { background: transparent; }
.place-fields :deep(.el-descriptions__label.el-descriptions__cell.is-bordered-label) { width: 90px; background: rgba(17, 45, 59, .64); color: #7f9ea6; }
.place-fields :deep(.el-descriptions__content.el-descriptions__cell.is-bordered-content) { background: rgba(8, 26, 38, .48); color: #d9e8eb; overflow-wrap: anywhere; }
.place-fields :deep(.el-descriptions__cell) { border-color: rgba(113, 184, 195, .16) !important; }
.category-detail { display: inline-flex; align-items: center; gap: 7px; }
.category-detail img { width: 22px; height: 22px; object-fit: contain; }
.travel-note { margin-top: 14px; padding: 15px 16px; border: 1px solid rgba(113, 184, 195, .16); border-radius: 5px; background: rgba(8, 26, 38, .48); }
.travel-note h4 { margin: 0 0 9px; color: #7f9ea6; font-size: 12px; font-weight: 500; }
.travel-note p { margin: 0; color: #d9e8eb; font-size: 13px; line-height: 1.75; overflow-wrap: anywhere; white-space: pre-wrap; }
</style>

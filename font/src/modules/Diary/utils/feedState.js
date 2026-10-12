import { reactive } from 'vue'

export const diaryFeedState = reactive({
  entries: [],
  page: 1,
  total: 0,
  scrollTop: 0,
  keyword: '',
  year: null,
  expandedIds: [],
  savedAt: 0,
  restoreOnReturn: false,
  editFromFeed: false,
})

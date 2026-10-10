export default [
  {
    path: '/diary',
    name: 'Diary',
    component: () => import('../views/DiaryLayout.vue'),
    redirect: '/diary/home',
    children: [
      { path: 'home', name: 'DiaryHome', component: () => import('../views/DiaryOverview.vue') },
      { path: 'feed', name: 'DiaryFeed', component: () => import('../views/DiaryHome.vue') },
      { path: 'manage', name: 'DiaryManage', component: () => import('../views/DiaryHome.vue') },
      { path: 'write', name: 'DiaryWrite', component: () => import('../views/DiaryWrite.vue') },
      { path: 'write/:id', name: 'DiaryEdit', component: () => import('../views/DiaryWrite.vue') },
    ],
  },
]

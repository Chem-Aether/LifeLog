export default [
  {
    path: '/bill',
    name: 'Bill',
    component: () => import('../main.vue'),
    redirect: '/bill/home',
    children: [
      { path: 'home', component: () => import('../views/Home.vue') },
      { path: 'statistics', component: () => import('../views/Statistics.vue') },
      { path: 'query', component: () => import('../views/bills.vue') },
      { path: 'categories', component: () => import('../views/CategoryManagement.vue') },
      { path: 'account', component: () => import('../views/TaskRelease.vue') },
      { path: 'account/:id', name: 'BillAccountDetail', component: () => import('../views/AccountDetail.vue') },
    ],
  },
]

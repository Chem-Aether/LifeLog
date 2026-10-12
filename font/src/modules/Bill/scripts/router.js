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
      { path: 'query/edit/:id?', name: 'BillRecordEdit', component: () => import('../views/bills.vue') },
      { path: 'query/import', name: 'BillRecordImport', component: () => import('../views/BillImport.vue') },
      { path: 'categories', component: () => import('../views/CategoryManagement.vue') },
      { path: 'categories/edit/:id?', name: 'BillCategoryEdit', component: () => import('../views/CategoryManagement.vue') },
      { path: 'account', component: () => import('../views/TaskRelease.vue') },
      { path: 'account/edit/:id?', name: 'BillAccountEdit', component: () => import('../views/TaskRelease.vue') },
      { path: 'account/:id', name: 'BillAccountDetail', component: () => import('../views/AccountDetail.vue') },
    ],
  },
]

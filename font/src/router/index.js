import { createRouter, createWebHistory } from 'vue-router'


const moduleFiles = import.meta.glob('../modules/**/router.js', { eager: true })
let allModuleRoutes = []

for (const path in moduleFiles) {
    const mod = moduleFiles[path].default || []
    allModuleRoutes = allModuleRoutes.concat(mod)
}

const routes = [
    { path: '/', redirect: '/login' },
    { path: '/login', component: () => import('@/system/Login.vue') },
    { path: '/register', component: () => import('@/system/Register.vue') },
    { path: '/exit', beforeEnter: () => { localStorage.removeItem('token'); localStorage.removeItem('username'); return '/login' } },

    // 首页布局
    { path: '/home', name: 'SysHome', component: () => import('@/system/SysMain.vue') },

    ...allModuleRoutes
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach(to => {
    const publicPaths = ['/login', '/register']
    if (!publicPaths.includes(to.path) && !localStorage.getItem('token')) return '/login'
})

export default router

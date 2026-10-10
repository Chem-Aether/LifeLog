export default [
    {
        path: '/travel',
        name: 'Travel',
        component: () => import('../views/Board/MainBoard.vue'),
    },

    {
        path: '/travel/admin',
        name: 'TravelAdmin',
        component: () => import('../views/main.vue'),
        redirect: '/travel/admin/trainTicket',
        children: [
            { path: 'trainTicket', component: () => import('../views/admin/trainTicket.vue'), },
            { path: 'flightTicket', component: () => import('../views/admin/flightTicket.vue'), },
            { path: 'footprint', component: () => import('../views/admin/footprint.vue'), },
        ]
    }
]

export default [
    {
        path: '/travel',
        name: 'Travel',
        redirect: '/travel/admin',
    },
    {
        path: '/travel/map',
        name: 'TravelMap',
        component: () => import('../views/Board/MainBoard.vue'),
    },

    {
        path: '/travel/admin',
        name: 'TravelAdmin',
        component: () => import('../views/main.vue'),
        redirect: '/travel/admin/trainTicket',
        children: [
            { path: 'trainTicket', component: () => import('../views/admin/trainTicket.vue'), },
            { path: 'trainTicket/edit/:id?', name: 'TravelTrainTicketEdit', component: () => import('../views/admin/trainTicket.vue'), },
            { path: 'flightTicket', component: () => import('../views/admin/flightTicket.vue'), },
            { path: 'flightTicket/edit/:id?', name: 'TravelFlightTicketEdit', component: () => import('../views/admin/flightTicket.vue'), },
            { path: 'footprint', component: () => import('../views/admin/footprint.vue'), },
            { path: 'footprint/edit/:id?', name: 'TravelFootprintEdit', component: () => import('../views/admin/footprint.vue'), },
            { path: 'album', name: 'TravelCityAlbum', component: () => import('../views/admin/cityAlbum.vue'), },
            { path: 'statistics', name: 'TravelStatistics', component: () => import('../views/admin/statistics.vue'), },
        ]
    }
]

import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from './stores/auth';

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/dashboard' },
  {
    path: '/login',
    name: 'login',
    component: () => import('./views/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('./views/DashboardView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/caregiver/events/:eventId',
    name: 'event-detail',
    component: () => import('./views/EventDetailView.vue'),
    props: true,
    meta: { requiresAuth: true },
  },
  {
    path: '/caregiver/elders/:elderId',
    name: 'care-profile',
    component: () => import('./views/CareProfileView.vue'),
    props: true,
    meta: { requiresAuth: true },
  },
  {
    // Spec § Required MVP Frontend Behavior：照顧者端 care-recipient 詳情路由
    // alias 至既有 CareProfileView，將 careRecipientId 對映成 elderId prop。
    path: '/caregiver/recipients/:careRecipientId',
    name: 'care-recipient-profile',
    component: () => import('./views/CareProfileView.vue'),
    props: (route) => ({ elderId: route.params.careRecipientId }),
    meta: { requiresAuth: true },
  },
  {
    // Spec § Required MVP Frontend Behavior：被照顧者主畫面（4 大按鈕）
    path: '/recipient',
    name: 'recipient-home',
    component: () => import('./views/RecipientView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/workflows/:id',
    name: 'workflow-detail',
    component: () => import('./views/WorkflowDetailView.vue'),
    props: true,
    meta: { requiresAuth: true },
  },
  {
    path: '/sla',
    name: 'sla-dashboard',
    component: () => import('./views/SlaDashboardView.vue'),
    meta: { requiresAuth: true },
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  auth.initFromStorage();
  const requiresAuth = to.meta.requiresAuth !== false;
  if (requiresAuth && !auth.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    return { path: '/dashboard' };
  }
  return true;
});

export default router;

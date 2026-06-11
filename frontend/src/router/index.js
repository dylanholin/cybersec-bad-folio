import { createRouter, createWebHistory } from 'vue-router'

/**
 * Décode le payload d'un JWT sans vérification de signature côté client.
 * La vraie vérification est faite côté serveur (JwtAuthenticationFilter + hasRole).
 * Cette fonction ne sert qu'à améliorer l'UX du routeur (pas de sécurité).
 */
function getRoleFromToken(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.role || null
  } catch {
    return null
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('@/views/HomeView.vue') },
    { path: '/login', component: () => import('@/views/LoginView.vue') },
    { path: '/register', component: () => import('@/views/RegisterView.vue') },
    { path: '/profile/:id', component: () => import('@/views/ProfileView.vue') },
    { path: '/project/:id', component: () => import('@/views/ProjectView.vue') },

    // A01-06 : la protection réelle est côté serveur (hasRole ADMIN sur /api/admin/**)
    // Le guard ci-dessous est un simple garde-fou UX — il décode le JWT au lieu
    // de faire confiance au sessionStorage, ce qui évite les faux positifs si
    // le store est altéré manuellement.
    {
      path: '/admin',
      component: () => import('@/views/AdminView.vue'),
      beforeEnter: (to, from, next) => {
        const token = sessionStorage.getItem('devfolio_token')
        const role = token ? getRoleFromToken(token) : null
        if (role === 'ADMIN') {
          next()
        } else {
          next('/login')
        }
      }
    }
  ]
})

export default router

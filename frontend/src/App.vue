<script setup>
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { LogOut, User, Shield, Home, LogIn, UserPlus, Briefcase, ChevronDown } from '@lucide/vue'

const auth = useAuthStore()
const router = useRouter()

function logout() {
  auth.logout()
  router.push('/login')
}

function userInitial(email) {
  return (email || '?').charAt(0).toUpperCase()
}
</script>

<template>
  <nav class="navbar navbar-expand-lg sticky-top">
    <div class="container">
      <router-link class="navbar-brand d-flex align-items-center gap-2" to="/">
        <Briefcase :size="20" stroke-width="2.5" />
        DevFolio
      </router-link>

      <button class="navbar-toggler" type="button"
              data-bs-toggle="collapse" data-bs-target="#navbarNav"
              aria-controls="navbarNav" aria-expanded="false"
              aria-label="Menu de navigation">
        <span class="navbar-toggler-icon"></span>
      </button>

      <div class="collapse navbar-collapse" id="navbarNav">
        <div class="navbar-nav ms-auto align-items-lg-center">
          <template v-if="auth.token">
            <router-link class="nav-link" to="/">
              <Home :size="16" /> Accueil
            </router-link>

            <div class="nav-item dropdown">
              <a class="nav-link dropdown-toggle d-flex align-items-center gap-2" href="#"
                 id="userDropdown" role="button" data-bs-toggle="dropdown"
                 aria-expanded="false">
                <div class="user-avatar-mini">{{ userInitial(auth.user?.email) }}</div>
                <span class="d-none d-lg-inline">{{ auth.user?.email }}</span>
                <ChevronDown :size="14" />
              </a>
              <ul class="dropdown-menu dropdown-menu-end shadow-sm" aria-labelledby="userDropdown">
                <li>
                  <router-link class="dropdown-item d-flex align-items-center gap-2" :to="`/profile/${auth.user?.id}`">
                    <User :size="14" /> Mon Profil
                  </router-link>
                </li>
                <li v-if="auth.user?.role === 'ADMIN'">
                  <router-link class="dropdown-item d-flex align-items-center gap-2" to="/admin">
                    <Shield :size="14" /> Administration
                  </router-link>
                </li>
                <li><hr class="dropdown-divider"></li>
                <li>
                  <a class="dropdown-item d-flex align-items-center gap-2 text-danger" href="#" @click.prevent="logout">
                    <LogOut :size="14" /> Déconnexion
                  </a>
                </li>
              </ul>
            </div>
          </template>

          <template v-else>
            <router-link class="nav-link" to="/login">
              <LogIn :size="16" /> Connexion
            </router-link>
            <router-link class="nav-link nav-link-cta" to="/register">
              <UserPlus :size="16" /> Inscription
            </router-link>
          </template>
        </div>
      </div>
    </div>
  </nav>

  <main class="container py-4 flex-grow-1">
    <router-view v-slot="{ Component }">
      <transition name="page" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </main>

  <footer class="footer">
    <div class="container d-flex flex-column flex-md-row justify-content-between align-items-center">
      <span>DevFolio — Portfolio étudiant sécurisé</span>
      <span>Projet pédagogique OWASP Top 10 2025</span>
    </div>
  </footer>
</template>

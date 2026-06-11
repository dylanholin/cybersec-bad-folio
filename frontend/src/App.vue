<script setup>
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const router = useRouter()

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <nav class="navbar navbar-expand-lg navbar-dark bg-dark">
    <div class="container">
      <router-link class="navbar-brand" to="/">DevFolio</router-link>
      <div class="navbar-nav ms-auto">
        <template v-if="auth.token">
          <router-link class="nav-link" to="/">Accueil</router-link>
          <router-link class="nav-link" :to="`/profile/${auth.user?.id}`">Mon Profil</router-link>
          <router-link v-if="auth.user?.role === 'ADMIN'" class="nav-link" to="/admin">Admin</router-link>
          <a class="nav-link" href="#" @click.prevent="logout">Déconnexion</a>
        </template>
        <template v-else>
          <router-link class="nav-link" to="/login">Connexion</router-link>
          <router-link class="nav-link" to="/register">Inscription</router-link>
        </template>
      </div>
    </div>
  </nav>
  <div class="container mt-4">
    <router-view />
  </div>
</template>

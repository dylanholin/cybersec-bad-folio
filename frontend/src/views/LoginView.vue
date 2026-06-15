<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import api from '@/services/api'
import { LogIn } from '@lucide/vue'

const email = ref('')
const password = ref('')
const error = ref('')
const router = useRouter()
const auth = useAuthStore()

async function login() {
  try {
    error.value = ''
    const { data } = await api.post('/auth/login', {
      email: email.value,
      password: password.value
    })
    auth.setToken(data.token, data.user)
    router.push('/')
  } catch (e) {
    error.value = e.response?.data?.error || 'Erreur de connexion'
  }
}
</script>

<template>
  <div class="row justify-content-center">
    <div class="col-md-6 col-lg-5">
      <div class="card">
        <div class="card-body p-4">
          <h2 class="card-title text-center mb-4">
            <LogIn :size="24" class="me-1" /> Connexion
          </h2>
          <div v-if="error" class="alert alert-danger" role="alert" id="login-error">
            {{ error }}
          </div>
          <form @submit.prevent="login" novalidate>
            <div class="mb-3">
              <label for="login-email" class="form-label">Email</label>
              <input id="login-email" v-model="email" type="email"
                     class="form-control" required
                     :aria-describedby="error ? 'login-error' : undefined"
                     placeholder="nom@exemple.com">
            </div>
            <div class="mb-4">
              <label for="login-password" class="form-label">Mot de passe</label>
              <input id="login-password" v-model="password" type="password"
                     class="form-control" required
                     :aria-describedby="error ? 'login-error' : undefined"
                     placeholder="Votre mot de passe">
            </div>
            <button type="submit" class="btn btn-primary w-100">
              Se connecter
            </button>
          </form>
          <p class="text-center mt-3 mb-0">
            Pas de compte ? <router-link to="/register">S'inscrire</router-link>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import api from '@/services/api'
import { UserPlus } from '@lucide/vue'

const email = ref('')
const password = ref('')
const error = ref('')
const router = useRouter()
const auth = useAuthStore()

// A04-04 corrigé : indicateur de force côté client
const passwordStrength = computed(() => {
  const p = password.value
  if (p.length === 0) return { label: '', level: 0, class: '' }
  let score = 0
  if (p.length >= 12) score++
  if (p.length >= 16) score++
  if (/[A-Z]/.test(p)) score++
  if (/[0-9]/.test(p)) score++
  if (/[^A-Za-z0-9]/.test(p)) score++
  if (score <= 1) return { label: 'Faible', level: 1, class: 'bg-danger' }
  if (score <= 3) return { label: 'Moyen', level: 2, class: 'bg-warning' }
  return { label: 'Fort', level: 3, class: 'bg-success' }
})

async function register() {
  try {
    error.value = ''
    const { data } = await api.post('/auth/register', {
      email: email.value,
      password: password.value
    })
    auth.setToken(data.token, data.user)
    router.push('/')
  } catch (e) {
    error.value = e.response?.data?.error || 'Erreur lors de l\'inscription'
  }
}
</script>

<template>
  <div class="row justify-content-center">
    <div class="col-md-6 col-lg-5">
      <div class="card">
        <div class="card-body p-4">
          <h2 class="card-title text-center mb-4">
            <UserPlus :size="24" class="me-1" /> Inscription
          </h2>
          <div v-if="error" class="alert alert-danger" role="alert" id="register-error">
            {{ error }}
          </div>
          <form @submit.prevent="register" novalidate>
            <div class="mb-3">
              <label for="register-email" class="form-label">Email</label>
              <input id="register-email" v-model="email" type="email"
                     class="form-control" required
                     :aria-describedby="error ? 'register-error' : undefined"
                     placeholder="nom@exemple.com">
            </div>
            <div class="mb-4">
              <label for="register-password" class="form-label">Mot de passe</label>
              <input id="register-password" v-model="password" type="password"
                     class="form-control" minlength="12" required
                     :aria-describedby="password ? 'password-help' : (error ? 'register-error' : undefined)"
                     placeholder="Minimum 12 caractères">
              <div v-if="password" class="mt-2">
                <div class="progress" style="height: 6px" role="progressbar"
                     :aria-valuenow="passwordStrength.level" aria-valuemin="0" aria-valuemax="3">
                  <div class="progress-bar" :class="passwordStrength.class"
                       :style="{ width: (passwordStrength.level / 3 * 100) + '%' }"></div>
                </div>
                <small id="password-help" class="text-muted">
                  Force : {{ passwordStrength.label }}, 12 car. min., majuscule, chiffre, spécial
                </small>
              </div>
            </div>
            <button type="submit" class="btn btn-primary w-100">
              S'inscrire
            </button>
          </form>
          <p class="text-center mt-3 mb-0">
            Déjà un compte ? <router-link to="/login">Se connecter</router-link>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/services/api'
import { FolderOpen } from '@lucide/vue'

const route = useRoute()
const user = ref({})
const projects = ref([])
const loading = ref(true)

// Fallback initiale si pas d'avatar
const userInitial = computed(() => {
  const email = user.value.email || ''
  return email.charAt(0).toUpperCase() || '?'
})

onMounted(async () => {
  const userId = route.params.id
  const { data: userData } = await api.get(`/users/${userId}`)
  user.value = userData

  const { data: projectData } = await api.get('/projects')
  projects.value = projectData.filter(p => p.ownerId == userId)
  loading.value = false
})
</script>

<template>
  <div>
    <div v-if="loading" class="text-center py-5">
      <div class="spinner"></div>
      <p class="text-muted mt-2">Chargement du profil…</p>
    </div>

    <div v-else>
      <div class="row">
        <div class="col-md-4 mb-4">
          <div class="card">
            <div class="card-body text-center">
              <img v-if="user.avatarUrl" :src="user.avatarUrl"
                   class="rounded-circle mb-3" width="80" height="80" alt="Avatar">
              <div v-else class="avatar-fallback mx-auto mb-3">
                {{ userInitial }}
              </div>
              <h3>{{ user.email }}</h3>
              <span class="badge bg-secondary">{{ user.role }}</span>
            </div>
          </div>
        </div>

        <div class="col-md-8">
          <div class="card mb-4">
            <div class="card-body">
              <h5 class="card-title mb-2">Biographie</h5>
              <div v-if="user.bio" class="bio" style="white-space: pre-line">{{ user.bio }}</div>
              <p v-else class="text-muted fst-italic">Aucune biographie renseignée.</p>
            </div>
          </div>

          <h5>Projets</h5>
          <div class="row">
            <div v-for="project in projects" :key="project.id" class="col-md-6 mb-3">
              <div class="card h-100">
                <div class="card-body">
                  <h6>{{ project.title }}</h6>
                  <p class="text-muted mb-2">{{ project.description }}</p>
                  <router-link :to="`/project/${project.id}`" class="btn btn-sm btn-primary">
                    Voir
                  </router-link>
                </div>
              </div>
            </div>
          </div>

          <div v-if="projects.length === 0" class="text-center py-4">
            <FolderOpen :size="36" class="text-muted mb-2" />
            <p class="text-muted">Aucun projet publié.</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

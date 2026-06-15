<script setup>
import { ref, onMounted } from 'vue'
import api from '@/services/api'
import { Search, FolderOpen } from '@lucide/vue'

const projects = ref([])
const searchQuery = ref('')
const loading = ref(true)

onMounted(async () => {
  await loadProjects()
})

async function loadProjects() {
  loading.value = true
  const { data } = await api.get('/projects')
  projects.value = data
  loading.value = false
}

async function search() {
  if (!searchQuery.value.trim()) {
    await loadProjects()
    return
  }
  loading.value = true
  const { data } = await api.get('/search/projects', {
    params: { q: searchQuery.value }
  })
  projects.value = data
  loading.value = false
}
</script>

<template>
  <div>
    <div class="hero">
      <div class="container">
        <h1>DevFolio</h1>
        <p>Découvrez les projets des étudiants et partagez les vôtres</p>
      </div>
    </div>

    <div class="row justify-content-center">
      <div class="col-lg-8">
        <div class="search-box">
          <div class="d-flex gap-3 align-items-stretch">
            <input v-model="searchQuery" type="text" class="form-control"
                   placeholder="Rechercher un projet..." @keyup.enter="search"
                   aria-label="Rechercher un projet">
            <button class="btn btn-primary flex-shrink-0" @click="search">
              <Search :size="16" /> Rechercher
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="text-center py-5">
      <div class="spinner"></div>
      <p class="text-muted mt-2">Chargement des projets…</p>
    </div>

    <div v-else>
      <div class="row">
        <div v-for="project in projects" :key="project.id" class="col-md-4 mb-4">
          <div class="card h-100">
            <div class="card-body">
              <h5 class="card-title">{{ project.title }}</h5>
              <p class="card-text text-muted">{{ project.description }}</p>
              <router-link :to="`/project/${project.id}`" class="btn btn-sm btn-primary">
                Voir le projet
              </router-link>
            </div>
          </div>
        </div>
      </div>

      <div v-if="projects.length === 0" class="text-center py-5">
        <FolderOpen :size="48" class="text-muted mb-2" />
        <p class="text-muted">Aucun projet trouvé.</p>
      </div>
    </div>
  </div>
</template>

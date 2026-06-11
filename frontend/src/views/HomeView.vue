<script setup>
import { ref, onMounted } from 'vue'
import api from '@/services/api'

const projects = ref([])
const searchQuery = ref('')

onMounted(async () => {
  await loadProjects()
})

async function loadProjects() {
  const { data } = await api.get('/projects')
  projects.value = data
}

async function search() {
  if (!searchQuery.value.trim()) {
    await loadProjects()
    return
  }
  const { data } = await api.get('/search/projects', {
    params: { q: searchQuery.value }
  })
  projects.value = data
}
</script>

<template>
  <div>
    <h1 class="mb-4">Portfolios</h1>

    <div class="input-group mb-4">
      <input v-model="searchQuery" type="text" class="form-control"
             placeholder="Rechercher un projet..." @keyup.enter="search">
      <button class="btn btn-outline-primary" @click="search">Rechercher</button>
    </div>

    <div class="row">
      <div v-for="project in projects" :key="project.id" class="col-md-4 mb-4">
        <div class="card h-100">
          <div class="card-body">
            <h5 class="card-title">{{ project.title }}</h5>
            <p class="card-text">{{ project.description }}</p>
            <router-link :to="`/project/${project.id}`" class="btn btn-sm btn-primary">
              Voir le projet
            </router-link>
          </div>
        </div>
      </div>
    </div>

    <div v-if="projects.length === 0" class="text-center text-muted">
      Aucun projet trouvé.
    </div>
  </div>
</template>

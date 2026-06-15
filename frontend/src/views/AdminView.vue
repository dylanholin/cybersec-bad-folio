<script setup>
import { ref, onMounted } from 'vue'
import api from '@/services/api'
import { Shield, Users } from '@lucide/vue'

const users = ref([])
const loading = ref(true)

onMounted(async () => {
  const { data } = await api.get('/admin/users')
  users.value = data
  loading.value = false
})
</script>

<template>
  <div>
    <h2 class="d-flex align-items-center gap-2 mb-1">
      <Shield :size="24" /> Administration
    </h2>
    <p class="text-muted mb-4">Liste de tous les utilisateurs inscrits</p>

    <div v-if="loading" class="text-center py-5">
      <div class="spinner"></div>
      <p class="text-muted mt-2">Chargement…</p>
    </div>

    <div v-else>
      <div class="card">
        <div class="table-responsive">
          <table class="table table-striped mb-0">
            <thead>
              <tr>
                <th>ID</th>
                <th>Email</th>
                <th>Rôle</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="user in users" :key="user.id">
                <td>{{ user.id }}</td>
                <td>{{ user.email }}</td>
                <td>
                  <span :class="user.role === 'ADMIN' ? 'badge bg-danger' : 'badge bg-primary'">
                    {{ user.role }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="users.length === 0" class="text-center py-4">
        <Users :size="36" class="text-muted mb-2" />
        <p class="text-muted">Aucun utilisateur.</p>
      </div>
    </div>
  </div>
</template>

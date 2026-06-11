<script setup>
import { ref, onMounted } from 'vue'
import api from '@/services/api'

// 🔴 A01-06 : route "protégée" par JS seulement — contournable via DevTools
const users = ref([])

onMounted(async () => {
  // 🔴 A01-03 : endpoint admin sans vérification côté serveur
  const { data } = await api.get('/admin/users')
  users.value = data
})
</script>

<template>
  <div>
    <h2>Administration</h2>
    <p class="text-muted">Panel d'administration — liste de tous les utilisateurs</p>

    <table class="table table-striped">
      <thead>
        <tr>
          <th>ID</th>
          <th>Email</th>
          <th>Rôle</th>
          <th>Mot de passe (hash)</th>
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
          <!-- 🔴 : hash MD5 du mot de passe affiché dans l'UI -->
          <td><code>{{ user.password }}</code></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    // 🔴 A07-03 : JWT dans localStorage — accessible au JavaScript de la page (XSS)
    token: localStorage.getItem('devfolio_token') || null,
    user: JSON.parse(localStorage.getItem('devfolio_user') || 'null'),
  }),
  actions: {
    setToken(token, user) {
      this.token = token
      this.user = user
      // 🔴 A07-03 : persistance en localStorage
      localStorage.setItem('devfolio_token', token)
      localStorage.setItem('devfolio_user', JSON.stringify(user))
    },
    logout() {
      this.token = null
      this.user = null
      localStorage.removeItem('devfolio_token')
      localStorage.removeItem('devfolio_user')
      // 🔴 A07-05 : pas d'appel serveur pour invalider le token
    }
  }
})

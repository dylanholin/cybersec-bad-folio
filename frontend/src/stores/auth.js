import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: sessionStorage.getItem('devfolio_token') || null,
    user: JSON.parse(sessionStorage.getItem('devfolio_user') || 'null'),
  }),
  actions: {
    setToken(token, user) {
      this.token = token
      this.user = user
      // sessionStorage : token détruit à la fermeture de l'onglet
      sessionStorage.setItem('devfolio_token', token)
      sessionStorage.setItem('devfolio_user', JSON.stringify(user))
    },
    async logout() {
      // A07-05 : invalider le token côté serveur (blacklist)
      if (this.token) {
        try {
          await fetch('/api/auth/logout', {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${this.token}` }
          })
        } catch {
          // Même si l'appel échoue, on nettoie le côté client
        }
      }
      this.token = null
      this.user = null
      sessionStorage.removeItem('devfolio_token')
      sessionStorage.removeItem('devfolio_user')
    }
  }
})

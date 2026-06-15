import axios from 'axios'

// En dev : accès direct au backend. En production Docker : nginx reverse proxy sur /api
const baseURL = import.meta.env.PROD
  ? '/api'
  : 'http://localhost:8080/api'

const api = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Interceptor JWT : utilise sessionStorage (pas localStorage)
api.interceptors.request.use(config => {
  const token = sessionStorage.getItem('devfolio_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export default api

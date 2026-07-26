import axios from 'axios'

const client = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api' })
client.interceptors.request.use((config) => {
  const token = localStorage.getItem('wakebook_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export const api = {
  popularBooks: (params) => client.get('/books/popular', { params }),
  todayBook: () => client.get('/books/today'),
  randomBook: () => client.get('/books/random'),
  keywords: (isbn) => client.post('/ai/keywords', { isbn }),
  recommendations: (payload) => client.post('/recommendations', payload),
  compare: (popularBook, hiddenBook) => client.post('/recommendations/compare', { popularBook, hiddenBook }),
  login: (payload) => client.post('/auth/login', payload),
  signup: (payload) => client.post('/auth/signup', payload),
}

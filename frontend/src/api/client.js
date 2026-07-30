import axios from 'axios'

const client = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/api' })
client.interceptors.request.use((config) => {
  const token = localStorage.getItem('wakebook_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
client.interceptors.response.use((response) => response.data.data)

export const api = {
  popularBooks: (params) => client.get('/books/popular', { params }),
  searchBooks: (params) => client.get('/books/search', { params }),
  bookDetail: (isbn, region) => client.get(`/books/${isbn}`, { params: region ? { region } : undefined }),
  todayBook: (libraryCode) => client.get('/books/today', { params: { libraryCode } }),
  randomBook: (libraryCode) => client.get('/books/random', { params: { libraryCode } }),
  keywords: (isbn) => client.post('/ai/keywords', { isbn }),
  recommendations: (payload) => client.post('/recommendations', payload),
  compare: (popularBook, hiddenBook) => client.post('/recommendations/compare', { popularBook, hiddenBook }),
  explore: (payload) => client.post('/recommendations/explore', payload),
  login: (payload) => client.post('/auth/login', payload),
  signup: (payload) => client.post('/auth/signup', payload),
  me: () => client.get('/auth/me'),
  bookshelves: () => client.get('/bookshelves'),
  createBookshelf: (payload) => client.post('/bookshelves', payload),
  updateBookshelf: (shelfId, payload) => client.patch(`/bookshelves/${shelfId}`, payload),
  deleteBookshelf: (shelfId) => client.delete(`/bookshelves/${shelfId}`),
  addBookshelfBook: (shelfId, payload) => client.post(`/bookshelves/${shelfId}/books`, payload),
  updateBookshelfBook: (shelfId, bookId, status) => client.patch(`/bookshelves/${shelfId}/books/${bookId}`, { status }),
  deleteBookshelfBook: (shelfId, bookId) => client.delete(`/bookshelves/${shelfId}/books/${bookId}`),
  librarianDashboard: () => client.get('/librarian/dashboard'),
  generateCuration: (payload) => client.post('/librarian/curations/generate', payload),
  saveCuration: (payload) => client.post('/librarian/curations', payload),
  curations: (params) => client.get('/librarian/curations', { params }),
  curation: (id) => client.get(`/librarian/curations/${id}`),
  updateCuration: (id, payload) => client.patch(`/librarian/curations/${id}`, payload),
  deleteCuration: (id) => client.delete(`/librarian/curations/${id}`),
  uploadHiddenBooks: (libraryCode, libraryName, file) => {
    const formData = new FormData()
    formData.append('libraryCode', libraryCode)
    formData.append('libraryName', libraryName)
    formData.append('file', file)
    return client.post('/librarian/hidden-books/upload', formData)
  },
}

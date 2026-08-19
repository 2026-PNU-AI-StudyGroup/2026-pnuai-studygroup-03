import axios from 'axios'

export const TOKEN_KEY = 'wakebook_token'
export const USER_KEY = 'wakebook_user'
export const LIBRARY_KEY = 'wakebook_library'

/** 토큰이 만료되면 화면 어디서든 로그아웃 처리를 할 수 있도록 알린다. */
export const UNAUTHORIZED_EVENT = 'wakebook:unauthorized'

const client = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/api' })

client.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

client.interceptors.response.use(
  (response) => response.data.data,
  (error) => {
    const status = error.response?.status
    const url = error.config?.url || ''
    const isLoginAttempt = url.includes('/auth/login') || url.includes('/auth/signup')
    if (status === 401 && !isLoginAttempt) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    }
    return Promise.reject(error)
  },
)

/** 백엔드 공통 오류 응답({ code, message })에서 사용자에게 보여 줄 문구를 꺼낸다. */
export function errorMessage(error, fallback = '요청을 처리하지 못했습니다.') {
  if (error?.response?.data?.message) return error.response.data.message
  if (error?.code === 'ERR_NETWORK') return '서버에 연결하지 못했습니다. 백엔드가 실행 중인지 확인해 주세요.'
  return fallback
}

export const api = {
  // 3. 도서 탐색
  popularBooks: (params) => client.get('/books/popular', { params }),
  searchBooks: (params) => client.get('/books/search', { params }),
  bookDetail: (isbn, region) => client.get(`/books/${isbn}`, { params: region ? { region } : undefined }),
  todayBook: (libraryCode) => client.get('/books/today', { params: { libraryCode } }),
  randomBook: (libraryCode) => client.get('/books/random', { params: { libraryCode } }),
  libraries: () => client.get('/libraries'),
  libraryDirectory: (region) => client.get('/libraries/directory', { params: { region } }),
  // 저장된 후보군을 그대로 읽는다. AI도 정보나루도 호출하지 않는다.
  libraryHiddenBooks: (libraryCode, page = 1, size = 12) =>
    client.get(`/libraries/${libraryCode}/hidden-books`, { params: { page, size } }),
  // 후보군 산출은 수 분이 걸린다. 접수만 하고 jobId를 돌려받아 진행 상태를 따로 조회한다.
  collectHiddenBooks: (libraryCode) => client.post(`/libraries/${libraryCode}/hidden-books`),
  hiddenBookJob: (jobId) => client.get(`/hidden-book-jobs/${jobId}`),

  // 4. AI 추천
  keywords: (isbn) => client.post('/ai/keywords', { isbn }),
  recommendations: (payload) => client.post('/recommendations', payload),
  compare: (popularBook, hiddenBook) => client.post('/recommendations/compare', { popularBook, hiddenBook }),
  explore: (payload) => client.post('/recommendations/explore', payload),
  publicCurations: (params) => client.get('/curations', { params }),
  publicCuration: (id) => client.get(`/curations/${id}`),

  // 2. 인증
  login: (payload) => client.post('/auth/login', payload),
  signup: (payload) => client.post('/auth/signup', payload),
  me: () => client.get('/auth/me'),

  // 5. 나의 책장
  bookshelves: () => client.get('/bookshelves'),
  createBookshelf: (payload) => client.post('/bookshelves', payload),
  updateBookshelf: (shelfId, payload) => client.patch(`/bookshelves/${shelfId}`, payload),
  deleteBookshelf: (shelfId) => client.delete(`/bookshelves/${shelfId}`),
  addBookshelfBook: (shelfId, isbn, status = 'WISH') =>
    client.post(`/bookshelves/${shelfId}/books`, { isbn, status }),
  updateBookshelfBook: (shelfId, bookId, status) => client.patch(`/bookshelves/${shelfId}/books/${bookId}`, { status }),
  deleteBookshelfBook: (shelfId, bookId) => client.delete(`/bookshelves/${shelfId}/books/${bookId}`),

  // 6. 사서
  librarianDashboard: () => client.get('/librarian/dashboard'),
  generateCuration: (payload) => client.post('/librarian/curations/generate', payload),
  saveCuration: (payload) => client.post('/librarian/curations', payload),
  curations: (params) => client.get('/librarian/curations', { params }),
  curation: (id) => client.get(`/librarian/curations/${id}`),
  // PATCH는 books 목록으로 도서 구성을 통째로 교체한다. books를 빼면 큐레이션 도서가 전부 삭제된다.
  updateCuration: (id, payload) => client.patch(`/librarian/curations/${id}`, payload),
  deleteCuration: (id) => client.delete(`/librarian/curations/${id}`),
  // 대상 도서관은 로그인한 사서의 소속으로 서버가 정한다. 응답은 접수된 작업(jobId)이다.
  uploadHiddenBooks: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return client.post('/librarian/hidden-books/upload', formData)
  },
}

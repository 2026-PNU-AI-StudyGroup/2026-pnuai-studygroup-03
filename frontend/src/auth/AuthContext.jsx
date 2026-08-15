import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { Library, LogIn } from 'lucide-react'
import { api, TOKEN_KEY, UNAUTHORIZED_EVENT, USER_KEY } from '../api/client'

const AuthContext = createContext(null)

export const useAuth = () => useContext(AuthContext)

function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(readStoredUser)
  const [expired, setExpired] = useState(false)

  const clearSession = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    setUser(null)
  }, [])

  // 토큰 만료(401)는 화면마다 따로 처리하지 않고 여기서 한 번에 로그아웃시킨다.
  useEffect(() => {
    const handleUnauthorized = () => {
      setUser((current) => {
        if (current) setExpired(true)
        return null
      })
    }
    window.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized)
  }, [])

  // 저장된 토큰이 아직 살아 있는지 확인하고, 소속 도서관 등 최신 정보로 갱신한다.
  useEffect(() => {
    if (!localStorage.getItem(TOKEN_KEY)) return
    api.me()
      .then((me) => {
        localStorage.setItem(USER_KEY, JSON.stringify(me))
        setUser(me)
      })
      .catch(() => undefined)
  }, [])

  const persistLogin = useCallback((response) => {
    localStorage.setItem(TOKEN_KEY, response.accessToken)
    localStorage.setItem(USER_KEY, JSON.stringify(response.user))
    setExpired(false)
    setUser(response.user)
    return response.user
  }, [])

  const value = useMemo(() => ({
    user,
    expired,
    dismissExpired: () => setExpired(false),
    login: async (credentials) => persistLogin(await api.login(credentials)),
    register: async (account) => {
      await api.signup(account)
      return persistLogin(await api.login({ email: account.email, password: account.password }))
    },
    logout: clearSession,
  }), [user, expired, persistLogin, clearSession])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function RequireAuth({ children }) {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}

export function RequireLibrarian({ children }) {
  const { user } = useAuth()
  const location = useLocation()

  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (user.role !== 'LIBRARIAN') {
    return (
      <section className="restricted">
        <Library size={38} />
        <h1>사서 전용 공간입니다.</h1>
        <p>사서 계정으로 로그인하면 장서 등록과 큐레이션 도구를 사용할 수 있어요.</p>
        <Link className="primary-link" to="/signup">사서로 회원가입하기 <LogIn size={16} /></Link>
      </section>
    )
  }
  return children
}

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, LIBRARY_KEY } from '../api/client'
import { useAuth } from '../auth/AuthContext'

const LibraryContext = createContext(null)

export const useLibrary = () => useContext(LibraryContext)

function readStoredLibrary() {
  try {
    return JSON.parse(localStorage.getItem(LIBRARY_KEY) || 'null')
  } catch {
    return null
  }
}

/**
 * 추천·오늘의 책은 모두 "그 도서관에 등록된 잠자는 도서 후보군"에서 나온다.
 * 이용자가 도서관 코드를 외울 수는 없으므로, 후보군이 실제로 등록된 도서관 목록을 받아 고르게 한다.
 */
export function LibraryProvider({ children }) {
  const { user } = useAuth()
  const [library, setLibraryState] = useState(readStoredLibrary)
  const [libraries, setLibraries] = useState([])
  const [loading, setLoading] = useState(true)

  const setLibrary = useCallback((next) => {
    if (next) localStorage.setItem(LIBRARY_KEY, JSON.stringify(next))
    else localStorage.removeItem(LIBRARY_KEY)
    setLibraryState(next)
  }, [])

  const reloadLibraries = useCallback(async () => {
    try {
      const list = (await api.libraries()) || []
      setLibraries(list)
      return list
    } catch {
      setLibraries([])
      return []
    } finally {
      setLoading(false)
    }
  }, [])

  // 첫 방문자에게 "도서관부터 고르세요"를 요구하지 않도록, 후보가 가장 많은 도서관을 기본값으로 잡는다.
  useEffect(() => {
    reloadLibraries().then((list) => {
      if (!list.length) return
      setLibraryState((current) => {
        if (current && list.some((item) => item.libraryCode === current.libraryCode)) {
          return current
        }
        const fallback = { libraryCode: list[0].libraryCode, libraryName: list[0].libraryName }
        localStorage.setItem(LIBRARY_KEY, JSON.stringify(fallback))
        return fallback
      })
    })
  }, [reloadLibraries])

  // 사서는 자기 소속 도서관을 기본값으로 쓴다.
  useEffect(() => {
    if (user?.role === 'LIBRARIAN' && user.libraryCode && library?.libraryCode !== user.libraryCode) {
      setLibrary({ libraryCode: user.libraryCode, libraryName: user.libraryName || user.libraryCode })
    }
  }, [user, library?.libraryCode, setLibrary])

  const value = useMemo(() => ({
    library,
    libraryCode: library?.libraryCode || '',
    libraries,
    loading,
    setLibrary,
    reloadLibraries,
    /** 선택한 도서관의 후보군을 무엇으로 산출했는지(CSV_UPLOAD / LIBRARY_API / DEMO_SEED). */
    source: libraries.find((item) => item.libraryCode === library?.libraryCode)?.source || null,
  }), [library, libraries, loading, setLibrary, reloadLibraries])

  return <LibraryContext.Provider value={value}>{children}</LibraryContext.Provider>
}

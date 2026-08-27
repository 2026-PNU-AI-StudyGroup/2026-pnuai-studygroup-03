import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'

const BookshelfContext = createContext(null)

export const useBookshelf = () => useContext(BookshelfContext)

export const READING_STATUS = [
  { code: 'WISH', label: '읽고 싶은 책' },
  { code: 'READING', label: '읽는 중' },
  { code: 'COMPLETED', label: '다 읽은 책' },
  { code: 'REVISIT', label: '나중에 다시 볼 책' },
]

export const statusLabel = (code) =>
  READING_STATUS.find((status) => status.code === code)?.label || code

/**
 * 책장 상태를 한 곳에서 들고 있어야 추천 목록·상세·책장 화면이 같은 "담김" 표시를 공유할 수 있다.
 * 서버 응답을 그대로 상태로 쓰기 때문에, 저장에 실패하면 화면에도 담기지 않는다.
 */
export function BookshelfProvider({ children }) {
  const { user } = useAuth()
  const [shelves, setShelves] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    if (!user) {
      setShelves([])
      return
    }
    setLoading(true)
    try {
      setShelves(await api.bookshelves())
      setError('')
    } catch (err) {
      setError(errorMessage(err, '책장을 불러오지 못했습니다.'))
    } finally {
      setLoading(false)
    }
  }, [user])

  useEffect(() => { reload() }, [reload])

  const value = useMemo(() => {
    const defaultShelf = shelves.find((shelf) => shelf.type === 'DEFAULT') || shelves[0] || null

    const findSaved = (isbn) => {
      for (const shelf of shelves) {
        const book = shelf.books?.find((item) => item.isbn === isbn)
        if (book) return { shelfId: shelf.id, book }
      }
      return null
    }

    return {
      shelves,
      loading,
      error,
      defaultShelf,
      reload,
      findSaved,
      saveBook: async (isbn, status = 'WISH', shelfId = defaultShelf?.id) => {
        if (!shelfId) throw new Error('책장이 없습니다.')
        await api.addBookshelfBook(shelfId, isbn, status)
        await reload()
      },
      updateStatus: async (shelfId, bookId, status) => {
        await api.updateBookshelfBook(shelfId, bookId, status)
        await reload()
      },
      removeBook: async (shelfId, bookId) => {
        await api.deleteBookshelfBook(shelfId, bookId)
        await reload()
      },
      createShelf: async (payload) => {
        await api.createBookshelf(payload)
        await reload()
      },
      updateShelf: async (shelfId, payload) => {
        await api.updateBookshelf(shelfId, payload)
        await reload()
      },
      removeShelf: async (shelfId) => {
        await api.deleteBookshelf(shelfId)
        await reload()
      },
    }
  }, [shelves, loading, error, reload])

  return <BookshelfContext.Provider value={value}>{children}</BookshelfContext.Provider>
}

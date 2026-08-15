import { useState } from 'react'
import { Check, Heart } from 'lucide-react'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { READING_STATUS, statusLabel, useBookshelf } from '../bookshelf/BookshelfContext'

/**
 * 추천 결과·상세에서 실제로 나의 책장에 담는다. 담을 때 읽기 상태를 고를 수 있어야
 * "읽고 싶은 책"으로만 쌓이지 않는다.
 */
export default function SaveToShelfButton({ isbn, onMessage }) {
  const { user } = useAuth()
  const { findSaved, saveBook, removeBook, defaultShelf } = useBookshelf()
  const [busy, setBusy] = useState(false)
  const [choosing, setChoosing] = useState(false)
  const saved = findSaved(isbn)

  const run = async (action) => {
    setBusy(true)
    try {
      await action()
      onMessage?.('')
    } catch (err) {
      onMessage?.(errorMessage(err, '책장 저장에 실패했습니다.'))
    } finally {
      setBusy(false)
      setChoosing(false)
    }
  }

  const toggle = () => {
    if (!user) return onMessage?.('책장에 담으려면 먼저 로그인해 주세요.')
    if (!defaultShelf) return onMessage?.('책장을 찾지 못했습니다. 잠시 후 다시 시도해 주세요.')
    if (saved) return run(() => removeBook(saved.shelfId, saved.book.id))
    setChoosing((current) => !current)
  }

  return (
    <div className="save-wrap">
      <button
        type="button"
        className={saved ? 'save saved' : 'save'}
        onClick={toggle}
        disabled={busy}
        aria-pressed={Boolean(saved)}
        title={saved ? `책장에서 빼기 (${statusLabel(saved.book.status)})` : '나의 책장에 담기'}
      >
        <Heart size={17} fill={saved ? 'currentColor' : 'none'} />
      </button>

      {choosing && !saved && (
        <div className="save-status-menu">
          <b>어떤 상태로 담을까요?</b>
          {READING_STATUS.map((status) => (
            <button key={status.code} onClick={() => run(() => saveBook(isbn, status.code))} disabled={busy}>
              <Check size={13} /> {status.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

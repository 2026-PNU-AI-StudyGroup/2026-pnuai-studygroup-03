import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { BookMarked, Plus, Save, Trash2 } from 'lucide-react'
import { errorMessage } from '../api/client'
import { READING_STATUS, statusLabel, useBookshelf } from '../bookshelf/BookshelfContext'
import Cover from '../components/Cover'
import Notice from '../components/Notice'

export default function Bookshelf() {
  const { shelves, loading, error, updateStatus, removeBook, createShelf, removeShelf } = useBookshelf()
  const [activeShelfId, setActiveShelfId] = useState(null)
  const [statusTab, setStatusTab] = useState('WISH')
  const [message, setMessage] = useState('')
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    if (!activeShelfId && shelves.length) setActiveShelfId(shelves[0].id)
  }, [shelves, activeShelfId])

  const activeShelf = shelves.find((shelf) => shelf.id === activeShelfId) || shelves[0] || null
  const books = activeShelf?.books?.filter((book) => book.status === statusTab) || []

  const handleCreate = async (event) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    try {
      await createShelf({ name: form.get('name'), description: form.get('description') || null })
      setCreating(false)
      setMessage('')
    } catch (err) {
      setMessage(errorMessage(err, '컬렉션을 만들지 못했습니다.'))
    }
  }

  const run = async (action, failureMessage) => {
    try {
      await action()
      setMessage('')
    } catch (err) {
      setMessage(errorMessage(err, failureMessage))
    }
  }

  return (
    <section className="page bookshelf">
      <div className="section-heading">
        <div>
          <p className="eyebrow"><span /> MY LIBRARY</p>
          <h1 className="page-title">나의 책장</h1>
          <p className="lead">마음에 남은 책들을 나만의 속도로 기록하세요.</p>
        </div>
        <button className="secondary-button" onClick={() => setCreating(true)}><Plus size={16} /> 새 컬렉션</button>
      </div>

      <Notice>{error}</Notice>
      <Notice>{message}</Notice>
      {loading && <p className="lead">책장을 불러오는 중입니다.</p>}

      {shelves.length > 1 && (
        <div className="shelf-collections">
          {shelves.map((shelf) => (
            <button
              key={shelf.id}
              className={shelf.id === activeShelf?.id ? 'active' : ''}
              onClick={() => setActiveShelfId(shelf.id)}
            >
              {shelf.name} <b>{shelf.bookCount}</b>
            </button>
          ))}
        </div>
      )}

      {activeShelf && (
        <>
          {activeShelf.type === 'CUSTOM' && (
            <button
              className="delete-button shelf-delete"
              onClick={() => {
                if (window.confirm(`“${activeShelf.name}” 컬렉션을 삭제할까요?`)) {
                  setActiveShelfId(null)
                  run(() => removeShelf(activeShelf.id), '컬렉션을 삭제하지 못했습니다.')
                }
              }}
            >
              <Trash2 size={14} /> 이 컬렉션 삭제
            </button>
          )}

          <div className="shelf-tabs">
            {READING_STATUS.map((status) => (
              <button
                key={status.code}
                className={statusTab === status.code ? 'active' : ''}
                onClick={() => setStatusTab(status.code)}
              >
                {status.label}
                <b>{activeShelf.books?.filter((book) => book.status === status.code).length || 0}</b>
              </button>
            ))}
          </div>

          <div className="shelf-list">
            {books.length ? books.map((book) => (
              <article key={book.id}>
                <Cover book={book} />
                <div>
                  <h2><Link to={`/books/${book.isbn}`}>{book.title}</Link></h2>
                  <div className="status-select">
                    <label>읽기 상태</label>
                    <select
                      value={book.status}
                      onChange={(event) =>
                        run(() => updateStatus(activeShelf.id, book.id, event.target.value), '읽기 상태를 변경하지 못했습니다.')}
                    >
                      {READING_STATUS.map((status) => (
                        <option key={status.code} value={status.code}>{status.label}</option>
                      ))}
                    </select>
                  </div>
                </div>
                <button onClick={() => run(() => removeBook(activeShelf.id, book.id), '도서를 삭제하지 못했습니다.')}>
                  책장에서 빼기
                </button>
              </article>
            )) : (
              <div className="empty">
                <BookMarked size={32} />
                <p>‘{statusLabel(statusTab)}’에 담긴 책이 아직 없어요.</p>
                <Link to="/popular">새 책 탐색하기</Link>
              </div>
            )}
          </div>
        </>
      )}

      {creating && (
        <div className="modal-backdrop">
          <form className="curation-modal" onSubmit={handleCreate}>
            <div>
              <span className="badge">새 컬렉션</span>
              <button type="button" className="modal-close" onClick={() => setCreating(false)}>×</button>
            </div>
            <h2>컬렉션 만들기</h2>
            <label>이름<input name="name" required maxLength={100} placeholder="예: 올여름에 읽을 책" /></label>
            <label>설명<textarea name="description" maxLength={500} placeholder="어떤 책을 모을 컬렉션인가요? (선택)" /></label>
            <button className="primary-link" type="submit"><Save size={15} /> 만들기</button>
          </form>
        </div>
      )}
    </section>
  )
}

import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, Dices, MapPin } from 'lucide-react'
import { api, errorMessage } from '../api/client'
import { useLibrary } from '../library/LibraryContext'
import Cover from '../components/Cover'
import LibraryPicker from '../components/LibraryPicker'
import Notice from '../components/Notice'
import SaveToShelfButton from '../components/SaveToShelfButton'

export default function Today({ random = false }) {
  const navigate = useNavigate()
  const { libraryCode, library } = useLibrary()
  const [book, setBook] = useState(null)
  const [message, setMessage] = useState('')
  const [shelfMessage, setShelfMessage] = useState('')
  const [loading, setLoading] = useState(false)

  const loadBook = useCallback(async () => {
    if (!libraryCode) return
    setLoading(true)
    setMessage('')
    try {
      setBook(random ? await api.randomBook(libraryCode) : await api.todayBook(libraryCode))
    } catch (err) {
      setBook(null)
      setMessage(errorMessage(err, '도서관 추천을 불러오지 못했습니다.'))
    } finally {
      setLoading(false)
    }
  }, [libraryCode, random])

  useEffect(() => { loadBook() }, [loadBook])

  return (
    <section className="center-page">
      <p className="eyebrow"><span /> {random ? 'SERENDIPITY' : "TODAY'S WAKEBOOK"}</p>
      <h1>{random ? '오늘은 이 책을\n만나볼까요?' : '오늘 깨워낸\n한 권의 책'}</h1>
      <p className="lead">
        {random
          ? '예상하지 못한 책이 새로운 생각의 시작이 될 수 있어요.'
          : '아직 많은 독자를 만나지 못한, 좋은 책을 소개합니다.'}
      </p>

      <LibraryPicker />

      {!libraryCode && <p className="lead">도서관을 선택하면 그 도서관의 잠자는 책을 보여 드려요.</p>}
      {libraryCode && (
        <Link className="text-link" to={`/libraries/${libraryCode}`}>
          이 도서관의 잠자는 책 전체 보기 <ArrowRight size={13} />
        </Link>
      )}
      <Notice>{message}</Notice>
      <Notice>{shelfMessage}</Notice>
      {loading && <p className="lead">추천 도서를 불러오는 중입니다.</p>}

      {book && (
        <article className="today-card">
          <Cover book={book} />
          <div>
            <span className="badge">{library?.libraryName}의 잠자는 책</span>
            <h2>{book.title}</h2>
            {book.reason && <blockquote>“{book.reason}”</blockquote>}
            {book.callNumber && (
              <p className="shelf-location">
                <MapPin size={13} />
                <span>{book.shelfName ? `${book.shelfName} · ` : ''}<b>{book.callNumber}</b></span>
              </p>
            )}
            {book.keywords?.length > 0 && (
              <div className="tags">{book.keywords.map((keyword) => <span key={keyword}>#{keyword}</span>)}</div>
            )}
            <div className="today-actions">
              <button className="primary-link" onClick={() => navigate(`/books/${book.isbn}`)}>
                이 책 자세히 보기 <ArrowRight size={16} />
              </button>
              <SaveToShelfButton isbn={book.isbn} onMessage={setShelfMessage} />
              <Link className="secondary-button" to={`/explore/${book.isbn}`}>이 책에서 더 찾기 <ArrowRight size={15} /></Link>
              {random && (
                <button className="secondary-button" onClick={loadBook} disabled={loading}>
                  다른 책 만나기 <Dices size={16} />
                </button>
              )}
            </div>
          </div>
        </article>
      )}
    </section>
  )
}

import { useEffect, useState } from 'react'
import { ArrowLeft, ArrowRight, BookOpen, CalendarDays } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { api, errorMessage } from '../api/client'
import Cover from '../components/Cover'
import Notice from '../components/Notice'

function formatDate(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric',
  }).format(new Date(value))
}

export default function CurationDetail() {
  const { curationId } = useParams()
  const [curation, setCuration] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    setLoading(true)
    setCuration(null)
    api.publicCuration(curationId)
      .then((data) => {
        if (!active) return
        setCuration(data)
        setError('')
      })
      .catch((requestError) => {
        if (!active) return
        setError(errorMessage(requestError, '큐레이션을 불러오지 못했습니다.'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [curationId])

  return (
    <section className="page public-curation-detail">
      <Link className="curation-back" to="/curations"><ArrowLeft size={16} /> 큐레이션 목록</Link>
      <Notice>{error}</Notice>
      {loading && <p className="curation-loading">큐레이션을 불러오는 중입니다.</p>}

      {curation && (
        <>
          <div className="curation-detail-heading">
            <p className="eyebrow"><span /> LIBRARIAN&apos;S SELECTION</p>
            <h1>{curation.title}</h1>
            {curation.description && <p>{curation.description}</p>}
            <div>
              <span><BookOpen size={14} /> 추천 도서 {curation.bookCount}권</span>
              <span><CalendarDays size={14} /> {formatDate(curation.createdAt)}</span>
            </div>
          </div>

          <div className="curation-detail-books">
            {curation.books.map((book, index) => (
              <Link className="curation-detail-book" to={`/books/${book.isbn}`} key={book.id || book.isbn}>
                <div className="curation-book-cover">
                  <Cover book={book} />
                  <span>{String(index + 1).padStart(2, '0')}</span>
                </div>
                <div>
                  <small>ISBN {book.isbn}</small>
                  <h2>{book.title}</h2>
                  {book.comment && <p>{book.comment}</p>}
                  <b>도서 정보 보기 <ArrowRight size={14} /></b>
                </div>
              </Link>
            ))}
          </div>

          {!curation.books.length && (
            <div className="empty-curations">
              <BookOpen size={30} />
              <p>이 큐레이션에는 아직 등록된 도서가 없습니다.</p>
            </div>
          )}
        </>
      )}
    </section>
  )
}

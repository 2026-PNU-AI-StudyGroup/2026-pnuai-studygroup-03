import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowRight, ChevronRight, MapPin } from 'lucide-react'
import { api, errorMessage } from '../api/client'
import { useLibrary } from '../library/LibraryContext'
import { SOURCE_LABEL } from '../library/useHiddenBookJob'
import Cover from '../components/Cover'
import Notice from '../components/Notice'
import SaveToShelfButton from '../components/SaveToShelfButton'

const PAGE_SIZE = 12

/**
 * 도서관의 잠자는 도서 전체 목록. 저장된 후보군을 그대로 보여 주므로
 * AI도 정보나루도 호출하지 않는다. 후보군이 제대로 만들어졌는지 확인하는 용도로도 쓴다.
 */
export default function LibraryBooks() {
  const { libraryCode } = useParams()
  const { libraries, setLibrary } = useLibrary()
  const [page, setPage] = useState(1)
  const [result, setResult] = useState(null)
  const [message, setMessage] = useState('')
  const [shelfMessage, setShelfMessage] = useState('')
  const [loading, setLoading] = useState(true)

  const summary = libraries.find((item) => item.libraryCode === libraryCode)

  useEffect(() => {
    setLoading(true)
    api.libraryHiddenBooks(libraryCode, page, PAGE_SIZE)
      .then((data) => { setResult(data); setMessage('') })
      .catch((err) => { setResult(null); setMessage(errorMessage(err, '잠자는 도서 목록을 불러오지 못했습니다.')) })
      .finally(() => setLoading(false))
  }, [libraryCode, page])

  const books = result?.content || []

  return (
    <section className="page">
      <p className="eyebrow"><span /> SLEEPING BOOKS</p>
      <h1 className="page-title">{summary?.libraryName || '이 도서관'}의<br />잠자는 책들.</h1>
      <p className="lead">
        대출이 적었지만 소개할 만한 책들입니다. 서가 위치를 확인해 바로 빌리러 갈 수 있어요.
        {summary && <> · 기준: {SOURCE_LABEL[summary.source] || summary.source}</>}
      </p>

      {summary && (
        <div className="library-books-actions">
          <button
            className="secondary-button"
            onClick={() => setLibrary({ libraryCode: summary.libraryCode, libraryName: summary.libraryName })}
          >
            이 도서관으로 탐색하기
          </button>
          <Link className="text-link" to="/today">오늘의 잠자는 책 보기 <ChevronRight size={13} /></Link>
        </div>
      )}

      <Notice>{message}</Notice>
      <Notice>{shelfMessage}</Notice>
      {loading && <p className="lead">목록을 불러오는 중입니다.</p>}
      {!loading && !books.length && !message && (
        <p className="lead">이 도서관에는 아직 등록된 잠자는 도서가 없습니다.</p>
      )}

      <div className="library-books-grid">
        {books.map((book) => (
          <article className="library-book-card" key={book.isbn}>
            <SaveToShelfButton isbn={book.isbn} onMessage={setShelfMessage} />
            <Cover book={book} />
            <div>
              <h2>{book.title}</h2>
              <p>{book.author || '저자 정보 없음'}</p>
              {book.callNumber && (
                <p className="shelf-location">
                  <MapPin size={13} />
                  <span>{book.shelfName ? `${book.shelfName} · ` : ''}<b>{book.callNumber}</b></span>
                </p>
              )}
              {(book.reason || book.description) && (
                <p className="library-book-summary">{book.reason || book.description}</p>
              )}
              {book.keywords?.length > 0 && (
                <div className="tags">{book.keywords.map((keyword) => <span key={keyword}>#{keyword}</span>)}</div>
              )}
              <div className="recommend-links">
                <Link to={`/books/${book.isbn}`}>도서 상세 <ChevronRight size={14} /></Link>
                <Link to={`/explore/${book.isbn}`}>이 책에서 더 찾기 <ArrowRight size={14} /></Link>
              </div>
            </div>
          </article>
        ))}
      </div>

      {result && result.totalPages > 1 && (
        <div className="pagination">
          <button disabled={page <= 1} onClick={() => setPage((current) => current - 1)}>이전</button>
          <span>{page} / {result.totalPages} · 전체 {result.totalElements}권</span>
          <button disabled={page >= result.totalPages} onClick={() => setPage((current) => current + 1)}>다음</button>
        </div>
      )}
    </section>
  )
}

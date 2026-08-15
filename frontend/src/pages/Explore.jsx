import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useLibrary } from '../library/LibraryContext'
import Cover from '../components/Cover'
import LibraryPicker from '../components/LibraryPicker'
import Notice from '../components/Notice'
import RecommendCard from '../components/RecommendCard'

// API 명세 4.4의 type 값.
const EXPLORE_TYPES = [
  { code: 'SIMILAR_TOPIC', label: '비슷한 주제', hint: '같은 관심사를 조금 다른 각도에서' },
  { code: 'SAME_MOOD', label: '같은 분위기', hint: '읽는 느낌이 비슷한 책' },
  { code: 'EASIER', label: '더 쉬운 책', hint: '부담 없이 읽기 좋은 책' },
  { code: 'DEEPER', label: '더 깊은 책', hint: '한 걸음 더 들어가고 싶을 때' },
  { code: 'OPPOSITE_VIEW', label: '반대 관점', hint: '다른 입장에서 본 이야기' },
]

export default function Explore() {
  const { isbn } = useParams()
  const { user } = useAuth()
  const { libraryCode, library } = useLibrary()

  const [baseBook, setBaseBook] = useState(null)
  const [type, setType] = useState(EXPLORE_TYPES[0].code)
  const [results, setResults] = useState(null)
  const [message, setMessage] = useState('')
  const [shelfMessage, setShelfMessage] = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    api.bookDetail(isbn)
      .then(setBaseBook)
      .catch(() => setBaseBook(null))
  }, [isbn])

  const runExplore = async (nextType) => {
    setType(nextType)
    if (!user) return setMessage('재탐색은 로그인 후 이용할 수 있습니다.')
    if (!libraryCode) return setMessage('도서관을 먼저 선택해 주세요.')

    setLoading(true)
    setMessage('')
    try {
      const response = await api.explore({ isbn, libraryCode, type: nextType })
      setResults(response)
      if (!response.length) {
        setMessage('이 조건에 맞는 다른 잠자는 도서를 찾지 못했습니다.')
      }
    } catch (err) {
      setResults(null)
      setMessage(errorMessage(err, '재탐색 결과를 불러오지 못했습니다.'))
    } finally {
      setLoading(false)
    }
  }

  const selected = EXPLORE_TYPES.find((item) => item.code === type)

  return (
    <section className="page discovery-page">
      <p className="eyebrow"><span /> EXPLORE FURTHER</p>
      <h1 className="page-title">이 책에서<br />한 걸음 더.</h1>
      <p className="lead">읽은 책을 기준으로 방향을 정해, 같은 도서관의 다른 잠자는 책으로 이어 갑니다.</p>

      <div className="recommend-layout">
        <aside className="source-book">
          {baseBook && (
            <>
              <Cover book={baseBook} />
              <div>
                <span>기준 도서</span>
                <h2>{baseBook.title}</h2>
                <p>{baseBook.author}</p>
                <Link className="text-link" to={`/books/${isbn}`}>도서 상세 보기</Link>
              </div>
            </>
          )}
        </aside>

        <div className="selector">
          <LibraryPicker />
          {!user && <p className="login-hint"><Link to="/login">로그인</Link>하면 재탐색을 이용할 수 있어요.</p>}

          <div className="explore-types">
            {EXPLORE_TYPES.map((item) => (
              <button
                key={item.code}
                className={type === item.code ? 'chosen' : ''}
                onClick={() => runExplore(item.code)}
                disabled={loading || !user}
              >
                <b>{item.label}</b>
                <span>{item.hint}</span>
              </button>
            ))}
          </div>

          {loading && <p className="lead">AI가 조건에 맞는 책을 고르는 중입니다.</p>}
          <Notice>{message}</Notice>
        </div>
      </div>

      {results && results.length > 0 && (
        <>
          <div className="result-heading">
            <div>
              <p className="eyebrow"><span /> EXPLORE RESULT</p>
              <h2><em>{selected?.label}</em>으로 이어지는 책</h2>
              <p>{library?.libraryName}의 잠자는 도서 중에서 골랐어요.</p>
            </div>
            <strong>{results.length}권</strong>
          </div>

          <Notice>{shelfMessage}</Notice>

          <div className="recommend-cards">
            {results.map((book) => (
              <RecommendCard
                key={book.isbn}
                book={book}
                metrics={[
                  { label: '조건 관련도', value: book.relevance },
                  { label: '발견 가치', value: book.discoveryValue },
                ]}
                compareTo={`/compare/${isbn}/${book.isbn}`}
                exploreFrom={`/explore/${book.isbn}`}
                onMessage={setShelfMessage}
              />
            ))}
          </div>
        </>
      )}
    </section>
  )
}

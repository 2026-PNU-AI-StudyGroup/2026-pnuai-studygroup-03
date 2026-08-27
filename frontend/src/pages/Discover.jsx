import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowRight, ChevronRight, WandSparkles } from 'lucide-react'
import { api, errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useLibrary } from '../library/LibraryContext'
import Cover from '../components/Cover'
import LibraryPicker from '../components/LibraryPicker'
import Notice from '../components/Notice'
import RecommendCard from '../components/RecommendCard'

// API 명세 4.2의 허용값. 예상 독서 시간(readingTime)은 2026-07-30 명세 개정에서 제거됐다.
const PURPOSES = ['마음의 위로', '새로운 관점', '실용적인 해결책', '깊이 있는 사유']
const MOODS = ['따뜻한', '담백한', '유쾌한', '사색적인']
// 후보군 전체를 쏟아내지 않고 상위 몇 권만 먼저 보여 준다.
const DEFAULT_LIMIT = 6
const MORE_LIMIT = 15

function ChipGroup({ label, items, selected, onSelect, hash }) {
  return (
    <div className="filter">
      <label>{label}</label>
      <div>
        {items.map((item) => (
          <button
            key={item}
            type="button"
            className={selected.includes(item) ? 'chosen' : ''}
            onClick={() => onSelect(item)}
          >
            {hash ? '#' : ''}{item}
          </button>
        ))}
      </div>
    </div>
  )
}

export default function Discover() {
  const { isbn } = useParams()
  const { user } = useAuth()
  const { libraryCode, library } = useLibrary()

  const [baseBook, setBaseBook] = useState(null)
  const [baseError, setBaseError] = useState('')
  const [keywords, setKeywords] = useState([])
  const [keywordError, setKeywordError] = useState('')
  const [selectedKeywords, setSelectedKeywords] = useState([])
  const [purpose, setPurpose] = useState(PURPOSES[0])
  const [mood, setMood] = useState(MOODS[0])
  const [results, setResults] = useState(null)
  const [resultCriteria, setResultCriteria] = useState(null)
  const [visibleLimit, setVisibleLimit] = useState(DEFAULT_LIMIT)
  const [message, setMessage] = useState('')
  const [shelfMessage, setShelfMessage] = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setBaseBook(null)
    setResults(null)
    setResultCriteria(null)
    api.bookDetail(isbn)
      .then((book) => { setBaseBook(book); setBaseError('') })
      .catch((err) => setBaseError(errorMessage(err, '기준 도서 정보를 불러오지 못했습니다.')))
  }, [isbn])

  // 키워드 탐색은 서비스의 진입점이라 비로그인 방문자에게도 보여 준다(추천부터 로그인 필요).
  useEffect(() => {
    setKeywords([])
    setSelectedKeywords([])
    api.keywords(isbn)
      .then(({ keywords: generated }) => {
        const list = generated || []
        setKeywords(list)
        setSelectedKeywords(list.slice(0, 1))
        setKeywordError('')
      })
      .catch((err) => setKeywordError(errorMessage(err, 'AI 키워드를 생성하지 못했습니다.')))
  }, [isbn])

  const toggleKeyword = (keyword) => {
    setSelectedKeywords((current) =>
      current.includes(keyword) ? current.filter((item) => item !== keyword) : [...current, keyword])
  }

  const requestRecommendations = async () => {
    if (!user) return setMessage('추천을 받으려면 먼저 로그인해 주세요.')
    if (!libraryCode) return setMessage('추천을 받을 도서관을 먼저 선택해 주세요.')
    if (!selectedKeywords.length) return setMessage('키워드를 하나 이상 선택해 주세요.')

    setLoading(true)
    setMessage('')
    const requestedCriteria = {
      keywords: [...selectedKeywords],
      purpose,
      mood,
      libraryCode,
      libraryName: library?.libraryName || '선택한 도서관',
    }
    try {
      const response = await api.recommendations({
        isbn,
        libraryCode: requestedCriteria.libraryCode,
        keywords: requestedCriteria.keywords,
        purpose: requestedCriteria.purpose,
        mood: requestedCriteria.mood,
        limit: MORE_LIMIT,
      })
      setResults(response)
      setResultCriteria(requestedCriteria)
      setVisibleLimit(DEFAULT_LIMIT)
      if (!response.length) {
        setMessage(`${requestedCriteria.libraryName}의 후보군에서 선택한 키워드와 독자층에 맞는 잠자는 책을 찾지 못했습니다. 키워드나 독서 조건을 바꿔 다시 시도해 주세요.`)
      }
    } catch (err) {
      setResults(null)
      setMessage(errorMessage(err, '추천을 불러오지 못했습니다.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="page discovery-page">
      <p className="eyebrow"><span /> DISCOVER WITH AI</p>
      <h1 className="page-title">한 권의 인기 책에서<br />나만의 다음 책으로.</h1>

      <Notice>{baseError}</Notice>

      <div className="recommend-layout">
        <aside className="source-book">
          {baseBook ? (
            <>
              <Cover book={baseBook} />
              <div>
                <span>기준 인기 도서</span>
                <h2>{baseBook.title}</h2>
                <p>{baseBook.author}</p>
                <Link className="text-link" to="/popular">다른 책으로 바꾸기 <ChevronRight size={13} /></Link>
              </div>
            </>
          ) : !baseError && <p className="lead">기준 도서를 불러오는 중입니다.</p>}
        </aside>

        <div className="selector">
          <LibraryPicker />

          {!user && (
            <p className="login-hint">
              키워드는 로그인 없이 볼 수 있어요. <Link to="/login">로그인</Link>하면 이 조건에 맞는 잠자는 도서를 추천해 드립니다.
            </p>
          )}

          <Notice>{keywordError}</Notice>

          {keywords.length > 0 && (
            <ChipGroup label="AI 핵심 키워드 (여러 개 선택 가능)" items={keywords} selected={selectedKeywords} onSelect={toggleKeyword} hash />
          )}
          <ChipGroup label="독서 목적" items={PURPOSES} selected={[purpose]} onSelect={setPurpose} />
          <ChipGroup label="원하는 분위기" items={MOODS} selected={[mood]} onSelect={setMood} />

          <div className="condition-summary">
            <WandSparkles size={18} />
            <span>
              <b>{selectedKeywords.map((keyword) => `#${keyword}`).join(' ') || '키워드 미선택'}</b> · {purpose} · {mood}
            </span>
            <button onClick={requestRecommendations} disabled={loading || !user}>
              {loading ? 'AI 추천 생성 중...' : '잠자는 책 추천받기'} <ArrowRight size={15} />
            </button>
          </div>

          <Notice>{message}</Notice>
        </div>
      </div>

      {results && results.length > 0 && (
        <>
          <div className="result-heading">
            <div>
              <p className="eyebrow"><span /> RECOMMENDATION RESULT</p>
              <h2><em>{resultCriteria?.keywords.map((keyword) => `#${keyword}`).join(' ')}</em>에 어울리는 잠자는 책</h2>
              <p>{resultCriteria?.libraryName}에서 관련성은 높지만 대출이 적었던 책을 AI가 골라냈어요.</p>
            </div>
            <strong>{results.length}권 발견</strong>
          </div>

          <Notice>{shelfMessage}</Notice>

          <div className="recommend-cards">
            {results.slice(0, visibleLimit).map((book) => (
              <RecommendCard
                key={book.isbn}
                book={book}
                metrics={[
                  { label: '키워드 관련성', value: book.keywordRelevance },
                  { label: '목적 적합', value: book.purposeMatch },
                  { label: '발견 가치', value: book.discoveryValue },
                ]}
                compareTo={`/compare/${isbn}/${book.isbn}`}
                exploreFrom={`/explore/${book.isbn}`}
                onMessage={setShelfMessage}
              />
            ))}
          </div>

          {visibleLimit === DEFAULT_LIMIT && results.length > DEFAULT_LIMIT && (
            <button className="secondary-button more-button" onClick={() => setVisibleLimit(MORE_LIMIT)}>
              추천 더 보기 <ArrowRight size={15} />
            </button>
          )}

        </>
      )}
    </section>
  )
}

import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Activity,
  ArrowRight,
  CheckCircle2,
  Clock3,
  ExternalLink,
  Radio,
  Sparkles,
  TrendingUp,
} from 'lucide-react'
import { api, errorMessage } from '../api/client'
import { useLibrary } from '../library/LibraryContext'
import Cover from '../components/Cover'
import LibraryPicker from '../components/LibraryPicker'

const SOURCE_ROLE = {
  DISCOVERY: '트렌드 발견',
  EVIDENCE: '뉴스 근거',
  VALIDATION: '국내 관심도 검증',
}

function formatDate(date) {
  if (!date) return ''
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric', weekday: 'short',
  }).format(new Date(`${date}T00:00:00+09:00`))
}

function formatTime(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

function statusMessage(error) {
  const code = error?.response?.data?.code
  if (code === 'TREND_001') {
    return {
      type: 'pending',
      title: '아직 오늘의 추천을 준비하고 있어요.',
      description: '트렌드와 도서의 연결을 검토하고 있습니다. 잠시 후 다시 확인해 주세요.',
    }
  }
  if (code === 'BOOK_001') {
    return {
      type: 'books',
      title: '이 도서관은 추천 후보가 아직 없어요.',
      description: '다른 도서관을 선택하거나 잠자는 책 후보군을 먼저 만들어 주세요.',
    }
  }
  if (code === 'TREND_002' || error?.response?.status === 503) {
    return {
      type: 'unavailable',
      title: '오늘의 트렌드를 불러오지 못했어요.',
      description: '외부 트렌드 수집이 잠시 지연되고 있습니다. 조금 뒤 다시 시도해 주세요.',
    }
  }
  return {
    type: 'error',
    title: '추천을 불러오는 중 문제가 생겼어요.',
    description: errorMessage(error, '잠시 후 다시 시도해 주세요.'),
  }
}

function EmptyState({ state, onRetry }) {
  const Icon = state.type === 'pending' ? Clock3 : state.type === 'books' ? Sparkles : Activity
  return (
    <div className="trend-empty">
      <Icon size={27} />
      <h2>{state.title}</h2>
      <p>{state.description}</p>
      {state.type !== 'books' && (
        <button className="secondary-button" onClick={onRetry}>다시 확인하기</button>
      )}
    </div>
  )
}

function TrendBook({ book }) {
  return (
    <article className="trend-book">
      <Link to={`/books/${book.isbn}`} aria-label={`${book.title} 상세 보기`}>
        <Cover book={book} />
      </Link>
      <div className="trend-book-copy">
        <span>잠자는 책 · 대출 {Number(book.loanCount || 0).toLocaleString()}회</span>
        <h3>{book.title}</h3>
        <p className="trend-book-author">{book.author || '저자 정보 없음'}</p>
        <p className="trend-book-reason"><Sparkles size={13} /> {book.reason}</p>
        <div className="trend-book-links">
          <Link to={`/books/${book.isbn}`}>도서 상세 <ArrowRight size={12} /></Link>
          <Link to={`/explore/${book.isbn}`}>이 책에서 더 찾기 <ArrowRight size={12} /></Link>
        </div>
      </div>
    </article>
  )
}

function TrendCard({ item }) {
  const confirmed = item.validationStatus === 'CONFIRMED'
  return (
    <article className="trend-card">
      <div className="trend-card-head">
        <div className="trend-rank"><b>{String(item.finalRank).padStart(2, '0')}</b><span>TODAY</span></div>
        <div className="trend-topic">
          <div className="trend-meta">
            {confirmed && <span className="confirmed"><CheckCircle2 size={12} /> 국내 검색 상승 확인</span>}
            {item.trafficLabel && <span><TrendingUp size={12} /> Google {item.trafficLabel}</span>}
          </div>
          <h2>{item.displayTopic}</h2>
          <p>{item.contextDescription}</p>
          {item.sourceKeyword && <small>발견 키워드 · {item.sourceKeyword}</small>}
        </div>
      </div>

      <div className="trend-recommendation">
        <p className="eyebrow"><span /> AI BOOK MATCH</p>
        <h3>{item.recommendationTitle}</h3>
        <div className="trend-books">
          {(item.books || []).map((book) => (
            <TrendBook key={book.recommendationId || book.isbn} book={book} />
          ))}
        </div>
      </div>
    </article>
  )
}

export default function Trends() {
  const { libraryCode, library } = useLibrary()
  const [result, setResult] = useState(null)
  const [state, setState] = useState(null)
  const [loading, setLoading] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    setResult(null)
    setState(null)
    if (!libraryCode) return

    let active = true
    setLoading(true)
    api.dailyTrends(libraryCode)
      .then((data) => {
        if (!active) return
        setResult(data)
        setState(null)
      })
      .catch((error) => {
        if (!active) return
        setResult(null)
        setState(statusMessage(error))
      })
      .finally(() => { if (active) setLoading(false) })

    return () => { active = false }
  }, [libraryCode, reloadKey])

  const items = result?.items || []
  const fallback = result?.freshness === 'FALLBACK'

  return (
    <section className="page trends-page">
      <div className="trend-hero">
        <div>
          <p className="eyebrow"><span /> REAL-TIME BOOK CURATION</p>
          <h1 className="page-title">오늘의 관심사에서<br />잠자는 책을 깨우다.</h1>
          <p className="lead">
            지금 주목받는 검색어를 국내 뉴스와 검색 흐름으로 확인하고,<br />선택한 도서관에서 그 맥락을 함께 읽을 책을 찾아드립니다.
          </p>
        </div>
        <div className="trend-live-mark" aria-hidden="true">
          <span><Radio size={20} /> LIVE</span>
          <b>{items.length || '—'}</b>
          <small>오늘의 주제</small>
        </div>
      </div>

      <div className="trend-controls">
        <LibraryPicker />
        {result && (
          <div className="trend-date">
            <span>{formatDate(result.recommendationDate)}</span>
            <small><Clock3 size={12} /> {formatTime(result.generatedAt)} 갱신</small>
          </div>
        )}
      </div>

      {!libraryCode && (
        <div className="trend-empty">
          <Sparkles size={27} />
          <h2>도서관을 먼저 선택해 주세요.</h2>
          <p>그 도서관이 보유한 잠자는 책 안에서 오늘의 트렌드와 연결되는 책을 찾아드려요.</p>
        </div>
      )}

      {fallback && (
        <p className="trend-fallback">
          <Clock3 size={14} /> 오늘 데이터 수집이 지연되어 {formatDate(result.recommendationDate)} 추천을 보여드려요.
        </p>
      )}

      {loading && (
        <div className="trend-loading">
          <Activity className="spin" size={21} />
          <div><b>오늘의 흐름과 책을 연결하고 있어요.</b><span>트렌드 추천을 불러오는 중입니다.</span></div>
        </div>
      )}

      {!loading && state && <EmptyState state={state} onRetry={() => setReloadKey((key) => key + 1)} />}

      {!loading && result && !items.length && (
        <EmptyState
          state={{ type: 'pending', title: '표시할 트렌드 추천이 아직 없어요.', description: '새로운 추천이 준비되면 이곳에 바로 표시됩니다.' }}
          onRetry={() => setReloadKey((key) => key + 1)}
        />
      )}

      {items.length > 0 && (
        <div className="trend-list" aria-label={`${library?.libraryName || result.libraryName} 실시간 트렌드 추천`}>
          {items.map((item) => <TrendCard key={item.trendId} item={item} />)}
        </div>
      )}

      {result?.sources?.length > 0 && (
        <section className="trend-sources">
          <div>
            <p className="eyebrow"><span /> DATA SOURCES</p>
            <h2>발견부터 국내 검증까지</h2>
            <p>검색 흐름 하나만 믿지 않고 서로 다른 근거를 함께 확인합니다.</p>
          </div>
          <div className="source-list">
            {result.sources.map((source) => (
              <a key={source.type} href={source.url} target="_blank" rel="noreferrer">
                <span>{SOURCE_ROLE[source.role] || source.role}</span>
                <b>{source.name}</b>
                <small>{formatTime(source.fetchedAt)} · {source.region}</small>
                <ExternalLink size={13} />
              </a>
            ))}
          </div>
        </section>
      )}
    </section>
  )
}

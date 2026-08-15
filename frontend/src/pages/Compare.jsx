import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Sparkles } from 'lucide-react'
import { api, errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Cover from '../components/Cover'
import Notice from '../components/Notice'

function CompareBook({ book, label }) {
  if (!book) return <article className="compare-book"><span>{label}</span><p className="lead">불러오는 중</p></article>
  return (
    <article className="compare-book">
      <span>{label}</span>
      <Cover book={book} />
      <h2>{book.title}</h2>
      <p>{book.author}</p>
    </article>
  )
}

export default function Compare() {
  const { baseIsbn, hiddenIsbn } = useParams()
  const { user } = useAuth()
  const [books, setBooks] = useState({ base: null, hidden: null })
  const [comparison, setComparison] = useState(null)
  const [message, setMessage] = useState('')

  // 비교 화면의 두 책은 항상 URL의 ISBN으로 실제 조회한다(샘플 도서로 대체하지 않는다).
  useEffect(() => {
    Promise.all([
      api.bookDetail(baseIsbn).catch(() => null),
      api.bookDetail(hiddenIsbn).catch(() => null),
    ]).then(([base, hidden]) => setBooks({ base, hidden }))
  }, [baseIsbn, hiddenIsbn])

  useEffect(() => {
    if (!user) return
    setComparison(null)
    api.compare(baseIsbn, hiddenIsbn)
      .then((data) => { setComparison(data); setMessage('') })
      .catch((err) => setMessage(errorMessage(err, 'AI 비교 분석을 불러오지 못했습니다.')))
  }, [baseIsbn, hiddenIsbn, user])

  return (
    <section className="page compare-page">
      <p className="eyebrow"><span /> BOOK COMPARISON</p>
      <h1 className="page-title">같은 주제, 다른 시선</h1>
      <p className="lead">베스트셀러에서 느낀 관심을 더 넓고 깊게 이어가 보세요.</p>

      {!user && <p className="login-hint">AI 비교 분석은 로그인 후 이용할 수 있습니다.</p>}
      <Notice>{message}</Notice>

      <div className="comparison">
        <CompareBook book={books.base} label="인기 도서" />
        <div className="vs"><span>VS</span><p>AI 분석</p></div>
        <CompareBook book={books.hidden} label="잠자는 도서" />
      </div>

      {comparison ? (
        <>
          <section className="ai-insight">
            <Sparkles />
            <div>
              <span>AI 비교 분석</span>
              <h2>{comparison.difference}</h2>
            </div>
          </section>
          <div className="compare-table">
            <div>
              <b>공통 키워드</b>
              <span>{comparison.commonKeywords?.map((keyword) => `#${keyword}`).join(' ') || '-'}</span>
            </div>
            <div>
              <b>인기 도서 특징</b>
              <span>{comparison.popularBookProfile?.difficulty} · {comparison.popularBookProfile?.style}</span>
            </div>
            <div>
              <b>잠자는 도서 특징</b>
              <span>{comparison.hiddenBookProfile?.difficulty} · {comparison.hiddenBookProfile?.style}</span>
            </div>
          </div>
        </>
      ) : user && !message && <p className="lead">AI가 두 책을 비교하는 중입니다.</p>}
    </section>
  )
}

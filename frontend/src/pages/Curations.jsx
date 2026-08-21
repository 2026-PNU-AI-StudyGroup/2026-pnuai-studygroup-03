import { useEffect, useState } from 'react'
import { ArrowRight, BookOpen, ChevronLeft, ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { api, errorMessage } from '../api/client'
import Cover from '../components/Cover'
import Notice from '../components/Notice'

const PAGE_SIZE = 9

export default function Curations() {
  const [page, setPage] = useState(1)
  const [result, setResult] = useState({ content: [], totalPages: 0, totalElements: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    setLoading(true)
    api.publicCurations({ page, size: PAGE_SIZE })
      .then((data) => {
        if (!active) return
        setResult(data)
        setError('')
      })
      .catch((requestError) => {
        if (!active) return
        setError(errorMessage(requestError, '공개 큐레이션을 불러오지 못했습니다.'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [page])

  return (
    <section className="page public-curations">
      <p className="eyebrow"><span /> LIBRARIAN CURATIONS</p>
      <div className="public-curations-heading">
        <div>
          <h1 className="page-title">사서 큐레이션</h1>
          <p className="lead">도서관 사서가 주제에 맞춰 고른 책을 만나보세요.</p>
        </div>
        <strong>{result.totalElements}<span>개의 큐레이션</span></strong>
      </div>

      <Notice>{error}</Notice>
      {loading && <p className="curation-loading">큐레이션을 불러오는 중입니다.</p>}

      {!loading && !error && (
        <div className="curation-gallery">
          {result.content.map((curation) => (
            <Link className="public-curation-card" to={`/curations/${curation.id}`} key={curation.id}>
              <div className="public-curation-cover">
                <Cover book={{ title: curation.title, cover: curation.cover }} />
                <span>{curation.bookCount}권</span>
              </div>
              <div className="public-curation-copy">
                <span className="public-curation-label"><BookOpen size={13} /> 사서의 책장</span>
                <h2>{curation.title}</h2>
                <p>{curation.description || '주제에 맞는 도서를 한 권씩 골라 담았습니다.'}</p>
                <b>큐레이션 보기 <ArrowRight size={15} /></b>
              </div>
            </Link>
          ))}
        </div>
      )}

      {!loading && !error && !result.content.length && (
        <div className="empty-curations">
          <BookOpen size={30} />
          <p>현재 공개된 큐레이션이 없습니다.</p>
        </div>
      )}

      {!loading && !error && result.totalPages > 1 && (
        <div className="pagination curation-pagination">
          <button type="button" onClick={() => setPage((current) => current - 1)} disabled={page <= 1}
            aria-label="이전 페이지" title="이전 페이지">
            <ChevronLeft size={15} />
          </button>
          <span>{page} / {result.totalPages}</span>
          <button type="button" onClick={() => setPage((current) => current + 1)}
            disabled={page >= result.totalPages} aria-label="다음 페이지" title="다음 페이지">
            <ChevronRight size={15} />
          </button>
        </div>
      )}
    </section>
  )
}

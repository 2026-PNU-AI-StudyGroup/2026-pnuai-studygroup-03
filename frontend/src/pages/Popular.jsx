import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRight, LoaderCircle, Sparkles } from 'lucide-react'
import { api, errorMessage } from '../api/client'
import Cover from '../components/Cover'
import Notice from '../components/Notice'

// 백엔드 BookCategory / BookService 검증값과 맞춘 목록이다.
const CATEGORIES = ['ALL', '총류', '철학', '종교', '사회과학', '자연과학', '기술과학', '예술', '언어', '문학', '역사']
const GENDERS = [{ code: 'ALL', label: '전체' }, { code: 'M', label: '남성' }, { code: 'F', label: '여성' }]
const AGES = [
  { value: '', label: '전 연령' }, { value: '8', label: '8~13세' }, { value: '14', label: '14~19세' },
  { value: '20', label: '20대' }, { value: '30', label: '30대' }, { value: '40', label: '40대' },
  { value: '50', label: '50대' }, { value: '60', label: '60대 이상' },
]
const PAGE_SIZE = 12

export default function Popular() {
  const [filters, setFilters] = useState({ category: 'ALL', gender: 'ALL', age: '' })
  const [page, setPage] = useState(1)
  const [displayedPage, setDisplayedPage] = useState(1)
  const [result, setResult] = useState(null)
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    setLoading(true)
    const params = { page, size: PAGE_SIZE, category: filters.category, gender: filters.gender }
    if (filters.age) params.age = Number(filters.age)
    api.popularBooks(params)
      .then((data) => {
        if (!active) return
        setResult(data)
        setDisplayedPage(page)
        setMessage('')
      })
      .catch((err) => {
        if (!active) return
        setResult(null)
        setMessage(errorMessage(err, '인기 도서를 불러오지 못했습니다.'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => { active = false }
  }, [filters, page])

  const changeFilter = (key, value) => {
    setPage(1)
    setFilters((current) => ({ ...current, [key]: value }))
  }

  const books = result?.content || []

  return (
    <section className="page popular-page">
      <p className="eyebrow"><span /> POPULAR BOOKS</p>
      <h1 className="page-title">익숙한 인기 도서에서<br />탐색을 시작해 보세요.</h1>
      <p className="lead">도서관 정보나루의 최근 1년 대출 데이터를 기준으로 한 인기 도서입니다. 한 권을 고르면 AI가 핵심 키워드를 뽑아 줍니다.</p>

      <div className="popular-filters">
        <label>분야
          <select value={filters.category} onChange={(e) => changeFilter('category', e.target.value)}>
            {CATEGORIES.map((category) => (
              <option key={category} value={category}>{category === 'ALL' ? '전체' : category}</option>
            ))}
          </select>
        </label>
        <label>성별
          <select value={filters.gender} onChange={(e) => changeFilter('gender', e.target.value)}>
            {GENDERS.map((gender) => <option key={gender.code} value={gender.code}>{gender.label}</option>)}
          </select>
        </label>
        <label>연령대
          <select value={filters.age} onChange={(e) => changeFilter('age', e.target.value)}>
            {AGES.map((age) => <option key={age.label} value={age.value}>{age.label}</option>)}
          </select>
        </label>
      </div>

      <Notice>{message}</Notice>
      <div className={`popular-results${loading ? ' is-loading' : ''}`} aria-busy={loading}>
        {loading && (
          <div className="popular-loading" role="status" aria-live="polite">
            <LoaderCircle size={22} aria-hidden="true" />
            <span>인기 도서를 불러오는 중입니다.</span>
          </div>
        )}
        <div className="popular-grid">
          {books.map((book) => (
            <article className="popular-card" key={`${book.isbn}-${book.rank}`}>
              <span className="rank">{book.rank}</span>
              <Cover book={book} />
              <div>
                <h2>{book.title}</h2>
                <p>{book.author || '저자 정보 없음'}</p>
                <span className="popular-loan">대출 {book.loanCount.toLocaleString()}회</span>
                <Link className="outline-link" to={`/discover/${book.isbn}`}>
                  <Sparkles size={14} /> 이 책으로 탐색하기
                </Link>
                <Link className="text-link" to={`/books/${book.isbn}`}>도서 상세 <ChevronRight size={13} /></Link>
              </div>
            </article>
          ))}
        </div>
      </div>

      {!loading && !books.length && !message && <p className="lead">조건에 맞는 인기 도서가 없습니다.</p>}

      {result && result.totalPages > 1 && (
        <div className="pagination">
          <button disabled={loading || displayedPage <= 1} onClick={() => setPage(displayedPage - 1)}>이전</button>
          <span>{displayedPage} / {result.totalPages}</span>
          <button disabled={loading || displayedPage >= result.totalPages} onClick={() => setPage(displayedPage + 1)}>다음</button>
        </div>
      )}
    </section>
  )
}

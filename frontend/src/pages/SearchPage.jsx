import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRight, Search } from 'lucide-react'
import { api, errorMessage } from '../api/client'
import Cover from '../components/Cover'
import Notice from '../components/Notice'

const PAGE_SIZE = 12

export default function SearchPage() {
  const [keyword, setKeyword] = useState('')
  const [submitted, setSubmitted] = useState('')
  const [page, setPage] = useState(1)
  const [result, setResult] = useState(null)
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(false)

  const search = async (nextPage = 1, nextKeyword = keyword) => {
    if (!nextKeyword.trim()) return
    setLoading(true)
    try {
      setResult(await api.searchBooks({ keyword: nextKeyword.trim(), page: nextPage, size: PAGE_SIZE }))
      setSubmitted(nextKeyword.trim())
      setPage(nextPage)
      setMessage('')
    } catch (err) {
      setResult(null)
      setMessage(errorMessage(err, '검색 결과를 불러오지 못했습니다.'))
    } finally {
      setLoading(false)
    }
  }

  const books = result?.content || []

  return (
    <section className="page">
      <p className="eyebrow"><span /> BOOK SEARCH</p>
      <h1 className="page-title">찾고 싶은 책을<br />검색해 보세요.</h1>

      <form className="book-search" onSubmit={(event) => { event.preventDefault(); search(1) }}>
        <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="제목, 저자, 키워드 입력" />
        <button type="submit" disabled={loading}><Search size={18} /> 검색</button>
      </form>

      <Notice>{message}</Notice>
      {loading && <p className="lead">검색 중입니다.</p>}
      {!loading && submitted && !books.length && !message && (
        <p className="lead">‘{submitted}’에 대한 검색 결과가 없습니다.</p>
      )}

      <div className="search-results">
        {books.map((book) => (
          <Link className="search-book" key={book.isbn} to={`/books/${book.isbn}`}>
            <Cover book={book} />
            <div>
              <h2>{book.title}</h2>
              <p>{book.author || '저자 정보 없음'}</p>
              <span>상세 보기 <ChevronRight size={14} /></span>
            </div>
          </Link>
        ))}
      </div>

      {result && result.totalPages > 1 && (
        <div className="pagination">
          <button disabled={page <= 1} onClick={() => search(page - 1, submitted)}>이전</button>
          <span>{page} / {result.totalPages}</span>
          <button disabled={page >= result.totalPages} onClick={() => search(page + 1, submitted)}>다음</button>
        </div>
      )}
    </section>
  )
}

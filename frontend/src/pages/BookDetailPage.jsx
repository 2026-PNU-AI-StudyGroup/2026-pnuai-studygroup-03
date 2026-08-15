import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowRight, Sparkles } from 'lucide-react'
import { api, errorMessage } from '../api/client'
import Cover from '../components/Cover'
import Notice from '../components/Notice'
import SaveToShelfButton from '../components/SaveToShelfButton'

// 정보나루 지역(시도) 코드. 소장 도서관 조회에 필요하다.
const REGIONS = [
  { code: '21', label: '부산' }, { code: '11', label: '서울' }, { code: '22', label: '대구' },
  { code: '23', label: '인천' }, { code: '24', label: '광주' }, { code: '25', label: '대전' },
  { code: '26', label: '울산' }, { code: '29', label: '세종' }, { code: '31', label: '경기' },
  { code: '32', label: '강원' }, { code: '33', label: '충북' }, { code: '34', label: '충남' },
  { code: '35', label: '전북' }, { code: '36', label: '전남' }, { code: '37', label: '경북' },
  { code: '38', label: '경남' }, { code: '39', label: '제주' },
]

export default function BookDetailPage() {
  const { isbn } = useParams()
  const [region, setRegion] = useState('21')
  const [book, setBook] = useState(null)
  const [holdings, setHoldings] = useState({ loading: true, libraries: [], error: '' })
  const [message, setMessage] = useState('')
  const [shelfMessage, setShelfMessage] = useState('')

  // 지역 소장 조회(정보나루 libSrchByBook)는 20초 가까이 걸린다. 책 정보를 먼저 그리고
  // 소장 도서관은 따로 채워야 화면이 멈춘 것처럼 보이지 않는다.
  useEffect(() => {
    setBook(null)
    api.bookDetail(isbn)
      .then((data) => { setBook(data); setMessage('') })
      .catch((err) => setMessage(errorMessage(err, '도서 상세 정보를 불러오지 못했습니다.')))
  }, [isbn])

  useEffect(() => {
    let cancelled = false
    setHoldings({ loading: true, libraries: [], error: '' })
    api.bookDetail(isbn, region)
      .then((data) => {
        if (!cancelled) setHoldings({ loading: false, libraries: data.libraries || [], error: '' })
      })
      .catch((err) => {
        if (!cancelled) {
          setHoldings({ loading: false, libraries: [], error: errorMessage(err, '소장 도서관을 불러오지 못했습니다.') })
        }
      })
    return () => { cancelled = true }
  }, [isbn, region])

  if (message) return <section className="page"><Notice>{message}</Notice></section>
  if (!book) return <section className="page"><p className="lead">도서 정보를 불러오는 중입니다.</p></section>

  return (
    <section className="page detail-page">
      <div className="detail-top">
        <Cover book={book} />
        <div>
          <p className="eyebrow"><span /> BOOK DETAIL</p>
          <h1 className="page-title">{book.title}</h1>
          <p>{[book.author, book.publisher, book.publishedYear].filter(Boolean).join(' · ')}</p>
          {book.description && <p className="book-description">{book.description}</p>}
          <div className="detail-actions">
            <SaveToShelfButton isbn={book.isbn} onMessage={setShelfMessage} />
            <span className="small-label">나의 책장에 담기</span>
          </div>
          <div className="detail-links">
            <Link className="outline-link" to={`/discover/${book.isbn}`}>
              <Sparkles size={14} /> 이 책의 키워드로 탐색하기
            </Link>
            <Link className="outline-link" to={`/explore/${book.isbn}`}>
              이 책에서 더 찾기 <ArrowRight size={14} />
            </Link>
          </div>
          <Notice>{shelfMessage}</Notice>
        </div>
      </div>

      {book.tableOfContents?.length > 0 && (
        <section className="detail-block">
          <h2>목차</h2>
          <ol className="toc-list">
            {book.tableOfContents.map((line, index) => <li key={`${line}-${index}`}>{line}</li>)}
          </ol>
        </section>
      )}

      <section className="detail-block">
        <div className="detail-block-heading">
          <h2>소장 도서관</h2>
          <label className="region-select">
            지역
            <select value={region} onChange={(event) => setRegion(event.target.value)}>
              {REGIONS.map((item) => <option key={item.code} value={item.code}>{item.label}</option>)}
            </select>
          </label>
        </div>
        <Notice>{holdings.error}</Notice>
        {holdings.loading && <p className="lead">소장 도서관을 조회하는 중입니다. (정보나루 응답이 느려 20초 정도 걸릴 수 있어요)</p>}
        {!holdings.loading && !holdings.error && (
          holdings.libraries.length ? holdings.libraries.map((library) => (
            <p className="library-row" key={`${library.name}-${library.callNumber}`}>
              <b>{library.name}</b>
              <span>{library.callNumber}</span>
              <em className={library.available ? 'available' : ''}>{library.available ? '대출 가능' : '대출 중'}</em>
            </p>
          )) : <p className="lead">선택한 지역에서 이 책을 소장한 도서관을 찾지 못했습니다.</p>
        )}
      </section>
    </section>
  )
}

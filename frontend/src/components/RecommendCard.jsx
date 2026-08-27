import { Link } from 'react-router-dom'
import { ChevronRight, MapPin, Sparkles } from 'lucide-react'
import Cover from './Cover'
import SaveToShelfButton from './SaveToShelfButton'

/**
 * 추천 결과 카드. 발견에서 끝나지 않도록 청구기호(서가 위치)와 다음 탐색 링크를 함께 보여 준다.
 * metrics는 추천(키워드/목적/발견 가치)과 재탐색(관련도)에서 다르므로 호출부가 넘긴다.
 */
export default function RecommendCard({ book, metrics, compareTo, exploreFrom, onMessage }) {
  return (
    <article className="recommend-card">
      <SaveToShelfButton isbn={book.isbn} onMessage={onMessage} />
      <Cover book={book} />
      <div className="recommend-copy">
        <div className="score"><b>{book.score}</b><span>추천 적합도</span></div>
        <h3>{book.title}</h3>
        <p>{book.author}</p>
        {book.reason && <p className="ai-reason"><Sparkles size={14} /> {book.reason}</p>}

        {book.callNumber && (
          <p className="shelf-location">
            <MapPin size={13} />
            <span>{book.shelfName ? `${book.shelfName} · ` : ''}<b>{book.callNumber}</b></span>
          </p>
        )}

        <div className="metrics">
          {metrics.map((metric) => (
            <span key={metric.label}>{metric.label} <b>{metric.value}%</b></span>
          ))}
        </div>

        {book.keywords?.length > 0 && (
          <div className="tags">{book.keywords.map((keyword) => <span key={keyword}>#{keyword}</span>)}</div>
        )}

        <div className="recommend-links">
          {compareTo && <Link to={compareTo}>인기 도서와 비교하기 <ChevronRight size={15} /></Link>}
          {exploreFrom && <Link to={exploreFrom}>이 책에서 더 찾기 <ChevronRight size={15} /></Link>}
        </div>
      </div>
    </article>
  )
}

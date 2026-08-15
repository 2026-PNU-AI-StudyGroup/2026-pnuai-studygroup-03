import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, ChevronRight, Sparkles } from 'lucide-react'
import { api, errorMessage } from '../api/client'
import { useLibrary } from '../library/LibraryContext'
import Cover from '../components/Cover'
import LibraryPicker from '../components/LibraryPicker'
import Notice from '../components/Notice'

function HomeSection({ eyebrow, title, link, linkLabel, children }) {
  return (
    <section className="home-popular">
      <div className="section-heading">
        <div>
          <p className="eyebrow"><span /> {eyebrow}</p>
          <h2>{title}</h2>
        </div>
        <Link to={link}>{linkLabel} <ChevronRight size={16} /></Link>
      </div>
      {children}
    </section>
  )
}

function BookPanel({ book, badge, cta, to }) {
  return (
    <div className="home-book">
      <Cover book={book} />
      <div>
        <span className="badge"><Sparkles size={12} /> {badge}</span>
        <h3>{book.title}</h3>
        <p>{book.author || '저자 정보 없음'}</p>
        {book.reason && <p className="book-description">{book.reason}</p>}
        {book.keywords?.length > 0 && (
          <div className="tags">{book.keywords.map((keyword) => <span key={keyword}>#{keyword}</span>)}</div>
        )}
        <Link className="outline-link" to={to}>{cta} <ArrowRight size={16} /></Link>
      </div>
    </div>
  )
}

export default function Home() {
  const navigate = useNavigate()
  const { libraryCode, library } = useLibrary()
  const [popular, setPopular] = useState(null)
  const [popularError, setPopularError] = useState('')
  const [todayBook, setTodayBook] = useState(null)
  const [libraryError, setLibraryError] = useState('')

  useEffect(() => {
    api.popularBooks({ page: 1, size: 1 })
      .then((page) => setPopular(page.content?.[0] || null))
      .catch((err) => setPopularError(errorMessage(err, '인기 도서를 불러오지 못했습니다.')))
  }, [])

  useEffect(() => {
    if (!libraryCode) {
      setTodayBook(null)
      return
    }
    api.todayBook(libraryCode)
      .then((book) => { setTodayBook(book); setLibraryError('') })
      .catch((err) => {
        setTodayBook(null)
        setLibraryError(errorMessage(err, '오늘의 잠자는 책을 불러오지 못했습니다.'))
      })
  }, [libraryCode])

  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <p className="eyebrow"><span /> AI BOOK CURATION</p>
          <h1>잠자는 책을<br /><em>깨워</em> 보세요.</h1>
          <p className="hero-description">
            인기 도서의 관심 키워드에서 시작해<br />아직 발견되지 않은 좋은 책을 만나 보세요.
          </p>
          <button className="primary-link" onClick={() => navigate('/popular')}>
            인기 도서에서 시작하기 <ChevronRight size={17} />
          </button>
        </div>
        <div className="hero-art">
          <div className="sun" />
          <div className="arch" />
          <div className="book-shape book-one" />
          <div className="book-shape book-two" />
          <div className="book-shape book-three" />
          <p>every book<br />deserves a reader</p>
        </div>
      </section>

      <section className="home-library">
        <LibraryPicker />
        {library && <p className="library-picker-hint">현재 <b>{library.libraryName}</b>의 잠자는 도서를 기준으로 추천합니다.</p>}
      </section>

      <HomeSection eyebrow="THIS MONTH" title="지금 가장 사랑받는 책" link="/popular" linkLabel="인기 도서 전체 보기">
        <Notice>{popularError}</Notice>
        {popular ? (
          <BookPanel
            book={popular}
            badge={`이번 달 인기 ${popular.rank}위`}
            cta="이 책에서 탐색 시작"
            to={`/discover/${popular.isbn}`}
          />
        ) : !popularError && <p className="lead">인기 도서를 불러오는 중입니다.</p>}
      </HomeSection>

      <HomeSection eyebrow="TODAY'S WAKEBOOK" title="오늘의 잠자는 책" link="/today" linkLabel="오늘의 책 보러가기">
        {!libraryCode ? (
          <p className="lead">도서관을 선택하면 그 도서관의 오늘의 잠자는 책을 소개해 드려요.</p>
        ) : (
          <>
            <Notice>{libraryError}</Notice>
            {todayBook && (
              <BookPanel
                book={todayBook}
                badge="AI의 오늘 추천"
                cta="이 책 자세히 보기"
                to={`/books/${todayBook.isbn}`}
              />
            )}
          </>
        )}
      </HomeSection>
    </>
  )
}

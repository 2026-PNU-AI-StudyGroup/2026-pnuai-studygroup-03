import { useMemo, useState } from 'react'
import { ArrowUpRight, BookOpen, ChevronRight, Heart, Library, Menu, Search, Sparkles, X } from 'lucide-react'
import { hiddenBooks, popularBook } from './data/books'

function BookCard({ book, featured, onClick }) {
  return <article className={`book-card ${featured ? 'featured' : ''}`} onClick={() => onClick(book)}>
    <div className="cover-wrap"><img src={book.cover} alt={`${book.title} 표지`} /><span className={`availability ${book.status === '대출 가능' ? 'available' : ''}`}>{book.status}</span></div>
    <div className="book-copy">
      {featured && <span className="rank"><Sparkles size={13} /> 이번 달 인기 1위</span>}
      <h3>{book.title}</h3><p>{book.author}</p>
      {!featured && <p className="reason">“{book.reason}”</p>}
      <div className="card-footer"><span>{featured ? '대출 1,284회' : `최근 대출 ${book.loans}회`}</span><ArrowUpRight size={18} /></div>
    </div>
  </article>
}

function App() {
  const [selectedTag, setSelectedTag] = useState('인간관계')
  const [detail, setDetail] = useState(null)
  const [liked, setLiked] = useState([])
  const results = useMemo(() => hiddenBooks.filter(b => b.tags.includes(selectedTag)), [selectedTag])
  const toggleLike = (id) => setLiked(value => value.includes(id) ? value.filter(x => x !== id) : [...value, id])

  return <>
    <header><a className="logo" href="#top"><span>W</span>WakeBook</a><nav><a href="#discover">탐색하기</a><a href="#curation">북 큐레이션</a><a href="#about">WakeBook 소개</a></nav><button className="search-btn" aria-label="검색"><Search size={20} /></button><button className="menu-btn" aria-label="메뉴"><Menu size={23} /></button></header>
    <main id="top">
      <section className="hero"><div className="hero-copy"><p className="eyebrow"><span /> AI BOOK CURATION</p><h1>잠자는 책을<br /><em>깨워</em> 보세요.</h1><p className="hero-description">오늘의 인기 도서에서 관심 키워드를 골라 보세요.<br />아직 발견되지 않은 좋은 책들을 연결해 드릴게요.</p><a className="primary-link" href="#discover">나만의 책 발견하기 <ChevronRight size={17} /></a></div><div className="hero-art"><div className="sun" /><div className="arch" /><div className="book-shape book-one" /><div className="book-shape book-two" /><div className="book-shape book-three" /><div className="leaf leaf-one">⌇</div><p>every book<br />deserves a reader</p></div></section>
      <section id="discover" className="content-section discovery"><div className="section-title"><div><p className="eyebrow"><span /> THIS MONTH</p><h2>지금 가장 사랑받는 책</h2></div><button className="text-btn">인기 도서 전체보기 <ChevronRight size={16} /></button></div><div className="discovery-grid"><BookCard book={popularBook} featured onClick={setDetail} /><div className="keyword-panel"><div><span className="small-label">AI가 찾은 핵심 키워드</span><h3>어떤 이야기에<br />마음이 가나요?</h3><p>관심 있는 키워드를 선택하면<br />그 안에 숨은 책을 찾아드려요.</p></div><div className="tags">{popularBook.tags.map(tag => <button onClick={() => setSelectedTag(tag)} className={selectedTag === tag ? 'selected' : ''} key={tag}>#{tag}</button>)}</div></div></div></section>
      <section id="curation" className="content-section hidden-section"><div className="section-title"><div><p className="eyebrow"><span /> WAKE UP BOOKS</p><h2><mark>#{selectedTag}</mark> 안에 숨은 책들</h2><p className="section-subtitle">많이 빌려지진 않았지만, 당신의 다음 책이 될 수 있어요.</p></div><div className="counter"><b>{results.length}</b>권의 책을 찾았어요</div></div><div className="book-list">{results.map(book => <div className="hidden-card-wrap" key={book.id}><button className={`like ${liked.includes(book.id) ? 'liked' : ''}`} onClick={() => toggleLike(book.id)}><Heart size={18} fill={liked.includes(book.id) ? 'currentColor' : 'none'} /></button><BookCard book={book} onClick={setDetail} /></div>)}</div></section>
      <section id="about" className="mission"><div className="mission-icon"><BookOpen size={32} /></div><div><p className="eyebrow"><span /> OUR MISSION</p><h2>모든 좋은 책에는<br />만날 독자가 있어요.</h2></div><p>WakeBook은 AI와 함께 도서관의<br />잠자는 장서를 깨웁니다.</p><Library size={28} /></section>
    </main>
    <footer><a className="logo" href="#top"><span>W</span>WakeBook</a><p>© 2026 WakeBook. Books are waiting for you.</p></footer>
    {detail && <div className="modal-backdrop" onClick={() => setDetail(null)}><section className="modal" onClick={e => e.stopPropagation()}><button className="close" onClick={() => setDetail(null)}><X /></button><img src={detail.cover} alt="" /><div><span className="small-label">{detail.status} · {detail.library}</span><h2>{detail.title}</h2><p className="author">{detail.author} · {detail.publisher} · {detail.year}</p><p className="modal-description">{detail.description}</p><div className="modal-tags">{detail.tags.map(t => <span key={t}>#{t}</span>)}</div><button className="borrow">대출 가능 여부 확인 <ChevronRight size={17} /></button></div></section></div>}
  </>
}
export default App

import { useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

function Header() {
  const [open, setOpen] = useState(false)
  const { user, logout } = useAuth()

  return (
    <header className="app-header">
      <Link className="logo" to="/"><span>W</span>WakeBook</Link>
      <nav className={open ? 'open' : ''} onClick={() => setOpen(false)}>
        <NavLink to="/popular">인기 도서</NavLink>
        <NavLink to="/search">도서 검색</NavLink>
        <NavLink to="/trends">실시간 트렌드</NavLink>
        <NavLink to="/curations">큐레이션</NavLink>
        <NavLink to="/today">오늘의 책</NavLink>
        {user && <NavLink to="/bookshelf">나의 책장</NavLink>}
        {user?.role === 'LIBRARIAN' && <NavLink to="/librarian">사서 공간</NavLink>}
      </nav>
      <div className="header-actions">
        {user ? (
          <>
            <span className="user-name">{user.name}님</span>
            <button className="login-link" onClick={logout}>로그아웃</button>
          </>
        ) : (
          <>
            <Link className="login-link" to="/login">로그인</Link>
            <Link className="header-cta" to="/signup">시작하기</Link>
          </>
        )}
        <button className="mobile-menu" onClick={() => setOpen(!open)} aria-label="메뉴 열기">☰</button>
      </div>
    </header>
  )
}

export default function Layout() {
  const { expired, dismissExpired } = useAuth()

  return (
    <>
      <Header />
      {expired && (
        <p className="session-expired">
          로그인이 만료되어 로그아웃했습니다. 다시 로그인해 주세요.
          <button onClick={dismissExpired}>닫기</button>
        </p>
      )}
      <main><Outlet /></main>
      <footer>
        <Link className="logo" to="/"><span>W</span>WakeBook</Link>
        <p>© 2026 WakeBook. Every book deserves a reader.</p>
      </footer>
    </>
  )
}

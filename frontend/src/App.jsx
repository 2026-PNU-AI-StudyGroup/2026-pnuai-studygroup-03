import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, RequireAuth, RequireLibrarian } from './auth/AuthContext'
import { LibraryProvider } from './library/LibraryContext'
import { BookshelfProvider } from './bookshelf/BookshelfContext'
import Layout from './components/Layout'
import Home from './pages/Home'
import Popular from './pages/Popular'
import Discover from './pages/Discover'
import Explore from './pages/Explore'
import LibraryBooks from './pages/LibraryBooks'
import Today from './pages/Today'
import Compare from './pages/Compare'
import Bookshelf from './pages/Bookshelf'
import SearchPage from './pages/SearchPage'
import BookDetailPage from './pages/BookDetailPage'
import AuthPage from './pages/AuthPage'
import Librarian from './pages/Librarian'
import LibrarianOperations from './pages/LibrarianOperations'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <LibraryProvider>
          <BookshelfProvider>
            <Routes>
              <Route path="/login" element={<AuthPage />} />
              <Route path="/signup" element={<AuthPage signup />} />
              <Route element={<Layout />}>
                <Route path="/" element={<Home />} />
                <Route path="/popular" element={<Popular />} />
                {/* 기준 도서 없이는 추천을 만들 수 없으므로 인기 도서 목록으로 보낸다. */}
                <Route path="/discover" element={<Navigate to="/popular" replace />} />
                <Route path="/discover/:isbn" element={<Discover />} />
                <Route path="/explore/:isbn" element={<Explore />} />
                <Route path="/libraries/:libraryCode" element={<LibraryBooks />} />
                <Route path="/search" element={<SearchPage />} />
                <Route path="/books/:isbn" element={<BookDetailPage />} />
                <Route path="/today" element={<Today />} />
                <Route path="/random" element={<Today random />} />
                <Route path="/compare/:baseIsbn/:hiddenIsbn" element={<Compare />} />
                <Route path="/bookshelf" element={<RequireAuth><Bookshelf /></RequireAuth>} />
                <Route path="/librarian" element={<RequireLibrarian><Librarian /></RequireLibrarian>} />
                <Route
                  path="/librarian/operations"
                  element={<RequireLibrarian><LibrarianOperations /></RequireLibrarian>}
                />
                <Route path="*" element={<Navigate to="/" replace />} />
              </Route>
            </Routes>
          </BookshelfProvider>
        </LibraryProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}

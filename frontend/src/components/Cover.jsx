import { BookOpen } from 'lucide-react'

/** 정보나루 표지 URL이 비어 있거나 깨지는 경우가 흔해서, 실패하면 자리표시자를 보여 준다. */
export default function Cover({ book, className = 'book-cover' }) {
  const cover = book?.cover
  if (!cover) {
    return (
      <div className={`${className} cover-fallback`} aria-label={`${book?.title || '도서'} 표지 없음`}>
        <BookOpen size={26} />
      </div>
    )
  }
  return (
    <img
      className={className}
      src={cover}
      alt={`${book.title} 표지`}
      loading="lazy"
      onError={(event) => {
        event.currentTarget.classList.add('cover-broken')
        event.currentTarget.removeAttribute('src')
      }}
    />
  )
}

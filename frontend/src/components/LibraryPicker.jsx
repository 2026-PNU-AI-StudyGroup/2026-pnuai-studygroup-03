import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRight, Library, Plus, Search } from 'lucide-react'
import { api, errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useLibrary } from '../library/LibraryContext'
import { SOURCE_LABEL, useHiddenBookJob } from '../library/useHiddenBookJob'
import JobProgress from './JobProgress'
import Notice from './Notice'

const REGIONS = [
  { code: '21', label: '부산' }, { code: '11', label: '서울' }, { code: '22', label: '대구' },
  { code: '23', label: '인천' }, { code: '24', label: '광주' }, { code: '25', label: '대전' },
  { code: '26', label: '울산' }, { code: '29', label: '세종' }, { code: '31', label: '경기' },
  { code: '32', label: '강원' }, { code: '33', label: '충북' }, { code: '34', label: '충남' },
  { code: '35', label: '전북' }, { code: '36', label: '전남' }, { code: '37', label: '경북' },
  { code: '38', label: '경남' }, { code: '39', label: '제주' },
]

/**
 * 추천은 "그 도서관에 등록된 잠자는 도서 후보군"에서 나온다. 후보군이 있는 도서관을 고르게 하고,
 * 없는 도서관은 정보나루 API로 후보군을 직접 만들 수 있게 한다(사서 CSV 없이도 동작한다).
 */
export default function LibraryPicker() {
  const { user } = useAuth()
  const { library, libraries, loading, setLibrary, reloadLibraries, source } = useLibrary()
  const [adding, setAdding] = useState(false)

  if (loading) return <p className="library-picker-hint">도서관 목록을 불러오는 중입니다.</p>

  return (
    <div className="library-picker-block">
      {libraries.length > 0 ? (
        <label className="library-picker">
          <span><Library size={15} /> 도서관</span>
          <select
            value={library?.libraryCode || ''}
            onChange={(event) => {
              const picked = libraries.find((item) => item.libraryCode === event.target.value)
              setLibrary(picked ? { libraryCode: picked.libraryCode, libraryName: picked.libraryName } : null)
            }}
          >
            {libraries.map((item) => (
              <option key={item.libraryCode} value={item.libraryCode}>
                {item.libraryName} (잠자는 책 {item.hiddenBookCount}권)
              </option>
            ))}
          </select>
        </label>
      ) : (
        <div className="library-picker empty-library">
          <Library size={18} />
          <span>아직 잠자는 도서 후보군이 등록된 도서관이 없습니다. 아래에서 도서관을 골라 직접 만들어 보세요.</span>
        </div>
      )}

      {library && (
        <p className="library-picker-hint">
          {source && <>기준: {SOURCE_LABEL[source] || source} · </>}
          <Link className="text-link" to={`/libraries/${library.libraryCode}`}>
            잠자는 책 목록 보기 <ChevronRight size={12} />
          </Link>
        </p>
      )}

      {user ? (
        adding
          ? <LibraryFinder onDone={reloadLibraries} onClose={() => setAdding(false)} />
          : (
            <button className="text-link" onClick={() => setAdding(true)}>
              <Plus size={13} /> 우리 동네 도서관 추가하기
            </button>
          )
      ) : (
        <p className="library-picker-hint">로그인하면 원하는 도서관의 잠자는 도서를 직접 만들 수 있어요.</p>
      )}
    </div>
  )
}

function LibraryFinder({ onDone, onClose }) {
  const { libraries, setLibrary } = useLibrary()
  const [region, setRegion] = useState('21')
  const [keyword, setKeyword] = useState('')
  const [directory, setDirectory] = useState([])
  const [message, setMessage] = useState('')
  const [searching, setSearching] = useState(false)
  const [target, setTarget] = useState(null)

  const { job, error, starting, running, start } = useHiddenBookJob({
    onFinished: async (finished) => {
      await onDone?.()
      if (finished.status === 'SUCCEEDED' && finished.savedCount > 0) {
        setLibrary({ libraryCode: finished.libraryCode, libraryName: finished.libraryName })
      }
    },
  })

  const search = async () => {
    setSearching(true)
    setMessage('')
    try {
      setDirectory(await api.libraryDirectory(region))
    } catch (err) {
      setMessage(errorMessage(err, '도서관 목록을 불러오지 못했습니다.'))
    } finally {
      setSearching(false)
    }
  }

  const filtered = keyword.trim()
    ? directory.filter((item) => item.libraryName.includes(keyword.trim()))
    : directory

  return (
    <div className="library-finder">
      <div className="library-finder-head">
        <b>도서관 찾기</b>
        <button className="modal-close" onClick={onClose}>×</button>
      </div>
      <p className="library-finder-hint">
        도서관을 고르면 정보나루 장서·대출 순위를 비교해 그 도서관의 잠자는 도서를 만들어 드려요.
      </p>

      <div className="library-finder-controls">
        <select value={region} onChange={(event) => setRegion(event.target.value)}>
          {REGIONS.map((item) => <option key={item.code} value={item.code}>{item.label}</option>)}
        </select>
        <button className="secondary-button" onClick={search} disabled={searching}>
          <Search size={14} /> {searching ? '불러오는 중' : '도서관 목록 보기'}
        </button>
      </div>

      <Notice>{message}</Notice>
      <Notice>{error}</Notice>

      {directory.length > 0 && (
        <>
          <input
            className="library-finder-search"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="도서관 이름으로 좁히기"
          />
          <p className="library-finder-hint">
            도서관 한 곳을 만드는 데 정보나루 조회를 수십 번 씁니다. 하루에 만들 수 있는 수가 제한돼 있어요.
          </p>
          <div className="library-finder-list">
            {filtered.slice(0, 30).map((item) => {
              // 이미 후보군이 있는 도서관을 다시 만들면 정보나루 호출만 낭비된다. 바로 선택하게 한다.
              const ready = libraries.find((lib) => lib.libraryCode === item.libraryCode)
              return (
                <button
                  key={item.libraryCode}
                  className={target === item.libraryCode ? 'selected' : ''}
                  disabled={running || starting}
                  onClick={() => {
                    if (ready) {
                      setLibrary({ libraryCode: ready.libraryCode, libraryName: ready.libraryName })
                      onClose()
                      return
                    }
                    setTarget(item.libraryCode)
                    start(() => api.collectHiddenBooks(item.libraryCode))
                  }}
                >
                  <b>{item.libraryName}</b>
                  <span>{ready ? `준비됨 · 잠자는 책 ${ready.hiddenBookCount}권` : `장서 ${item.bookCount.toLocaleString()}권`}</span>
                </button>
              )
            })}
          </div>
        </>
      )}

      <JobProgress job={job} />
    </div>
  )
}

import { useCallback, useEffect, useRef, useState } from 'react'
import { AlertCircle, FileSpreadsheet, Pencil, Save, Trash2, Upload } from 'lucide-react'
import { api, errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useLibrary } from '../library/LibraryContext'
import { useHiddenBookJob } from '../library/useHiddenBookJob'
import JobProgress from '../components/JobProgress'
import Notice from '../components/Notice'

function CsvPanel() {
  const { user } = useAuth()
  const { reloadLibraries } = useLibrary()
  const fileRef = useRef(null)
  const [fileName, setFileName] = useState('')
  const { job, error, starting, running, start } = useHiddenBookJob({ onFinished: () => reloadLibraries?.() })

  const upload = (file) => {
    if (!file) return
    setFileName(file.name)
    // 대상 도서관은 서버가 로그인한 사서의 소속으로 결정한다.
    start(() => api.uploadHiddenBooks(file))
  }

  return (
    <section className="operation-panel">
      <div className="operation-heading">
        <div>
          <span className="badge"><FileSpreadsheet size={13} /> CSV 후보군</span>
          <h2>장서·대출 CSV 업로드</h2>
          <p>
            도서관정보나루에서 받은 “장서 대출목록” CSV를 올리면
            {' '}<b>{user.libraryName}({user.libraryCode})</b>의 저이용·고품질 도서 후보군을 다시 만듭니다.
            실제 대출건수를 쓰기 때문에 API 자동 산출보다 정확합니다.
          </p>
        </div>
      </div>

      <input
        ref={fileRef}
        className="file-input"
        type="file"
        accept=".csv,text/csv"
        onChange={(event) => upload(event.target.files?.[0])}
      />
      <button className="upload-dropzone" onClick={() => fileRef.current?.click()} disabled={starting || running}>
        <Upload size={25} />
        <b>{starting ? '접수 중입니다...' : fileName || 'CSV 파일을 선택하세요'}</b>
        <span>필수 열: 도서명, ISBN, 대출건수 (정보나루 원본 파일 그대로)</span>
      </button>

      <Notice>{error}</Notice>
      <JobProgress job={job} />
    </section>
  )
}

function CurationPanel() {
  const [curations, setCurations] = useState([])
  const [editing, setEditing] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const page = await api.curations({ page: 1, size: 20 })
      setCurations(page.content || [])
      setError('')
    } catch (err) {
      setError(errorMessage(err, '큐레이션 목록을 불러오지 못했습니다.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  // 목록 응답에는 도서 구성이 없다. PATCH는 books로 도서를 통째로 교체하므로,
  // 수정 전에 상세를 받아 기존 도서 목록을 그대로 다시 보내야 도서가 지워지지 않는다.
  const openEditor = async (summary) => {
    setError('')
    try {
      setEditing(await api.curation(summary.id))
    } catch (err) {
      setError(errorMessage(err, '큐레이션을 불러오지 못했습니다.'))
    }
  }

  const save = async (event) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    try {
      await api.updateCuration(editing.id, {
        title: form.get('title'),
        description: form.get('description'),
        isPublic: form.get('isPublic') === 'on',
        books: editing.books.map((book, index) => ({
          isbn: book.isbn,
          displayOrder: index + 1,
          comment: book.comment,
        })),
      })
      setEditing(null)
      setNotice('큐레이션을 수정했습니다.')
      load()
    } catch (err) {
      setError(errorMessage(err, '큐레이션을 수정하지 못했습니다.'))
    }
  }

  const remove = async (curation) => {
    if (!window.confirm(`“${curation.title}” 큐레이션을 삭제할까요?`)) return
    try {
      await api.deleteCuration(curation.id)
      setNotice('큐레이션을 삭제했습니다.')
      load()
    } catch (err) {
      setError(errorMessage(err, '큐레이션을 삭제하지 못했습니다.'))
    }
  }

  const publicCount = curations.filter((item) => item.isPublic).length

  return (
    <section className="operation-panel curation-list-panel">
      <div className="operation-heading">
        <div>
          <span className="badge"><Pencil size={13} /> CURATION MANAGER</span>
          <h2>큐레이션 목록 관리</h2>
          <p>저장한 전시 큐레이션을 수정하고 공개 상태를 관리합니다.</p>
        </div>
        <div className="curation-count"><b>{curations.length}</b><span>전체 · 공개 {publicCount}</span></div>
      </div>

      <Notice>{error}</Notice>
      <Notice tone="success">{notice}</Notice>
      {loading && <p className="lead">큐레이션을 불러오는 중입니다.</p>}

      <div className="curation-list">
        {curations.map((curation) => (
          <article key={curation.id}>
            <div className="curation-status">
              <span className={curation.isPublic ? 'public' : 'private'}>
                {curation.isPublic ? '공개 중' : '비공개'}
              </span>
            </div>
            <h3>{curation.title}</h3>
            <footer>
              <span>추천 도서 {curation.bookCount}권</span>
              <div>
                <button onClick={() => openEditor(curation)}><Pencil size={14} /> 수정</button>
                <button className="delete-button" onClick={() => remove(curation)}><Trash2 size={14} /> 삭제</button>
              </div>
            </footer>
          </article>
        ))}
      </div>

      {!loading && !curations.length && !error && (
        <div className="empty-operation">
          <AlertCircle size={28} />
          <p>저장된 큐레이션이 없습니다. 사서 공간에서 AI 초안을 만들어 저장해 보세요.</p>
        </div>
      )}

      {editing && (
        <div className="modal-backdrop">
          <form className="curation-modal" onSubmit={save}>
            <div>
              <span className="badge">큐레이션 수정</span>
              <button type="button" className="modal-close" onClick={() => setEditing(null)}>×</button>
            </div>
            <h2>전시 큐레이션 편집</h2>
            <label>제목<input name="title" defaultValue={editing.title} required maxLength={200} /></label>
            <label>소개 문구<textarea name="description" defaultValue={editing.description} maxLength={1000} /></label>
            <div className="editing-books">
              <b>포함된 도서 {editing.books.length}권</b>
              <ol>{editing.books.map((book) => <li key={book.isbn}>{book.title}</li>)}</ol>
            </div>
            <label className="public-check">
              <input name="isPublic" type="checkbox" defaultChecked={editing.isPublic} /> 이용자에게 공개하기
            </label>
            <button className="primary-link" type="submit"><Save size={15} /> 변경사항 저장</button>
          </form>
        </div>
      )}
    </section>
  )
}

export default function LibrarianOperations() {
  return (
    <section className="page librarian-operations">
      <p className="eyebrow"><span /> LIBRARIAN OPERATIONS</p>
      <h1 className="page-title">사서 관리 도구</h1>
      <p className="lead">장서 데이터를 등록하고, 전시 큐레이션을 한 곳에서 관리하세요.</p>
      <div className="operations-grid">
        <CsvPanel />
        <CurationPanel />
      </div>
    </section>
  )
}

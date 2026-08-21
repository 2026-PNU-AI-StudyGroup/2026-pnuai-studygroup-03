import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Activity, AlertCircle, CheckCircle2, FileSpreadsheet, Pencil, Radio, RefreshCw, Save, Trash2, Upload } from 'lucide-react'
import { api, errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useLibrary } from '../library/LibraryContext'
import { useHiddenBookJob } from '../library/useHiddenBookJob'
import JobProgress from '../components/JobProgress'
import Notice from '../components/Notice'

const RUNNING_TREND_STATUSES = new Set(['PENDING', 'PROCESSING'])

function TrendPanel() {
  const [current, setCurrent] = useState(null)
  const [batch, setBatch] = useState(null)
  const [loading, setLoading] = useState(true)
  const [starting, setStarting] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const loadCurrent = useCallback(async () => {
    try {
      const data = await api.librarianDailyTrends()
      setCurrent(data)
      setError('')
      return data
    } catch (err) {
      if (err?.response?.data?.code === 'TREND_001') {
        setCurrent(null)
        return null
      }
      setError(errorMessage(err, '오늘의 트렌드 추천 상태를 불러오지 못했습니다.'))
      return null
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadCurrent() }, [loadCurrent])

  useEffect(() => {
    if (!batch?.batchId || !RUNNING_TREND_STATUSES.has(batch.status)) return undefined
    const timer = window.setTimeout(async () => {
      try {
        const next = await api.trendBatch(batch.batchId)
        setBatch(next)
        if (next.status === 'COMPLETED') {
          setNotice(`오늘의 트렌드 추천 ${next.createdCount}개 주제를 생성했습니다.`)
          await loadCurrent()
        } else if (next.status === 'FAILED') {
          setError(`트렌드 추천 생성에 실패했습니다. 오류 코드: ${next.errorCode || 'TREND_002'}`)
        }
      } catch (err) {
        setError(errorMessage(err, '트렌드 생성 진행 상태를 확인하지 못했습니다.'))
      }
    }, 2000)
    return () => window.clearTimeout(timer)
  }, [batch, loadCurrent])

  const refresh = async () => {
    if (current && !window.confirm('오늘 추천을 다시 만들면 OpenAI 호출이 발생하고 현재 노출 결과가 바뀔 수 있습니다. 계속할까요?')) return
    setStarting(true)
    setError('')
    setNotice('')
    try {
      const next = await api.refreshDailyTrends(Boolean(current))
      setBatch(next)
      if (next.status === 'COMPLETED') {
        setNotice('오늘 생성된 추천을 확인했습니다.')
        await loadCurrent()
      } else {
        setNotice('트렌드 수집과 도서 연결을 시작했습니다. 이 화면에서 진행 상태를 자동으로 확인합니다.')
      }
    } catch (err) {
      const runningBatchId = err?.response?.data?.data?.batchId
      if (err?.response?.data?.code === 'TREND_003' && runningBatchId) {
        try {
          setBatch(await api.trendBatch(runningBatchId))
          setNotice('이미 실행 중인 트렌드 생성 작업을 이어서 확인합니다.')
        } catch (batchError) {
          setError(errorMessage(batchError, '실행 중인 트렌드 작업을 확인하지 못했습니다.'))
        }
      } else {
        setError(errorMessage(err, '트렌드 추천 생성을 시작하지 못했습니다.'))
      }
    } finally {
      setStarting(false)
    }
  }

  const running = RUNNING_TREND_STATUSES.has(batch?.status)
  const topicCount = current?.items?.length || 0

  return (
    <section className="operation-panel trend-operation-panel">
      <div className="operation-heading">
        <div>
          <span className="badge"><Radio size={13} /> REAL-TIME TREND</span>
          <h2>오늘의 실시간 트렌드 추천</h2>
          <p>Google 트렌드 후보를 네이버 뉴스·데이터랩으로 보강하고, 우리 도서관의 잠자는 책과 연결합니다.</p>
        </div>
        <div className="trend-operation-count">
          <b>{loading ? '—' : topicCount}</b><span>오늘의 주제</span>
        </div>
      </div>

      <div className="trend-operation-status">
        {running ? <Activity className="spin" size={19} /> : current ? <CheckCircle2 size={19} /> : <AlertCircle size={19} />}
        <div>
          <b>{running ? '트렌드와 도서를 연결하는 중입니다.' : current ? '오늘의 추천이 준비되었습니다.' : '오늘 생성된 추천이 없습니다.'}</b>
          <span>
            {running
              ? `작업 상태: ${batch.status} · 완료될 때까지 자동 확인합니다.`
              : current
                ? `${current.libraryName || '소속 도서관'} · ${topicCount}개 주제 · ${current.items.reduce((sum, item) => sum + (item.books?.length || 0), 0)}권`
                : '서버 시작 시 자동 생성되며, 아래 버튼으로 지금 바로 시작할 수도 있습니다.'}
          </span>
        </div>
      </div>

      <Notice>{error}</Notice>
      <Notice tone="success">{notice}</Notice>
      <div className="trend-operation-actions">
        <button className="primary-link" onClick={refresh} disabled={starting || running}>
          <RefreshCw className={starting ? 'spin' : ''} size={15} />
          {running ? '생성 진행 중' : current ? '오늘 추천 다시 생성' : '오늘 추천 생성'}
        </button>
        <Link className="secondary-button" to="/trends">이용자 화면에서 보기</Link>
      </div>
    </section>
  )
}

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
        <TrendPanel />
        <CsvPanel />
        <CurationPanel />
      </div>
    </section>
  )
}

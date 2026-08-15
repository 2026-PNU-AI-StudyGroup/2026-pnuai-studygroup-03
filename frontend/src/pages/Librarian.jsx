import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, Library, Save, WandSparkles } from 'lucide-react'
import { api, errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Notice from '../components/Notice'

const TARGET_AGES = ['전 연령', '10대', '20대', '30대', '40대 이상', '가족']
const MOODS = ['따뜻한', '사색적인', '경쾌한', '담백한']

function Stat({ value, label }) {
  return <div><b>{value}</b><span>{label}</span></div>
}

export default function Librarian() {
  const { user } = useAuth()
  const [dashboard, setDashboard] = useState(null)
  const [dashboardError, setDashboardError] = useState('')
  const [form, setForm] = useState({ topic: '', targetAge: '20대', mood: '따뜻한', bookCount: 5 })
  const [generated, setGenerated] = useState(null)
  const [message, setMessage] = useState('')
  const [notice, setNotice] = useState('')
  const [generating, setGenerating] = useState(false)
  const [saving, setSaving] = useState(false)

  const loadDashboard = () => {
    api.librarianDashboard()
      .then((data) => { setDashboard(data); setDashboardError('') })
      .catch((err) => setDashboardError(errorMessage(err, '대시보드 정보를 불러오지 못했습니다.')))
  }

  useEffect(loadDashboard, [])

  const createDraft = async () => {
    if (!form.topic.trim()) return setMessage('전시 주제를 입력해 주세요.')
    setGenerating(true)
    setMessage('')
    setNotice('')
    try {
      setGenerated(await api.generateCuration({
        topic: form.topic.trim(),
        targetAge: form.targetAge,
        mood: form.mood,
        bookCount: Number(form.bookCount),
        excludedKeywords: [],
        purpose: '전시 큐레이션',
      }))
    } catch (err) {
      setGenerated(null)
      setMessage(errorMessage(err, 'AI 초안을 생성하지 못했습니다.'))
    } finally {
      setGenerating(false)
    }
  }

  const saveDraft = async (isPublic) => {
    if (!generated) return
    setSaving(true)
    setMessage('')
    try {
      await api.saveCuration({
        title: generated.title,
        description: generated.description,
        isPublic,
        books: generated.books.map((book, index) => ({
          isbn: book.isbn,
          displayOrder: index + 1,
          comment: book.reason,
        })),
      })
      setNotice(`큐레이션을 ${isPublic ? '공개 상태로 ' : ''}저장했습니다. 운영 도구에서 수정할 수 있어요.`)
      loadDashboard()
    } catch (err) {
      setMessage(errorMessage(err, '큐레이션을 저장하지 못했습니다.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="page librarian">
      <p className="eyebrow"><span /> LIBRARIAN STUDIO · {user.name} 사서</p>
      <div className="librarian-title-row">
        <div>
          <h1 className="page-title">사서를 위한<br />AI 북 큐레이션</h1>
          <p className="lead">
            {user.libraryName || '소속 도서관'}({user.libraryCode})의 저이용 도서를 전시 큐레이션으로 연결합니다.
          </p>
        </div>
        <div className="librarian-title-actions">
          <Link className="secondary-button" to={`/libraries/${user.libraryCode}`}>
            우리 도서관 잠자는 책 보기
          </Link>
          <Link className="secondary-button" to="/librarian/operations">운영 도구 보기 <ArrowRight size={15} /></Link>
        </div>
      </div>

      <Notice>{dashboardError}</Notice>

      {dashboard && (
        <>
          <div className="dash-stats">
            <Stat value={dashboard.hiddenBookCount} label="등록된 잠자는 도서" />
            <Stat value={dashboard.monthlyCurationCount} label="이번 달 만든 큐레이션" />
            <Stat value={dashboard.recentCurations?.length || 0} label="최근 큐레이션" />
          </div>

          {dashboard.hiddenBookCount === 0 && (
            <Notice>
              아직 우리 도서관의 잠자는 도서 후보군이 없습니다. 운영 도구에서 “장서 대출목록” CSV를 등록하면
              실제 대출건수를 기준으로 만들 수 있고, CSV 없이 정보나루 API로 만들려면 탐색 화면의 도서관 선택에서 추가하면 됩니다.
            </Notice>
          )}

          {dashboard.popularKeywords?.length > 0 && (
            <div className="tags librarian-keywords">
              {dashboard.popularKeywords.map((keyword) => <span key={keyword}>#{keyword}</span>)}
            </div>
          )}
        </>
      )}

      <div className="curation-maker">
        <div>
          <span className="badge">새 큐레이션 만들기</span>
          <h2>전시의 주제를 입력해 주세요.</h2>
          <p>우리 도서관의 잠자는 도서 후보군 중에서 AI가 전시 도서와 문구 초안을 만들어 드립니다.</p>
          <input
            value={form.topic}
            onChange={(event) => setForm({ ...form, topic: event.target.value })}
            placeholder="예: 청년의 불안"
          />
          <div className="form-row">
            <select value={form.targetAge} onChange={(event) => setForm({ ...form, targetAge: event.target.value })}>
              {TARGET_AGES.map((age) => <option key={age} value={age}>대상: {age}</option>)}
            </select>
            <select value={form.mood} onChange={(event) => setForm({ ...form, mood: event.target.value })}>
              {MOODS.map((mood) => <option key={mood} value={mood}>분위기: {mood}</option>)}
            </select>
            <select value={form.bookCount} onChange={(event) => setForm({ ...form, bookCount: event.target.value })}>
              {[3, 5, 7, 10].map((count) => <option key={count} value={count}>{count}권</option>)}
            </select>
          </div>
          <button className="primary-link" onClick={createDraft} disabled={generating}>
            <WandSparkles size={16} /> {generating ? 'AI 초안 생성 중...' : 'AI 초안 생성하기'}
          </button>
          <Notice>{message}</Notice>
          <Notice tone="success">{notice}</Notice>
        </div>

        {generated ? (
          <div className="curation-result">
            <span>AI 생성 초안</span>
            <h3>{generated.title}</h3>
            <p>{generated.description}</p>
            {generated.hashtags?.length > 0 && (
              <div className="tags">{generated.hashtags.map((tag) => <span key={tag}>{tag}</span>)}</div>
            )}
            <div>
              {generated.books.map((book, index) => (
                <p key={book.isbn}>
                  <b>{String(index + 1).padStart(2, '0')}</b>
                  <span><strong>{book.title}</strong>{book.reason}</span>
                </p>
              ))}
            </div>
            <div className="curation-save-actions">
              <button className="outline-link" onClick={() => saveDraft(false)} disabled={saving}>
                <Save size={15} /> 비공개로 저장
              </button>
              <button className="primary-link" onClick={() => saveDraft(true)} disabled={saving}>
                공개로 저장 <ArrowRight size={15} />
              </button>
            </div>
          </div>
        ) : (
          <div className="curation-placeholder">
            <Library size={38} />
            <p>주제를 입력하면<br />전시 도서와 문구 초안을 만들어 드려요.</p>
          </div>
        )}
      </div>

      {dashboard?.recentCurations?.length > 0 && (
        <div className="curation-list recent-curations">
          <h2>최근 만든 큐레이션</h2>
          {dashboard.recentCurations.map((curation) => (
            <article key={curation.id}>
              <div className="curation-status">
                <span className={curation.isPublic ? 'public' : 'private'}>
                  {curation.isPublic ? '공개 중' : '비공개'}
                </span>
              </div>
              <h3>{curation.title}</h3>
              <footer><span>추천 도서 {curation.bookCount}권</span></footer>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

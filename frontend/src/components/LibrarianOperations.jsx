import { useMemo, useRef, useState } from 'react'
import { AlertCircle, CheckCircle2, FileSpreadsheet, Pencil, Plus, Save, Trash2, Upload, XCircle } from 'lucide-react'
import { api } from '../api/client'
import { useAuth } from '../App'

const initialCurations = [
  { id: 101, title: '괜찮지 않아도 괜찮은 우리에게', description: '불안을 이해하고 나를 돌보는 데 도움이 되는 저이용 도서 5권을 소개합니다.', isPublic: true, period: '2026.08.10 ~ 08.31', count: 5 },
  { id: 102, title: '관계의 온도를 다시 생각하다', description: '건강한 거리와 대화의 방법을 다룬 도서를 모았습니다.', isPublic: false, period: '2026.08.01 ~ 08.20', count: 4 },
]

function CsvPanel() {
  const { user } = useAuth()
  const fileRef = useRef(null); const [fileName, setFileName] = useState(''); const [result, setResult] = useState(null); const [message, setMessage] = useState(''); const [status, setStatus] = useState('idle'); const [uploading, setUploading] = useState(false)
  const libraryCode = user?.libraryCode; const libraryName = user?.libraryName
  const uploadCsv = async (file) => {
    if (!file) return
    if (!libraryCode || !libraryName) { setStatus('error'); setMessage('사서 계정에 소속 도서관 정보가 없습니다. 회원가입 시 입력한 도서관 코드를 확인해 주세요.'); return }
    setFileName(file.name); setMessage(''); setStatus('idle'); setResult(null); setUploading(true)
    try {
      const response = await api.uploadHiddenBooks(libraryCode, libraryName, file)
      setResult(response)
      setStatus('success')
      setMessage(`${response.libraryName}(${response.libraryCode})의 전체 ${response.totalRows}행 중 ${response.savedCount}권을 잠자는 도서 후보군으로 등록했습니다.`)
    } catch (err) {
      setStatus('error')
      setMessage(err.response?.data?.message || 'CSV 업로드에 실패했습니다. 파일 형식과 서버 상태를 확인해 주세요.')
    } finally {
      setUploading(false)
    }
  }
  return <section className="operation-panel"><div className="operation-heading"><div><span className="badge"><FileSpreadsheet size={13} /> CSV 후보군</span><h2>장서·대출 CSV 업로드</h2><p>정보나루에서 내려받은 "장서 대출목록" CSV를 올리면 {libraryName || '소속 도서관'}의 저이용 도서 후보군을 다시 만듭니다. 같은 도서관의 기존 후보군은 전부 새로 교체됩니다.</p></div></div><input ref={fileRef} className="file-input" type="file" accept=".csv,text/csv" onChange={e => uploadCsv(e.target.files?.[0])} disabled={uploading} /><button className="upload-dropzone" onClick={() => fileRef.current?.click()} disabled={uploading}><Upload size={25} /><b>{uploading ? '업로드 중...' : fileName || 'CSV 파일을 선택하세요'}</b><span>정보나루 오픈데이터 &gt; 장서 대출목록 CSV (컬럼: 도서명, 저자, 출판사, ISBN, 주제분류번호, 대출건수 등)</span></button>{message && <p className={status === 'error' ? 'operation-message error' : 'operation-message'}>{status === 'success' ? <CheckCircle2 size={15} /> : <XCircle size={15} />} {message}</p>}{result && <div className="upload-summary compact"><div><b>{result.totalRows}</b><span>CSV 전체 행</span></div><div><b>{result.savedCount}</b><span>후보군에 등록됨</span></div></div>}</section>
}

function CurationPanel() {
  const [curations, setCurations] = useState(initialCurations); const [editing, setEditing] = useState(null); const [notice, setNotice] = useState(''); const count = useMemo(() => curations.filter(item => item.isPublic).length, [curations])
  const save = async (event) => { event.preventDefault(); const form = new FormData(event.currentTarget); const next = { ...editing, title: form.get('title'), description: form.get('description'), period: form.get('period'), isPublic: form.get('isPublic') === 'on' }; try { await api.updateCuration(next.id, { title: next.title, description: next.description, isPublic: next.isPublic }) } catch {} setCurations(items => items.map(item => item.id === next.id ? next : item)); setEditing(null); setNotice('큐레이션 내용을 저장했습니다.') }
  const remove = async (item) => { if (!window.confirm(`“${item.title}” 큐레이션을 삭제할까요?`)) return; try { await api.deleteCuration(item.id) } catch {} setCurations(items => items.filter(curation => curation.id !== item.id)); setNotice('큐레이션을 삭제했습니다.') }
  return <section className="operation-panel curation-list-panel"><div className="operation-heading"><div><span className="badge"><Pencil size={13} /> CURATION MANAGER</span><h2>큐레이션 목록 관리</h2><p>저장한 전시 큐레이션을 수정하고 공개 상태를 관리합니다.</p></div><div className="curation-count"><b>{curations.length}</b><span>전체 · 공개 {count}</span></div></div>{notice && <p className="operation-message"><CheckCircle2 size={15} /> {notice}</p>}<div className="curation-list">{curations.map(item => <article key={item.id}><div className="curation-status"><span className={item.isPublic ? 'public' : 'private'}>{item.isPublic ? '공개 중' : '비공개'}</span><span>{item.period}</span></div><h3>{item.title}</h3><p>{item.description}</p><footer><span>추천 도서 {item.count}권</span><div><button onClick={() => setEditing(item)}><Pencil size={14} /> 수정</button><button className="delete-button" onClick={() => remove(item)}><Trash2 size={14} /> 삭제</button></div></footer></article>)}</div>{!curations.length && <div className="empty-operation"><AlertCircle size={28} /><p>저장된 큐레이션이 없습니다.</p><button onClick={() => setCurations(initialCurations)}><Plus size={15} /> 샘플 목록 복원</button></div>}{editing && <div className="modal-backdrop"><form className="curation-modal" onSubmit={save}><div><span className="badge">큐레이션 수정</span><button type="button" className="modal-close" onClick={() => setEditing(null)}>×</button></div><h2>전시 큐레이션 편집</h2><label>제목<input name="title" defaultValue={editing.title} required /></label><label>소개 문구<textarea name="description" defaultValue={editing.description} required /></label><label>전시 기간<input name="period" defaultValue={editing.period} required /></label><label className="public-check"><input name="isPublic" type="checkbox" defaultChecked={editing.isPublic} /> 이용자에게 공개하기</label><button className="primary-link" type="submit"><Save size={15} /> 변경사항 저장</button></form></div>}</section>
}

export default function LibrarianOperations() { return <section className="page librarian-operations"><p className="eyebrow"><span /> LIBRARIAN OPERATIONS</p><h1 className="page-title">사서 관리 도구</h1><p className="lead">장서 데이터를 등록하고, 전시 큐레이션을 한 곳에서 관리하세요.</p><div className="operations-grid"><CsvPanel /><CurationPanel /></div></section> }

import { useMemo, useRef, useState } from 'react'
import { AlertCircle, CheckCircle2, FileSpreadsheet, Pencil, Plus, Save, Trash2, Upload } from 'lucide-react'
import { api } from '../api/client'

const initialCurations = [
  { id: 101, title: '괜찮지 않아도 괜찮은 우리에게', description: '불안을 이해하고 나를 돌보는 데 도움이 되는 저이용 도서 5권을 소개합니다.', isPublic: true, period: '2026.08.10 ~ 08.31', count: 5 },
  { id: 102, title: '관계의 온도를 다시 생각하다', description: '건강한 거리와 대화의 방법을 다룬 도서를 모았습니다.', isPublic: false, period: '2026.08.01 ~ 08.20', count: 4 },
]
const sampleCandidates = [
  { title: '관계에도 연습이 필요합니다', author: '박상미', loans: 2, score: 93 },
  { title: '심리학이 이렇게 쓸모 있을 줄이야', author: '류쉬안', loans: 1, score: 89 },
  { title: '나를 사랑하는 연습', author: '정은길', loans: 2, score: 85 },
]

function CsvPanel() {
  const fileRef = useRef(null); const [fileName, setFileName] = useState(''); const [result, setResult] = useState(null); const [message, setMessage] = useState('')
  const inspectCsv = async (file) => {
    if (!file) return
    setFileName(file.name); setMessage('')
    const lines = (await file.text()).split(/\r?\n/).filter(Boolean); const header = (lines[0] || '').toLowerCase(); const valid = /(isbn|도서|title|제목)/.test(header)
    const parsed = { rows: Math.max(0, lines.length - 1), validRows: valid ? Math.max(0, lines.length - 1) : 0, invalidRows: valid ? 0 : Math.max(0, lines.length - 1), columns: lines[0]?.split(',').length || 0 }
    try { await api.uploadHiddenBooks('121018', 'WakeBook 시연도서관', file); setMessage('CSV를 등록하고 후보군 생성을 요청했습니다.') } catch { setMessage('파일 구조를 확인하고 후보군 미리보기를 생성했습니다.') }
    setResult(parsed)
  }
  return <section className="operation-panel"><div className="operation-heading"><div><span className="badge"><FileSpreadsheet size={13} /> CSV 후보군</span><h2>장서·대출 CSV 업로드</h2><p>도서관별 장서와 대출 이력을 바탕으로 저이용 도서 후보군을 생성합니다.</p></div></div><input ref={fileRef} className="file-input" type="file" accept=".csv,text/csv" onChange={e => inspectCsv(e.target.files?.[0])} /><button className="upload-dropzone" onClick={() => fileRef.current?.click()}><Upload size={25} /><b>{fileName || 'CSV 파일을 선택하세요'}</b><span>권장 열: ISBN, 제목, 저자, 대출횟수</span></button>{result && <><div className="upload-summary">{[[result.rows, '전체 행'], [result.validRows, '정상 행'], [result.invalidRows, '확인 필요'], [result.columns, '인식 열']].map(([value, label]) => <div key={label}><b>{value}</b><span>{label}</span></div>)}</div><p className="operation-message"><CheckCircle2 size={15} /> {message}</p><div className="candidate-header"><h3>생성된 잠자는 도서 후보</h3><span>대출 수 2회 이하 · 품질 기준 통과</span></div><div className="candidate-list">{sampleCandidates.map((book, index) => <article key={book.title}><b>{index + 1}</b><div><strong>{book.title}</strong><span>{book.author} · 최근 대출 {book.loans}회</span></div><em>적합도 {book.score}</em></article>)}</div></>}</section>
}

function CurationPanel() {
  const [curations, setCurations] = useState(initialCurations); const [editing, setEditing] = useState(null); const [notice, setNotice] = useState(''); const count = useMemo(() => curations.filter(item => item.isPublic).length, [curations])
  const save = async (event) => { event.preventDefault(); const form = new FormData(event.currentTarget); const next = { ...editing, title: form.get('title'), description: form.get('description'), period: form.get('period'), isPublic: form.get('isPublic') === 'on' }; try { await api.updateCuration(next.id, { title: next.title, description: next.description, isPublic: next.isPublic }) } catch {} setCurations(items => items.map(item => item.id === next.id ? next : item)); setEditing(null); setNotice('큐레이션 내용을 저장했습니다.') }
  const remove = async (item) => { if (!window.confirm(`“${item.title}” 큐레이션을 삭제할까요?`)) return; try { await api.deleteCuration(item.id) } catch {} setCurations(items => items.filter(curation => curation.id !== item.id)); setNotice('큐레이션을 삭제했습니다.') }
  return <section className="operation-panel curation-list-panel"><div className="operation-heading"><div><span className="badge"><Pencil size={13} /> CURATION MANAGER</span><h2>큐레이션 목록 관리</h2><p>저장한 전시 큐레이션을 수정하고 공개 상태를 관리합니다.</p></div><div className="curation-count"><b>{curations.length}</b><span>전체 · 공개 {count}</span></div></div>{notice && <p className="operation-message"><CheckCircle2 size={15} /> {notice}</p>}<div className="curation-list">{curations.map(item => <article key={item.id}><div className="curation-status"><span className={item.isPublic ? 'public' : 'private'}>{item.isPublic ? '공개 중' : '비공개'}</span><span>{item.period}</span></div><h3>{item.title}</h3><p>{item.description}</p><footer><span>추천 도서 {item.count}권</span><div><button onClick={() => setEditing(item)}><Pencil size={14} /> 수정</button><button className="delete-button" onClick={() => remove(item)}><Trash2 size={14} /> 삭제</button></div></footer></article>)}</div>{!curations.length && <div className="empty-operation"><AlertCircle size={28} /><p>저장된 큐레이션이 없습니다.</p><button onClick={() => setCurations(initialCurations)}><Plus size={15} /> 샘플 목록 복원</button></div>}{editing && <div className="modal-backdrop"><form className="curation-modal" onSubmit={save}><div><span className="badge">큐레이션 수정</span><button type="button" className="modal-close" onClick={() => setEditing(null)}>×</button></div><h2>전시 큐레이션 편집</h2><label>제목<input name="title" defaultValue={editing.title} required /></label><label>소개 문구<textarea name="description" defaultValue={editing.description} required /></label><label>전시 기간<input name="period" defaultValue={editing.period} required /></label><label className="public-check"><input name="isPublic" type="checkbox" defaultChecked={editing.isPublic} /> 이용자에게 공개하기</label><button className="primary-link" type="submit"><Save size={15} /> 변경사항 저장</button></form></div>}</section>
}

export default function LibrarianOperations() { return <section className="page librarian-operations"><p className="eyebrow"><span /> LIBRARIAN OPERATIONS</p><h1 className="page-title">사서 관리 도구</h1><p className="lead">장서 데이터를 등록하고, 전시 큐레이션을 한 곳에서 관리하세요.</p><div className="operations-grid"><CsvPanel /><CurationPanel /></div></section> }

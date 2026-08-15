import { CheckCircle2, Loader, XCircle } from 'lucide-react'

/** 후보군 산출 작업의 진행 상태. 실패를 성공처럼 보여 주지 않도록 상태별로 구분해 표시한다. */
export default function JobProgress({ job }) {
  if (!job) return null

  const running = job.status === 'PENDING' || job.status === 'RUNNING'
  const percent = job.totalCandidates > 0
    ? Math.min(100, Math.round((job.processedCount / job.totalCandidates) * 100))
    : 0

  if (job.status === 'FAILED') {
    return (
      <div className="job-progress failed">
        <XCircle size={16} />
        <span>{job.message || '후보군을 만들지 못했습니다.'}</span>
      </div>
    )
  }

  if (job.status === 'SUCCEEDED') {
    return (
      <div className="job-progress done">
        <CheckCircle2 size={16} />
        <span>
          {job.libraryName ? `${job.libraryName}: ` : ''}
          {job.message || `잠자는 도서 ${job.savedCount}권을 준비했습니다.`}
        </span>
      </div>
    )
  }

  return (
    <div className="job-progress running">
      <div className="job-progress-head">
        <Loader size={16} className="spin" />
        <span>
          {job.status === 'PENDING'
            ? '후보 도서를 고르는 중입니다...'
            : `후보 도서를 확인하는 중입니다 (${job.processedCount}/${job.totalCandidates}권, ${job.savedCount}권 선정)`}
        </span>
      </div>
      <div className="job-progress-bar"><span style={{ width: `${percent}%` }} /></div>
      <small>도서마다 정보나루 조회가 필요해 몇 분까지 걸릴 수 있습니다. 이 화면을 열어 두세요.</small>
    </div>
  )
}

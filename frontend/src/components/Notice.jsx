import { AlertCircle, CheckCircle2 } from 'lucide-react'

/**
 * 실패를 성공처럼 보여 주지 않기 위해, 안내와 오류를 같은 컴포넌트에서 구분해 표시한다.
 * tone: 'error' | 'success'
 */
export default function Notice({ tone = 'error', children }) {
  if (!children) return null
  const Icon = tone === 'success' ? CheckCircle2 : AlertCircle
  return (
    <p className={tone === 'success' ? 'api-message success' : 'api-message'}>
      <Icon size={14} /> {children}
    </p>
  )
}

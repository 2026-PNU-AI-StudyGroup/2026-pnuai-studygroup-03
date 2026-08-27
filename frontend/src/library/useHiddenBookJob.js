import { useCallback, useEffect, useRef, useState } from 'react'
import { api, errorMessage } from '../api/client'

const POLL_INTERVAL_MS = 3000

export const SOURCE_LABEL = {
  CSV_UPLOAD: '사서가 올린 대출 데이터 기준',
  LIBRARY_API: '정보나루 대출 순위 밖 장서',
  DEMO_SEED: '체험용으로 미리 준비된 데이터',
}

/**
 * 후보군 산출 작업의 진행 상태를 폴링한다. 몇 분 걸리는 작업이라,
 * 화면이 멈춘 것처럼 보이지 않게 진행률을 계속 보여 준다.
 */
export function useHiddenBookJob({ onFinished } = {}) {
  const [job, setJob] = useState(null)
  const [error, setError] = useState('')
  const [starting, setStarting] = useState(false)
  const timerRef = useRef(null)
  const finishedRef = useRef(onFinished)
  finishedRef.current = onFinished

  const clearTimer = () => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }

  useEffect(() => clearTimer, [])

  const poll = useCallback((jobId) => {
    clearTimer()
    timerRef.current = setTimeout(async () => {
      try {
        const next = await api.hiddenBookJob(jobId)
        setJob(next)
        if (next.status === 'PENDING' || next.status === 'RUNNING') {
          poll(jobId)
        } else {
          finishedRef.current?.(next)
        }
      } catch (err) {
        setError(errorMessage(err, '작업 상태를 확인하지 못했습니다.'))
      }
    }, POLL_INTERVAL_MS)
  }, [])

  const track = useCallback((started) => {
    setJob(started)
    setError('')
    poll(started.jobId)
  }, [poll])

  const start = useCallback(async (request) => {
    setStarting(true)
    setError('')
    setJob(null)
    try {
      track(await request())
    } catch (err) {
      setError(errorMessage(err, '후보군 산출을 시작하지 못했습니다.'))
    } finally {
      setStarting(false)
    }
  }, [track])

  const running = Boolean(job && (job.status === 'PENDING' || job.status === 'RUNNING'))

  return { job, error, starting, running, start, reset: () => { clearTimer(); setJob(null); setError('') } }
}

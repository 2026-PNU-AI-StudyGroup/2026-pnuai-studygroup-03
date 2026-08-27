import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ArrowRight } from 'lucide-react'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'

export default function AuthPage({ signup = false }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { login, register } = useAuth()
  const [role, setRole] = useState('USER')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (event) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    if (signup && form.get('password') !== form.get('passwordConfirm')) {
      return setError('비밀번호가 서로 일치하지 않습니다.')
    }
    setLoading(true)
    setError('')
    try {
      const account = signup
        ? await register({
          role,
          name: form.get('name'),
          email: form.get('email'),
          password: form.get('password'),
          nickname: form.get('nickname') || null,
          libraryName: role === 'LIBRARIAN' ? form.get('libraryName') : null,
          libraryCode: role === 'LIBRARIAN' ? form.get('libraryCode') : null,
          department: role === 'LIBRARIAN' ? form.get('department') : null,
        })
        : await login({ email: form.get('email'), password: form.get('password') })

      const redirectTo = location.state?.from
      navigate(redirectTo || (account.role === 'LIBRARIAN' ? '/librarian' : '/'), { replace: true })
    } catch (err) {
      setError(errorMessage(err, '요청에 실패했습니다. 백엔드 서버 상태를 확인해 주세요.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="auth">
      <Link className="logo" to="/"><span>W</span>WakeBook</Link>
      <form onSubmit={handleSubmit}>
        <p className="eyebrow"><span /> {signup ? 'JOIN WAKEBOOK' : 'WELCOME BACK'}</p>
        <h1>{signup ? '함께 책을 깨워볼까요?' : '다시 만나서 반가워요.'}</h1>

        {signup && (
          <>
            <label>이름<input name="name" required maxLength={100} placeholder="이름을 입력하세요" /></label>
            <label>닉네임<input name="nickname" maxLength={100} placeholder="닉네임을 입력하세요 (선택)" /></label>
            <div className="role-choice">
              <button type="button" className={role === 'USER' ? 'selected' : ''} onClick={() => setRole('USER')}>일반 사용자</button>
              <button type="button" className={role === 'LIBRARIAN' ? 'selected' : ''} onClick={() => setRole('LIBRARIAN')}>사서</button>
            </div>
            {role === 'LIBRARIAN' && (
              <>
                <label>소속 도서관<input name="libraryName" required maxLength={200} placeholder="예: 부산광역시 금정도서관" /></label>
                <label>
                  도서관 코드
                  <input name="libraryCode" required maxLength={20} placeholder="예: 121018" />
                  <small>도서관정보나루에 등록된 우리 도서관 코드입니다. 이 코드의 장서만 등록·수정할 수 있습니다.</small>
                </label>
                <label>담당 부서<input name="department" required maxLength={100} placeholder="예: 자료운영팀" /></label>
              </>
            )}
          </>
        )}

        <label>이메일<input name="email" type="email" required placeholder="hello@wakebook.kr" /></label>
        <label>비밀번호<input name="password" type="password" required placeholder="••••••••" /></label>
        {signup && <label>비밀번호 확인<input name="passwordConfirm" type="password" required placeholder="••••••••" /></label>}

        {error && <p className="form-error">{error}</p>}

        <button className="primary-link" type="submit" disabled={loading}>
          {loading ? '처리 중...' : signup ? `${role === 'LIBRARIAN' ? '사서' : '사용자'} 계정 만들기` : '로그인'}
          <ArrowRight size={17} />
        </button>
        <p className="auth-switch">
          {signup ? '이미 계정이 있으신가요?' : '아직 WakeBook이 처음이신가요?'}{' '}
          <Link to={signup ? '/login' : '/signup'}>{signup ? '로그인' : '회원가입'}</Link>
        </p>
      </form>
    </section>
  )
}

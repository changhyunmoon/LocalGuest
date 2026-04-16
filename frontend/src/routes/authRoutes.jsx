import { LoginPage } from '../pages/LoginPage'
import { SignupPage } from '../pages/SignupPage'

export const authRoutes = [
  { path: 'auth/login', element: <LoginPage /> },
  { path: 'auth/signup', element: <SignupPage /> },
]


import { useEffect, useState } from 'react';
import {
  BrowserRouter,
  Link,
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
} from 'react-router-dom';
import { AuthProvider, useAuth } from '../features/auth/AuthProvider';
import { AuthPage } from '../features/auth/AuthPage';
import { LearningProvider } from '../features/learning-setup/LearningProvider';
import { SetupPage } from '../features/learning-setup/SetupPage';
import { ProfilePage } from '../features/profile/ProfilePage';
import { AppLayout } from './layouts/desktop/AppLayout';
import { DashboardPage } from '../features/learning/DashboardPage';
import { CoursePage, CoursesPage } from '../features/learning/CoursesPage';
import { LessonPage } from '../features/learning/LessonPage';
import { AssessmentPage, AssessmentsPage } from '../features/assessment/AssessmentPage';
import { AttemptPage } from '../features/assessment/AttemptPage';
import { ResultPage } from '../features/assessment/ResultPage';
import { VocabularyPage } from '../features/vocabulary/VocabularyPage';
import { HistoryPage, ProgressPage } from '../features/progress/ProgressPages';
import { Empty, FormError } from '../shared/ui/States';

function Protected() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const [logoutError, setLogoutError] = useState<unknown>();
  if (!user)
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (user.status !== 'ACTIVE') return <Empty title="Tài khoản hiện không khả dụng." />;
  if (user.role !== 'LEARNER')
    return (
      <Empty title="Bạn đang đăng nhập tài khoản quản trị.">
        <p>Hãy dùng tài khoản Learner để mở không gian học.</p>
        <FormError error={logoutError} />
        <button className="button" onClick={() => void logout().catch(setLogoutError)}>
          Đăng xuất
        </button>
      </Empty>
    );
  return (
    <LearningProvider>
      <Outlet />
    </LearningProvider>
  );
}

function ScrollToTop() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);
  return null;
}

export function App() {
  return (
    <BrowserRouter>
      <ScrollToTop />
      {import.meta.env.DEV && import.meta.env.MODE === 'demo' && (
        <div className="mock-banner" role="status">
          <strong>MOCK / DEMO</strong> — Dữ liệu mẫu trên trình duyệt. Chưa kết nối backend thật.
        </div>
      )}
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<AuthPage />} />
          <Route path="/register" element={<AuthPage register />} />
          <Route element={<Protected />}>
            <Route element={<AppLayout />}>
              <Route index element={<DashboardPage />} />
              <Route path="profile" element={<ProfilePage />} />
              <Route path="setup" element={<SetupPage />} />
              <Route path="courses" element={<CoursesPage />} />
              <Route path="courses/:courseId" element={<CoursePage />} />
              <Route path="assessments" element={<AssessmentsPage />} />
              <Route path="assessments/:assessmentId" element={<AssessmentPage />} />
              <Route path="results/:resultId" element={<ResultPage />} />
              <Route path="vocabulary" element={<VocabularyPage />} />
              <Route path="progress" element={<ProgressPage />} />
              <Route path="history" element={<HistoryPage />} />
            </Route>
            <Route path="lessons/:lessonId" element={<LessonPage />} />
            <Route path="attempts/:attemptId" element={<AttemptPage />} />
          </Route>
          <Route
            path="*"
            element={
              <Empty title="Không tìm thấy trang này.">
                <Link className="button" to="/">
                  Về trang chủ
                </Link>
              </Empty>
            }
          />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

// Isolated TEST only; all business fixtures go through owner APIs via Gateway.
test.skip(process.env.RUN_KHOA_FINAL_LIVE !== '1', 'Requires the isolated Khoa TEST stack.');
const origin = 'http://127.0.0.1:5173';
const courseId = '10000000-0000-4000-8000-000000000001';
const levelId = '10000000-0000-4000-8000-000000000002';
const otherCourseId = '20000000-0000-4000-8000-000000000001';
const otherLevelId = '20000000-0000-4000-8000-000000000002';
const checks: { method: string; path: string; expected: number; actual: number }[] = [];
test.beforeEach(({ page }) => { checks.length = 0; test.setTimeout(120000); page.setDefaultTimeout(15000); });
test.afterEach(async ({}, info) => {
  await info.attach('sanitized-http', { body: JSON.stringify(checks), contentType: 'application/json' });
});
async function call(api: APIRequestContext, method: string, path: string, data?: unknown,
                    expected = 200, extra: Record<string, string> = {}) {
  const csrf = !['GET', 'HEAD'].includes(method)
    ? (await (await api.get('/api/v1/auth/csrf')).json()).csrfToken : '';
  const response = await api.fetch('/api/v1' + path, {
    method, data, headers: { Origin: origin, ...(csrf ? { 'X-CSRF-Token': csrf } : {}), ...extra },
  });
  checks.push({ method, path: path.split('?')[0], expected, actual: response.status() });
  expect(response.status(), `${method} ${path}`).toBe(expected);
  return response.status() === 204 ? null : response.json();
}
async function admin(page: Page) {
  const password = readFileSync(resolve(process.env.KHOA_STACK_ROOT!, '.secrets/admin_password'), 'utf8').trim();
  // The UI also records the non-secret Admin navigation hint used on reload.
  await page.goto('/login');
  await page.getByLabel('Email', { exact: true }).fill('admin.hung.w7@example.com');
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: /Đăng nhập/ }).click();
  await expect(page.getByRole('heading', { name: 'Chăm chút từng bài học.' })).toBeVisible();
}
async function lesson(page: Page) {
  await call(page.request, 'PATCH', `/admin/courses/${courseId}`, { published: true });
  const module = await call(page.request, 'POST', '/admin/modules', {
    courseId, title: `Khoa W8 ${crypto.randomUUID()}`, orderNo: Date.now() % 1000000000,
  }, 201);
  await call(page.request, 'PATCH', `/admin/modules/${module.moduleId}`, { published: true });
  return call(page.request, 'POST', '/admin/lessons', {
    moduleId: module.moduleId, title: `Khoa W8 lesson ${crypto.randomUUID()}`,
    content: 'Owner API fixture', orderNo: 1,
  }, 201);
}
async function exercise(page: Page, lessonId: string, points = 1) {
  const a = await call(page.request, 'POST', '/admin/assessments', {
    type: 'EXERCISE', courseId, lessonId, title: `Khoa W8 exercise ${crypto.randomUUID()}`,
    timeLimitSeconds: null,
  }, 201);
  const q = await call(page.request, 'POST', `/admin/assessments/${a.assessmentId}/questions`, {
    questionType: 'TRUE_FALSE', content: 'Khoa fractional question', points, correctAnswer: false,
  }, 201);
  return { a, q };
}


test('LIVE regression52: edit question retains fractional points', async ({page}, info) => {
  await admin(page);
  const l=await lesson(page);
  const {a,q}=await exercise(page,l.lessonId,0.001);
  await page.goto('/admin/assessments');
  await page.getByRole('button',{name:`Xem / sửa ${a.title}`,exact:true}).click();
  await page.getByRole('button',{name:`Sửa câu hỏi ${q.content}`,exact:true}).click();
  await expect(page.getByLabel('Điểm')).toHaveValue('0.001');
  const updated='Fractional score preserved '+crypto.randomUUID();
  await page.getByLabel('Nội dung câu hỏi').fill(updated);
  const response=page.waitForResponse(r=>r.request().method()==='PUT' && r.url().includes(`/questions/${q.questionId}`));
  await page.getByRole('button',{name:'Lưu câu hỏi'}).click();
  expect((await response).status()).toBe(200);
  const detail=await call(page.request,'GET',`/admin/assessments/${a.assessmentId}`);
  expect(detail.questions.find((x:any)=>x.questionId===q.questionId)).toMatchObject({points:0.001,content:updated,correctAnswer:false});
  await info.attach('fixture',{body:JSON.stringify({assessmentId:a.assessmentId,questionId:q.questionId}),contentType:'application/json'});
});

test('LIVE with injected save503 regression49: GET and tab return preserve unsaved until real ACK',async({page},info)=>{
  await admin(page);
  const l=await lesson(page);
  const {a,q}=await exercise(page,l.lessonId);
  await call(page.request,'PATCH',`/admin/assessments/${a.assessmentId}`,{ready:true});
  await call(page.request,'PATCH',`/admin/lessons/${l.lessonId}`,{published:true});
  await call(page.request,'POST','/auth/logout',undefined,204);
  const credentials={email:`khoa-final-${crypto.randomUUID()}@example.com`,password:crypto.randomUUID()+'-Aa1'};
  await call(page.request,'POST','/auth/register',credentials,201);
  await page.goto('/login');
  await page.getByLabel('Email',{exact:true}).fill(credentials.email);
  await page.getByLabel('Mật khẩu',{exact:true}).fill(credentials.password);
  await page.getByRole('button',{name:/Đăng nhập/}).click();
  await page.waitForURL(u=>u.pathname!=='/login');
  await call(page.request,'PUT','/me/learning-setup',{courseId,levelId});
  await page.goto('/profile');
  await page.getByLabel('Họ tên',{exact:true}).fill('Khoa Final LIVE');
  await page.getByRole('button',{name:'Lưu thay đổi'}).click();
  await expect(page.getByText('Đã cập nhật hồ sơ.')).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('Họ tên',{exact:true})).toHaveValue('Khoa Final LIVE');
  const attempt=await call(page.request,'POST',`/assessments/${a.assessmentId}/attempts`,undefined,201,{'Idempotency-Key':crypto.randomUUID()});
  await page.goto(`/attempts/${attempt.attemptId}`);
  let failed=false;
  await page.route(`**/api/v1/attempts/${attempt.attemptId}/answers`,async route=>{
    if(!failed){failed=true;await route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({code:'UNAVAILABLE',message:'Injected save unavailable',errors:[]})});}
    else await route.continue();
  });
  await page.getByRole('radio',{name:'Sai',exact:true}).check();
  await expect(page.getByText('Đáp án mới chưa lưu.')).toBeVisible();
  const get=page.waitForResponse(r=>r.request().method()==='GET' && r.url().endsWith(`/attempts/${attempt.attemptId}`));
  await page.getByRole('button',{name:'Kiểm tra trạng thái máy chủ'}).click();
  expect((await get).status()).toBe(200);
  await expect(page.getByText('Đáp án mới chưa lưu.')).toBeVisible();
  const tab=await page.context().newPage();await tab.goto('about:blank');await tab.bringToFront();await page.bringToFront();await tab.close();
  await expect(page.getByText('Đáp án mới chưa lưu.')).toBeVisible();
  const ack=page.waitForResponse(r=>r.request().method()==='PUT' && r.url().endsWith(`/attempts/${attempt.attemptId}/answers`));
  await page.getByRole('button',{name:'Lưu lại đáp án'}).click();
  expect((await ack).status()).toBe(200);
  await expect(page.getByText('Đã tải/lưu đáp án từ máy chủ.')).toBeVisible();
  await page.getByRole('button',{name:'Nộp bài',exact:true}).click();
  await expect(page.getByText('1/1',{exact:true})).toBeVisible();
  await info.attach('scope',{body:JSON.stringify({type:'LIVE_WITH_BROWSER_FAULT_INJECTION',injection:'first PUT answered503 in browser; all GET/retry/submit real',assessmentId:a.assessmentId,attemptId:attempt.attemptId,questionId:q.questionId}),contentType:'application/json'});
});

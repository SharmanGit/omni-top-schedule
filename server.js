const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const path = require('path');
const fs = require('fs');
const { parseOmniSchedule, pluralizeLessons } = require('./omniParser');

const app = express();
const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || '127.0.0.1';

const allowedOrigins = process.env.ALLOWED_ORIGINS
  ? process.env.ALLOWED_ORIGINS.split(',')
  : ['http://localhost', 'http://localhost:3000', 'capacitor://localhost'];

app.use(cors({
  origin: function (origin, callback) {
    if (!origin || allowedOrigins.includes(origin)) {
      callback(null, true);
    } else {
      callback(new Error('Not allowed by CORS'));
    }
  },
  credentials: true
}));
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc:  ["'self'"],
      styleSrc:   ["'self'", "'unsafe-inline'"],
      imgSrc:     ["'self'", "data:", "https://omni.top-academy.ru"],
      connectSrc: ["'self'"],
      fontSrc:    ["'self'"],
      objectSrc:  ["'none'"],
      frameAncestors: ["'none'"],
    }
  }
}));
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Teacher Profile helper: dynamically build profile from username or Omni dashboard
function deriveTeacherName(username) {
  if (!username) return 'Преподаватель';
  const clean = username.split('@')[0].replace(/[._]/g, ' ');
  return clean.split(' ')
    .filter(Boolean)
    .map(p => p.charAt(0).toUpperCase() + p.slice(1).toLowerCase())
    .join(' ') || 'Преподаватель';
}

function buildTeacherProfile(username, idLocalHash, parsedName = null) {
  const fullName = parsedName || deriveTeacherName(username);
  const parts = fullName.split(' ');
  return {
    id: idLocalHash || 0,
    firstName: parts[0] || fullName,
    lastName: parts.slice(1).join(' ') || '',
    name: fullName,
    role: "Преподаватель",
    email: username || "",
    avatar: "https://omni.top-academy.ru/img/avatarka.svg"
  };
}

let teacherProfile = buildTeacherProfile(null, null);

function formatDaysAgo(days) {
  const n = Math.abs(days);
  const n100 = n % 100;
  const n10 = n % 10;
  let word = 'дней';
  if (n100 < 11 || n100 > 19) {
    if (n10 === 1) word = 'день';
    else if (n10 >= 2 && n10 <= 4) word = 'дня';
  }
  return `${days} ${word}`;
}

// Teacher tasks from Omni TOP notDoneTask
const teacherTasks = {
  pendingReviewsCount: 31,
  newHomeworkToCheck: 2,
  hwWaitDays: 3,
  hwWaitText: "Самое старое сдано: 3 дня назад",
  reviewWaitDays: 6,
  reviewWaitText: "Ожидает: 6 дней • план до 07.11",
  summary: "31 студент в очереди ревью • 2 новых ДЗ на проверку"
};

// Active live Omni TOP user session
let activeOmniSession = {
  cookieStr: null,
  IdLocalHash: null,
  username: null,
  branches: []
};

// Multi-week cache file
const WEEKS_FILE = path.join(__dirname, 'data', 'teacher_schedule_weeks.json');
const ALL_BRANCHES_FILE = path.join(__dirname, 'data', 'teacher_schedule_all_branches.json');
const SESSION_FILE = path.join(__dirname, 'data', 'omni_session.json');

// Restore active session from disk if present
function loadPersistedSession() {
  if (fs.existsSync(SESSION_FILE)) {
    try {
      const data = JSON.parse(fs.readFileSync(SESSION_FILE, 'utf8'));
      if (data && data.cookieStr && data.IdLocalHash) {
        activeOmniSession = {
          ...data,
          branches: Array.isArray(data.branches) ? data.branches : []
        };
        if (data.username) {
          teacherProfile = buildTeacherProfile(data.username, data.IdLocalHash);
        }
        console.log(`[AUTH] Restored active Omni TOP live session for ${data.username} (${activeOmniSession.branches.length} branches)`);
      }
    } catch (e) {
      console.error('[AUTH] Failed to parse session file:', e);
    }
  }
}

function saveSession(session) {
  try {
    fs.writeFileSync(SESSION_FILE, JSON.stringify(session, null, 2), 'utf8');
    console.log(`[AUTH] Session saved to disk for ${session.username}`);
  } catch (e) {
    console.error('[AUTH] Failed to save session:', e);
  }
}

function clearSession() {
  activeOmniSession = { cookieStr: null, IdLocalHash: null, username: null, branches: [] };
  teacherProfile = buildTeacherProfile(null, null);
  if (fs.existsSync(SESSION_FILE)) {
    try {
      fs.unlinkSync(SESSION_FILE);
      console.log(`[AUTH] Session cleared from disk`);
    } catch (e) {}
  }
}

loadPersistedSession();

// Fetch LIVE schedule from Omni TOP using active session cookies across all branches
async function fetchLiveOmniSchedule(weekOffset = 0) {
  if (!activeOmniSession.cookieStr || !activeOmniSession.IdLocalHash) {
    return null;
  }

  const branchesToFetch = (activeOmniSession.branches && activeOmniSession.branches.length > 0)
    ? activeOmniSession.branches
    : [{ cityId: '95', cityName: 'Барнаул', cookieStr: activeOmniSession.cookieStr, IdLocalHash: activeOmniSession.IdLocalHash }];

  const branchPayloads = [];

  for (const b of branchesToFetch) {
    try {
      console.log(`[LIVE OMNI] Fetching live schedule for ${b.cityName} (${b.cityId}), week ${weekOffset}...`);
      const res = await fetch('https://omni.top-academy.ru/schedule/get-schedule', {
        method: 'POST',
        headers: {
          'content-type': 'application/json;charset=UTF-8',
          'id-local-hash': b.IdLocalHash,
          'cookie': b.cookieStr,
          'referer': 'https://omni.top-academy.ru/',
          'origin': 'https://omni.top-academy.ru',
          'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        },
        body: JSON.stringify({ week: weekOffset })
      });

      if (res.ok) {
        const text = await res.text();
        if (!text.startsWith('<!DOCTYPE')) {
          const json = JSON.parse(text);
          if (json && (json.body || json.schedule)) {
            branchPayloads.push({
              cityId: String(b.cityId),
              cityName: b.cityName,
              data: json
            });
          }
        }
      }
    } catch (err) {
      console.warn(`[LIVE OMNI] Failed to fetch schedule for ${b.cityName}:`, err.message);
    }
  }

  // Ensure all known branches are represented (augment any branch missing or returned empty)
  if (fs.existsSync(ALL_BRANCHES_FILE)) {
    try {
      const snap = JSON.parse(fs.readFileSync(ALL_BRANCHES_FILE, 'utf8'));
      if (Array.isArray(snap.branches)) {
        for (const snapBranch of snap.branches) {
          const existing = branchPayloads.find(b => String(b.cityId) === String(snapBranch.cityId));
          const count = existing ? Object.values(existing.data?.body || {}).reduce((acc, l) => acc + Object.keys(l).length, 0) : 0;
          if (!existing) {
            branchPayloads.push(snapBranch);
          } else if (count === 0 && snapBranch.data?.body && Object.keys(snapBranch.data.body).length > 0) {
            existing.data = snapBranch.data;
          }
        }
      }
    } catch (e) {
      console.warn('[LIVE OMNI] Branch augmentation error:', e.message);
    }
  }

  if (branchPayloads.length > 0) {
    console.log(`[LIVE OMNI] Successfully assembled live schedule across ${branchPayloads.length} branches!`);
    const multiPayload = {
      originalCityId: 95,
      totalBranches: branchPayloads.length,
      branches: branchPayloads
    };
    return parseOmniSchedule(multiPayload);
  }

  return null;
}

// Fetch LIVE tasks (homeworks & student reviews) from Omni TOP across all branches
async function fetchLiveOmniTasks() {
  if (!activeOmniSession.cookieStr || !activeOmniSession.IdLocalHash) {
    return null;
  }

  const branchesToFetch = (activeOmniSession.branches && activeOmniSession.branches.length > 0)
    ? activeOmniSession.branches
    : [{ cityId: '95', cityName: 'Барнаул', cookieStr: activeOmniSession.cookieStr, IdLocalHash: activeOmniSession.IdLocalHash }];

  let totalHw = 0;
  let totalReviews = 0;
  let minDeadlineHours = 999;
  let mergedStudents = [];
  const seenStudentIds = new Set();
  let maxFactDays = 0;
  let earliestVisitDate = null;
  let anySuccess = false;

  for (const b of branchesToFetch) {
    try {
      console.log(`[LIVE OMNI] Fetching live tasks for ${b.cityName} (${b.cityId})...`);
      const res = await fetch('https://omni.top-academy.ru/tasks/not-done-tasks', {
        method: 'POST',
        headers: {
          'content-type': 'application/json;charset=UTF-8',
          'id-local-hash': b.IdLocalHash,
          'cookie': b.cookieStr,
          'referer': 'https://omni.top-academy.ru/',
          'origin': 'https://omni.top-academy.ru',
          'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        },
        body: JSON.stringify({})
      });

      if (res.ok) {
        const text = await res.text();
        if (!text.startsWith('<!DOCTYPE')) {
          const json = JSON.parse(text);
          if (json) {
            anySuccess = true;
            if (json.dzStud) {
              totalHw += (json.dzStud.new_home_work || 0);
              if (json.dzStud.progress_bar) {
                const pb = json.dzStud.progress_bar;
                if (pb.factDays && pb.factDays > maxFactDays) maxFactDays = pb.factDays;
                const rem = Math.max(0, (pb.maxHours || 29) - (pb.factHours || 3));
                if (rem < minDeadlineHours) minDeadlineHours = rem;
              }
            }
            if (json.reviewsStud) {
              totalReviews += (json.reviewsStud.count_students || 0);
              if (Array.isArray(json.reviewsStud.students_list)) {
                json.reviewsStud.students_list.forEach(s => {
                  if (!seenStudentIds.has(s.id_stud)) {
                    seenStudentIds.add(s.id_stud);
                    if (s.date_vizit) {
                      if (!earliestVisitDate || s.date_vizit < earliestVisitDate) earliestVisitDate = s.date_vizit;
                    }
                    mergedStudents.push({
                      id: s.id_stud,
                      name: s.fio_stud,
                      group: s.name_tgroups,
                      subject: s.name_spec,
                      stream: s.name_streams,
                      dateVizit: s.date_vizit,
                      dueDate: s.due_date,
                      branch: b.cityName
                    });
                  }
                });
              }
            }
          }
        }
      }
    } catch (e) {
      console.warn(`[LIVE OMNI] Tasks error for ${b.cityName}:`, e.message);
    }
  }

  if (anySuccess) {
    if (minDeadlineHours === 999) minDeadlineHours = 26;
    if (maxFactDays === 0) maxFactDays = 3;
    let reviewDaysAgo = 6;
    if (earliestVisitDate) {
      const d1 = new Date(earliestVisitDate + 'T00:00:00');
      const d2 = new Date();
      reviewDaysAgo = Math.max(0, Math.floor((d2 - d1) / (24 * 3600 * 1000)));
    }
    console.log(`[LIVE OMNI] Live tasks: ${totalHw} HW (oldest: ${maxFactDays}d), ${totalReviews} reviews (oldest: ${reviewDaysAgo}d), ${mergedStudents.length} students`);
    return {
      pendingReviewsCount: totalReviews,
      newHomeworkToCheck: totalHw,
      hwWaitDays: maxFactDays,
      hwWaitText: `Самое старое сдано: ${formatDaysAgo(maxFactDays)} назад`,
      reviewWaitDays: reviewDaysAgo,
      reviewWaitText: `Ожидает: ${formatDaysAgo(reviewDaysAgo)} • план до 07.11`,
      deadlineHours: minDeadlineHours,
      deadlineText: `${minDeadlineHours} ч`,
      studentsList: mergedStudents,
      isLive: true
    };
  }
  return null;
}

function loadWeekSchedule(weekOffset = 0) {
  const wKey = String(weekOffset);
  if (fs.existsSync(WEEKS_FILE)) {
    try {
      const weeksData = JSON.parse(fs.readFileSync(WEEKS_FILE, 'utf8'));
      if (weeksData[wKey]) {
        return parseOmniSchedule(weeksData[wKey]);
      }
    } catch (e) {
      console.error('Error loading weeks data:', e);
    }
  }
  if (fs.existsSync(ALL_BRANCHES_FILE)) {
    try {
      const raw = JSON.parse(fs.readFileSync(ALL_BRANCHES_FILE, 'utf8'));
      return parseOmniSchedule(raw);
    } catch (e) {
      console.error('Error loading fallback schedule:', e);
    }
  }
  return { week: "26.10–01.11", totalLessons: 0, branches: [], days: [] };
}

// Helper: get ISO date string in LOCAL timezone (e.g. "2026-10-30"), avoiding UTC day shift
function getLocalDateIso(d = new Date()) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

// Helper: convert HH:MM to total minutes
function toMinutes(hhmm) {
  if (!hhmm) return 0;
  const [h, m] = hhmm.split(':').map(Number);
  return (h || 0) * 60 + (m || 0);
}

// Compute live state of lesson against current date & time
function computeLessonState(lesson, dayIsoDate, now) {
  if (!lesson.startTime || !lesson.endTime || !dayIsoDate) {
    return { status: 'planned', isCurrent: false, label: 'Запланировано' };
  }

  const lessonStart = new Date(`${dayIsoDate}T${lesson.startTime}:00`);
  const lessonEnd = new Date(`${dayIsoDate}T${lesson.endTime}:00`);

  if (now >= lessonStart && now < lessonEnd) {
    const elapsed = Math.floor((now - lessonStart) / 60000);
    const duration = Math.floor((lessonEnd - lessonStart) / 60000);
    const remaining = duration - elapsed;
    return {
      status: 'active',
      isCurrent: true,
      label: 'Идёт пара',
      remainingMinutes: remaining,
      progressPercent: Math.round((elapsed / duration) * 100)
    };
  } else if (now < lessonStart) {
    const startsInMinutes = Math.floor((lessonStart - now) / 60000);
    return {
      status: 'upcoming',
      isCurrent: false,
      label: 'Скоро',
      startsInMinutes,
      progressPercent: 0
    };
  } else {
    return {
      status: 'completed',
      isCurrent: false,
      label: 'Завершена',
      progressPercent: 100
    };
  }
}

// -------------------------------------------------------------
// REST API ENDPOINTS
// -------------------------------------------------------------

/**
 * 1. GET /api/teacher/schedule
 * Full week schedule for the teacher across all branches
 */
app.get('/api/teacher/schedule', async (req, res) => {
  const weekOffset = parseInt(req.query.week || '0', 10);
  const filterBranch = req.query.branch;

  // Try live Omni TOP schedule if logged in
  let scheduleData = await fetchLiveOmniSchedule(weekOffset);
  const isLive = !!scheduleData;

  // Fallback to local snapshot
  if (!scheduleData) {
    scheduleData = loadWeekSchedule(weekOffset);
  }
  const now = new Date();

  // 1. Compute status for all lessons
  const daysWithStatus = scheduleData.days.map(day => {
    let lessons = day.lessons.map(l => ({
      ...l,
      isoDate: day.isoDate,
      date: day.date,
      ...computeLessonState(l, day.isoDate, now)
    }));

    if (filterBranch && filterBranch !== 'all') {
      const allowed = filterBranch.split(',').map(s => s.trim());
      lessons = lessons.filter(l => allowed.includes(String(l.branchId)));
    }

    return {
      ...day,
      lessonsCountText: pluralizeLessons(lessons.length),
      lessons
    };
  });

  // 2. Determine strictly ONE single active or next upcoming lesson across the entire week
  let liveTargetLessonId = null;
  let liveTargetType = null; // 'active' | 'upcoming'

  if (weekOffset === 0) {
    // Check if any lesson is currently ongoing
    for (const day of daysWithStatus) {
      for (const l of day.lessons) {
        if (l.status === 'active') {
          liveTargetLessonId = l.id;
          liveTargetType = 'active';
          break;
        }
      }
      if (liveTargetLessonId) break;
    }

    // If none ongoing, find the very next upcoming lesson
    if (!liveTargetLessonId) {
      for (const day of daysWithStatus) {
        for (const l of day.lessons) {
          if (l.status === 'upcoming') {
            liveTargetLessonId = l.id;
            liveTargetType = 'upcoming';
            break;
          }
        }
        if (liveTargetLessonId) break;
      }
    }
  }

  // Tag the lessons with live badge flags
  daysWithStatus.forEach(day => {
    day.lessons.forEach(l => {
      l.isLiveActive = l.id === liveTargetLessonId && liveTargetType === 'active';
      l.isNextUpcoming = l.id === liveTargetLessonId && liveTargetType === 'upcoming';
    });
  });

  const totalFiltered = daysWithStatus.reduce((acc, d) => acc + d.lessons.length, 0);

  const todayIso = getLocalDateIso(now);

  // Live Tasks from Omni TOP
  let currentTasks = { ...teacherTasks };
  if (activeOmniSession.cookieStr && activeOmniSession.IdLocalHash) {
    const liveTasks = await fetchLiveOmniTasks();
    if (liveTasks) {
      currentTasks = { ...teacherTasks, ...liveTasks, isLive: true };
    }
  }

  const deadlineHours = currentTasks.deadlineHours || 26;
  if (!global.cachedDeadlineTarget || global.lastDeadlineHours !== deadlineHours) {
    global.lastDeadlineHours = deadlineHours;
    global.cachedDeadlineTarget = Date.now() + deadlineHours * 3600 * 1000;
  }
  const deadlineTargetTimestamp = global.cachedDeadlineTarget;

  res.json({
    teacher: teacherProfile,
    system: "Omni TOP",
    isLive: isLive,
    liveUser: activeOmniSession.username || null,
    todayIso: todayIso,
    weekOffset: weekOffset,
    week: scheduleData.week,
    totalLessons: scheduleData.totalLessons,
    totalLessonsText: pluralizeLessons(scheduleData.totalLessons),
    totalFilteredLessons: totalFiltered,
    totalFilteredText: pluralizeLessons(totalFiltered),
    branches: scheduleData.branches || [],
    tasks: {
      ...currentTasks,
      deadlineTargetTimestamp
    },
    days: daysWithStatus
  });
});

/**
 * 2. GET /api/teacher/tasks
 */
app.get('/api/teacher/tasks', async (req, res) => {
  const liveTasks = await fetchLiveOmniTasks();
  const tasks = liveTasks ? { ...teacherTasks, ...liveTasks, isLive: true } : { ...teacherTasks, isLive: false };
  res.json({
    teacher: teacherProfile,
    teacherTasks: tasks,
    reviewsPending: tasks.pendingReviewsCount,
    newHomeworks: tasks.newHomeworkToCheck,
    deadlineHours: tasks.deadlineHours,
    deadlineText: tasks.deadlineText,
    badgeText: `${tasks.newHomeworkToCheck} ДЗ • ${pluralizeLessons(tasks.pendingReviewsCount)}`
  });
});

/**
 * Helper: Real Omni TOP Authentication Handshake
 */
async function authenticateOmniUser(username, password, cityId = null) {
  try {
    // 1. Initial GET to fetch CSRF token and session cookies
    const initRes = await fetch('https://omni.top-academy.ru/login/index', {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
      }
    });
    const html = await initRes.text();
    const initCookies = initRes.headers.getSetCookie ? initRes.headers.getSetCookie() : [];
    const csrfMatch = html.match(/name="csrf-token" content="([^"]+)"/);
    const csrfToken = csrfMatch ? csrfMatch[1] : '';

    const cookieHeader = initCookies.map(c => c.split(';')[0]).join('; ');

    // 2. POST to /auth/login
    const loginRes = await fetch('https://omni.top-academy.ru/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-CSRF-Token': csrfToken,
        'Cookie': cookieHeader,
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        'X-Requested-With': 'XMLHttpRequest'
      },
      body: JSON.stringify({
        LoginForm: {
          username: username,
          password: password,
          id_city: cityId || null
        }
      })
    });

    const loginJson = await loginRes.json();
    const loginCookies = loginRes.headers.getSetCookie ? loginRes.headers.getSetCookie() : [];

    if (loginJson && loginJson.success === true) {
      const allCookies = [...initCookies, ...loginCookies];
      const authCookieMap = {};
      allCookies.forEach(c => {
        const [pair] = c.split(';');
        const [k, v] = pair.split('=');
        if (k && v) authCookieMap[k.trim()] = v.trim();
      });
      const finalCookieStr = Object.entries(authCookieMap).map(([k, v]) => `${k}=${v}`).join('; ');

      return {
        success: true,
        cookieStr: finalCookieStr,
        csrfToken: csrfToken,
        IdLocalHash: loginJson.IdLocalHash
      };
    } else {
      let rawError = '';
      if (loginJson && loginJson.error) {
        if (loginJson.error.password) rawError = loginJson.error.password[0];
        else if (loginJson.error.username) rawError = loginJson.error.username[0];
        else if (loginJson.error.id_city) rawError = loginJson.error.id_city[0];
        else rawError = JSON.stringify(loginJson.error);
      }

      // Localize to Russian
      let errorMsg = 'Неверная почта или пароль';
      const lower = String(rawError).toLowerCase();
      if (lower.includes('user not found') || lower.includes('пользователь не найден')) {
        errorMsg = 'Пользователь с такой почтой не найден';
      } else if (lower.includes('incorrect user name') || lower.includes('incorrect username') || lower.includes('incorrect password') || lower.includes('неверный пароль')) {
        errorMsg = 'Неверная почта или пароль';
      } else if (lower.includes('cannot be blank') || lower.includes('empty')) {
        errorMsg = 'Пожалуйста, заполните почту и пароль';
      } else if (rawError) {
        errorMsg = rawError;
      }

      return { success: false, error: errorMsg };
    }
  } catch (err) {
    console.error('Omni login error:', err);
    return { success: false, error: `Сетевая ошибка связи с Omni TOP: ${err.message}` };
  }
}

/**
 * Dynamically discover all accessible branches and teacher name from Omni TOP HTML dashboard
 */
async function discoverUserBranchesAndProfile(cookieStr, idLocalHash) {
  try {
    const res = await fetch('https://omni.top-academy.ru/', {
      headers: {
        'cookie': cookieStr,
        'id-local-hash': idLocalHash,
        'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
      }
    });
    if (!res.ok) return { branches: [], teacherName: null };
    const html = await res.text();
    let teacherName = null;
    const nameMatch = html.match(/class="[^"]*user-name[^"]*"[^>]*>([^<]+)</i) || html.match(/class="[^"]*fio[^"]*"[^>]*>([^<]+)</i);
    if (nameMatch) teacherName = nameMatch[1].trim();

    const menuMatch = html.match(/id="cityes"[^>]*>([\s\S]*?)<\/md-menu-content>/i);
    const searchHtml = menuMatch ? menuMatch[1] : html;
    const matches = Array.from(searchHtml.matchAll(/changeCity\((\d+)\)[^>]*>\s*([^<]+)\s*</g));
    const seen = new Set();
    const branches = [];
    for (const m of matches) {
      const cityId = String(m[1]);
      const cityName = m[2].trim();
      if (!seen.has(cityId)) {
        seen.add(cityId);
        branches.push({ cityId, cityName });
      }
    }
    return { branches, teacherName };
  } catch (err) {
    console.warn('[AUTH] Failed to dynamically discover branches:', err.message);
    return { branches: [], teacherName: null };
  }
}

async function discoverUserBranches(cookieStr, idLocalHash) {
  const result = await discoverUserBranchesAndProfile(cookieStr, idLocalHash);
  return result.branches;
}

/**
 * Switch branch context in Omni TOP and obtain dedicated signed city_id session cookie
 */
async function getBranchSessionCookie(baseCookieStr, idLocalHash, cityId) {
  try {
    const changeRes = await fetch(`https://omni.top-academy.ru/auth/change-city?city=${cityId}`, {
      method: 'GET',
      headers: {
        'cookie': baseCookieStr,
        'id-local-hash': idLocalHash,
        'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
      },
      redirect: 'manual'
    });

    const setCookies = changeRes.headers.getSetCookie ? changeRes.headers.getSetCookie() : [];
    const cookieMap = {};
    baseCookieStr.split(';').forEach(c => {
      const idx = c.indexOf('=');
      if (idx !== -1) cookieMap[c.slice(0, idx).trim()] = c.slice(idx + 1).trim();
    });
    setCookies.forEach(sc => {
      const pair = sc.split(';')[0];
      const idx = pair.indexOf('=');
      if (idx !== -1) cookieMap[pair.slice(0, idx).trim()] = pair.slice(idx + 1).trim();
    });
    return Object.entries(cookieMap).map(([k, v]) => `${k}=${v}`).join('; ');
  } catch (e) {
    console.warn(`[AUTH] Failed to get session cookie for city ${cityId}:`, e.message);
    return baseCookieStr;
  }
}

// Rate limit authentication attempts to mitigate brute-force
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 10,
  standardHeaders: true,
  legacyHeaders: false,
  message: { success: false, error: 'Слишком много попыток входа. Попробуйте позже.' }
});

/**
 * 3. POST /api/auth/login
 * Real authentication endpoint against Omni TOP with dynamic branch discovery
 */
app.post('/api/auth/login', authLimiter, async (req, res) => {
  const { username, password } = req.body || {};
  if (!username || !password) {
    return res.status(400).json({ success: false, error: 'Введите логин и пароль' });
  }

  console.log(`[AUTH] Attempting login for user: ${username}...`);
  // 1. Primary login handshake
  const authMain = await authenticateOmniUser(username, password);

  if (authMain.success) {
    console.log(`[AUTH] Login SUCCESS for ${username}! Dynamically discovering user branches from Omni TOP...`);
    
    // 2. Dynamically extract all available branches for this teacher from #cityes menu
    let { branches: discovered, teacherName } = await discoverUserBranchesAndProfile(authMain.cookieStr, authMain.IdLocalHash);
    if (!discovered || discovered.length === 0) {
      console.log('[AUTH] No branches menu found in HTML, falling back to known user branches');
      discovered = [
        { cityId: '95', cityName: 'Барнаул' },
        { cityId: '430', cityName: 'Барнаул Колледж' },
        { cityId: '607', cityName: 'Барнаул Школа' }
      ];
    } else {
      console.log(`[AUTH] Discovered ${discovered.length} branches:`, discovered.map(b => `${b.cityName} (${b.cityId})`).join(', '));
    }

    // 3. For each branch, switch context to obtain signed city_id cookie
    const branchSessions = [];
    for (const b of discovered) {
      const branchCookieStr = await getBranchSessionCookie(authMain.cookieStr, authMain.IdLocalHash, b.cityId);
      branchSessions.push({
        cityId: b.cityId,
        cityName: b.cityName,
        cookieStr: branchCookieStr,
        IdLocalHash: authMain.IdLocalHash
      });
    }

    activeOmniSession = {
      cookieStr: authMain.cookieStr,
      IdLocalHash: authMain.IdLocalHash,
      username: username,
      branches: branchSessions
    };
    teacherProfile = buildTeacherProfile(username, authMain.IdLocalHash, teacherName);
    saveSession(activeOmniSession);

    return res.json({
      success: true,
      teacher: teacherProfile,
      message: 'Успешная авторизация в Omni TOP'
    });
  } else {
    console.log(`[AUTH] Login FAILED for ${username}: ${authMain.error}`);
    return res.status(401).json({
      success: false,
      error: authMain.error
    });
  }
});

/**
 * 4. GET /api/auth/status
 */
app.get('/api/auth/status', (req, res) => {
  res.json({
    isAuthenticated: !!activeOmniSession.cookieStr,
    isLive: !!activeOmniSession.cookieStr,
    username: activeOmniSession.username || null
  });
});

/**
 * 5. POST /api/auth/logout
 */
app.post('/api/auth/logout', (req, res) => {
  clearSession();
  res.json({ success: true, message: 'Сессия сброшена' });
});


// Start Server
app.listen(PORT, HOST, () => {
  console.log(`====================================================`);
  console.log(`👨‍🏫 Omni TOP Multi-Branch Schedule App running at http://${HOST}:${PORT}`);
  console.log(`   - Schedule API: http://${HOST}:${PORT}/api/teacher/schedule`);
  console.log(`   - Tasks API: http://${HOST}:${PORT}/api/teacher/tasks`);
  console.log(`   - Real Auth API: http://${HOST}:${PORT}/api/auth/login`);
  console.log(`====================================================`);
});

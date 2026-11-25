/**
 * Omni TOP Native Client (Standalone - Runs 100% inside Android app without server.js)
 * Uses built-in CapacitorHttp plugin for direct CORS-free network requests.
 */

const OmniClient = (() => {
  const SESSION_KEY = 'omni_top_session';

  // Helper: native request via CapacitorHttp or standard fetch
  async function request(options) {
    const isCapacitor = typeof window !== 'undefined' && window.Capacitor && typeof window.Capacitor.nativePromise === 'function';

    if (isCapacitor) {
      try {
        const capOptions = {
          url: options.url,
          method: options.method || 'GET',
          headers: options.headers || {},
          data: options.data || options.body
        };
        const res = await window.Capacitor.nativePromise('CapacitorHttp', 'request', capOptions);
        return {
          ok: res.status >= 200 && res.status < 300,
          status: res.status,
          headers: res.headers || {},
          data: res.data,
          text: async () => typeof res.data === 'string' ? res.data : JSON.stringify(res.data),
          json: async () => typeof res.data === 'object' ? res.data : JSON.parse(res.data)
        };
      } catch (err) {
        console.warn('[OmniClient] CapacitorHttp request failed, trying fetch:', err);
      }
    }

    // Web / local dev fallback
    const fetchOptions = {
      method: options.method || 'GET',
      headers: options.headers || {}
    };
    if (options.data || options.body) {
      fetchOptions.body = typeof (options.data || options.body) === 'string'
        ? (options.data || options.body)
        : JSON.stringify(options.data || options.body);
    }
    return fetch(options.url, fetchOptions);
  }

  // Session persistence
  function getSession() {
    try {
      const raw = localStorage.getItem(SESSION_KEY);
      if (raw) return JSON.parse(raw);
    } catch (e) {}
    return { cookieStr: null, IdLocalHash: null, username: null, branches: [] };
  }

  function saveSession(session) {
    try {
      localStorage.setItem(SESSION_KEY, JSON.stringify(session));
      localStorage.setItem('omni_auth', 'true');
      if (session.username) localStorage.setItem('omni_user', session.username);
    } catch (e) {}
  }

  function clearSession() {
    try {
      localStorage.removeItem(SESSION_KEY);
      localStorage.setItem('omni_auth', 'false');
    } catch (e) {}
  }

  function isAuthenticated() {
    const s = getSession();
    return !!(s.cookieStr && s.IdLocalHash);
  }

  // Helper: get ISO date string in LOCAL timezone
  function getLocalDateIso(d = new Date()) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  // Helper: Russian grammar for days
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

  // Dynamic branch discovery from Omni TOP HTML dashboard
  async function discoverUserBranches(cookieStr, idLocalHash) {
    try {
      const res = await request({
        url: 'https://omni.top-academy.ru/',
        method: 'GET',
        headers: {
          'cookie': cookieStr,
          'id-local-hash': idLocalHash,
          'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        }
      });
      const html = await res.text();
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
      return branches;
    } catch (err) {
      console.warn('[OmniClient] Failed to discover branches:', err);
      return [];
    }
  }

  // Switch branch context and obtain signed city_id session cookie
  async function getBranchSessionCookie(baseCookieStr, idLocalHash, cityId) {
    try {
      const changeRes = await request({
        url: `https://omni.top-academy.ru/auth/change-city?city=${cityId}`,
        method: 'GET',
        headers: {
          'cookie': baseCookieStr,
          'id-local-hash': idLocalHash,
          'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        }
      });

      const setCookies = changeRes.headers['set-cookie'] || changeRes.headers['Set-Cookie'] || '';
      const cookieArray = Array.isArray(setCookies) ? setCookies : [setCookies];
      const cookieMap = {};
      baseCookieStr.split(';').forEach(c => {
        const idx = c.indexOf('=');
        if (idx !== -1) cookieMap[c.slice(0, idx).trim()] = c.slice(idx + 1).trim();
      });
      cookieArray.forEach(sc => {
        if (!sc) return;
        const pair = sc.split(';')[0];
        const idx = pair.indexOf('=');
        if (idx !== -1) cookieMap[pair.slice(0, idx).trim()] = pair.slice(idx + 1).trim();
      });
      return Object.entries(cookieMap).map(([k, v]) => `${k}=${v}`).join('; ');
    } catch (e) {
      console.warn(`[OmniClient] Failed to get session cookie for city ${cityId}:`, e);
      return baseCookieStr;
    }
  }

  // Direct login against Omni TOP
  async function login(username, password) {
    try {
      // 1. Initial GET to fetch CSRF token and initial cookies
      const initRes = await request({
        url: 'https://omni.top-academy.ru/login/index',
        method: 'GET',
        headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36' }
      });
      const html = await initRes.text();
      const csrfMatch = html.match(/name="csrf-token" content="([^"]+)"/);
      const csrfToken = csrfMatch ? csrfMatch[1] : '';

      const initCookies = initRes.headers['set-cookie'] || initRes.headers['Set-Cookie'] || '';
      const cookieArray = Array.isArray(initCookies) ? initCookies : [initCookies];
      const cookieHeader = cookieArray.map(c => c ? c.split(';')[0] : '').filter(Boolean).join('; ');

      // 2. POST to /auth/login
      const loginRes = await request({
        url: 'https://omni.top-academy.ru/auth/login',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-CSRF-Token': csrfToken,
          'Cookie': cookieHeader,
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
          'X-Requested-With': 'XMLHttpRequest'
        },
        data: {
          LoginForm: {
            username: username,
            password: password,
            id_city: null
          }
        }
      });

      const loginJson = await loginRes.json();
      const loginCookies = loginRes.headers['set-cookie'] || loginRes.headers['Set-Cookie'] || '';
      const allCookies = [...cookieArray, ...(Array.isArray(loginCookies) ? loginCookies : [loginCookies])];

      if (loginJson && loginJson.success === true) {
        const authCookieMap = {};
        allCookies.forEach(c => {
          if (!c) return;
          const [pair] = c.split(';');
          const [k, v] = pair.split('=');
          if (k && v) authCookieMap[k.trim()] = v.trim();
        });
        const finalCookieStr = Object.entries(authCookieMap).map(([k, v]) => `${k}=${v}`).join('; ');

        // 3. Dynamically discover user branches
        let discovered = await discoverUserBranches(finalCookieStr, loginJson.IdLocalHash);
        if (!discovered || discovered.length === 0) {
          discovered = [
            { cityId: '95', cityName: 'Барнаул' },
            { cityId: '430', cityName: 'Барнаул Колледж' },
            { cityId: '607', cityName: 'Барнаул Школа' }
          ];
        }

        // 4. Per-branch session cookies
        const branchSessions = [];
        for (const b of discovered) {
          const branchCookieStr = await getBranchSessionCookie(finalCookieStr, loginJson.IdLocalHash, b.cityId);
          branchSessions.push({
            cityId: b.cityId,
            cityName: b.cityName,
            cookieStr: branchCookieStr,
            IdLocalHash: loginJson.IdLocalHash
          });
        }

        const session = {
          cookieStr: finalCookieStr,
          IdLocalHash: loginJson.IdLocalHash,
          username: username,
          branches: branchSessions
        };
        saveSession(session);
        return { success: true };
      } else {
        let rawError = 'Неверная почта или пароль';
        if (loginJson && loginJson.error) {
          if (loginJson.error.password) rawError = loginJson.error.password[0];
          else if (loginJson.error.username) rawError = loginJson.error.username[0];
          else rawError = JSON.stringify(loginJson.error);
        }
        return { success: false, error: rawError };
      }
    } catch (e) {
      console.error('[OmniClient] Login error:', e);
      return { success: false, error: `Ошибка связи с Omni TOP: ${e.message}` };
    }
  }

  // Compute live state of lesson against current time in the branch's timezone
  function computeLessonState(lesson, dayIsoDate, now) {
    if (!lesson.startTime || !lesson.endTime || !dayIsoDate) {
      return { status: 'planned', isCurrent: false, label: 'Запланировано' };
    }
    const toBranch = window.OmniParser && typeof window.OmniParser.toBranchDate === 'function'
      ? window.OmniParser.toBranchDate
      : null;
    const lessonStart = (toBranch && toBranch(dayIsoDate, lesson.startTime, lesson.branchName)) || new Date(`${dayIsoDate}T${lesson.startTime}:00`);
    const lessonEnd = (toBranch && toBranch(dayIsoDate, lesson.endTime, lesson.branchName)) || new Date(`${dayIsoDate}T${lesson.endTime}:00`);

    if (now >= lessonStart && now < lessonEnd) {
      const elapsed = Math.floor((now - lessonStart) / 60000);
      const duration = Math.floor((lessonEnd - lessonStart) / 60000);
      return {
        status: 'active',
        isCurrent: true,
        label: 'Идёт пара',
        remainingMinutes: duration - elapsed,
        progressPercent: Math.round((elapsed / duration) * 100)
      };
    } else if (now < lessonStart) {
      return {
        status: 'upcoming',
        isCurrent: false,
        label: 'Скоро',
        startsInMinutes: Math.floor((lessonStart - now) / 60000),
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

  // Fetch full schedule (live or fallback)
  async function getSchedule(weekOffset = 0, filterBranch = null) {
    const session = getSession();
    let rawData = null;

    // Try live fetch across all branches
    if (session.cookieStr && session.IdLocalHash && Array.isArray(session.branches) && session.branches.length > 0) {
      const branchPayloads = [];
      for (const b of session.branches) {
        try {
          const res = await request({
            url: 'https://omni.top-academy.ru/schedule/get-schedule',
            method: 'POST',
            headers: {
              'content-type': 'application/json;charset=UTF-8',
              'id-local-hash': b.IdLocalHash,
              'cookie': b.cookieStr,
              'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
            },
            data: { week: weekOffset }
          });
          if (res.ok) {
            const text = await res.text();
            if (!text.startsWith('<!DOCTYPE')) {
              const json = typeof text === 'string' ? JSON.parse(text) : text;
              if (json && (json.body || json.schedule)) {
                branchPayloads.push({
                  cityId: String(b.cityId),
                  cityName: b.cityName,
                  data: json
                });
              }
            }
          }
        } catch (e) {
          console.warn(`[OmniClient] Schedule fetch failed for ${b.cityName}:`, e);
        }
      }

      // Augment any missing branches from snapshot so no branch is lost
      const snapshotWeek = window.OMNI_SNAPSHOT_WEEKS && (window.OMNI_SNAPSHOT_WEEKS[String(weekOffset)] || window.OMNI_SNAPSHOT_WEEKS['0']);
      if (snapshotWeek && Array.isArray(snapshotWeek.branches)) {
        for (const snapBranch of snapshotWeek.branches) {
          const existing = branchPayloads.find(b => String(b.cityId) === String(snapBranch.cityId));
          const count = existing ? Object.values(existing.data?.body || {}).reduce((acc, l) => acc + Object.keys(l).length, 0) : 0;
          if (!existing) {
            branchPayloads.push(snapBranch);
          } else if (count === 0 && snapBranch.data?.body && Object.keys(snapBranch.data.body).length > 0) {
            existing.data = snapBranch.data;
          }
        }
      }

      if (branchPayloads.length > 0) {
        rawData = {
          originalCityId: 95,
          totalBranches: branchPayloads.length,
          branches: branchPayloads
        };
      }
    }

    // Offline / demo fallback
    if (!rawData) {
      rawData = (window.OMNI_SNAPSHOT_WEEKS && (window.OMNI_SNAPSHOT_WEEKS[String(weekOffset)] || window.OMNI_SNAPSHOT_WEEKS['0'])) || { week: "26.10–01.11", totalLessons: 0, branches: [], days: [] };
    }

    const scheduleData = window.OmniParser.parseOmniSchedule(rawData);
    const now = new Date();
    const todayIso = getLocalDateIso(now);

    // Compute status for all lessons
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
        lessonsCountText: window.OmniParser.pluralizeLessons(lessons.length),
        lessons
      };
    });

    // Determine strictly ONE single active or next upcoming lesson
    let liveTargetLessonId = null;
    let liveTargetType = null;
    if (weekOffset === 0) {
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

    daysWithStatus.forEach(day => {
      day.lessons.forEach(l => {
        l.isLiveActive = l.id === liveTargetLessonId && liveTargetType === 'active';
        l.isNextUpcoming = l.id === liveTargetLessonId && liveTargetType === 'upcoming';
      });
    });

    const totalFiltered = daysWithStatus.reduce((acc, d) => acc + d.lessons.length, 0);

    // Tasks
    const tasks = await getTasks();

    return {
      teacher: {
        id: 226,
        firstName: "Андрей",
        lastName: "Винокуров",
        name: "Андрей Винокуров",
        email: session.username || "teacher@top-academy.ru"
      },
      todayIso,
      weekOffset,
      week: scheduleData.week,
      totalLessons: scheduleData.totalLessons,
      totalLessonsText: window.OmniParser.pluralizeLessons(scheduleData.totalLessons),
      totalFilteredLessons: totalFiltered,
      totalFilteredText: window.OmniParser.pluralizeLessons(totalFiltered),
      branches: scheduleData.branches || [],
      tasks,
      days: daysWithStatus
    };
  }

  // Fetch teacher tasks (HW and student reviews)
  async function getTasks() {
    const session = getSession();

    if (session.cookieStr && session.IdLocalHash && Array.isArray(session.branches) && session.branches.length > 0) {
      let totalHw = 0;
      let totalReviews = 0;
      let maxFactDays = 0;
      let earliestVisitDate = null;
      let mergedStudents = [];
      const seenStudentIds = new Set();
      let anySuccess = false;

      for (const b of session.branches) {
        try {
          const res = await request({
            url: 'https://omni.top-academy.ru/tasks/not-done-tasks',
            method: 'POST',
            headers: {
              'content-type': 'application/json;charset=UTF-8',
              'id-local-hash': b.IdLocalHash,
              'cookie': b.cookieStr,
              'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
            },
            data: {}
          });

          if (res.ok) {
            const text = await res.text();
            if (!text.startsWith('<!DOCTYPE')) {
              const json = typeof text === 'string' ? JSON.parse(text) : text;
              if (json) {
                anySuccess = true;
                if (json.dzStud) {
                  totalHw += (json.dzStud.new_home_work || 0);
                  if (json.dzStud.progress_bar && json.dzStud.progress_bar.factDays) {
                    if (json.dzStud.progress_bar.factDays > maxFactDays) maxFactDays = json.dzStud.progress_bar.factDays;
                  }
                }
                if (json.reviewsStud && Array.isArray(json.reviewsStud.students_list)) {
                  totalReviews += (json.reviewsStud.count_students || 0);
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
        } catch (e) {
          console.warn(`[OmniClient] Tasks fetch failed for ${b.cityName}:`, e);
        }
      }

      if (anySuccess) {
        if (maxFactDays === 0) maxFactDays = 3;
        let reviewDaysAgo = 6;
        if (earliestVisitDate) {
          const d1 = new Date(earliestVisitDate + 'T00:00:00');
          const d2 = new Date();
          reviewDaysAgo = Math.max(0, Math.floor((d2 - d1) / (24 * 3600 * 1000)));
        }
        mergedStudents.sort((a, b) => {
          const da = a.dueDate || a.dateVizit || '9999-99-99';
          const db = b.dueDate || b.dateVizit || '9999-99-99';
          if (da !== db) return da.localeCompare(db);
          return (a.name || '').localeCompare(b.name || '');
        });

        return {
          pendingReviewsCount: totalReviews,
          newHomeworkToCheck: totalHw,
          hwWaitDays: maxFactDays,
          hwWaitText: `Самое старое сдано: ${formatDaysAgo(maxFactDays)} назад`,
          reviewWaitDays: reviewDaysAgo,
          reviewWaitText: `Ожидает: ${formatDaysAgo(reviewDaysAgo)} • план до 07.11`,
          studentsList: mergedStudents,
          isLive: true
        };
      }
    }

    // Fallback to snapshot
    return (window.OMNI_SNAPSHOT_TASKS) || {
      pendingReviewsCount: 31,
      newHomeworkToCheck: 2,
      hwWaitDays: 3,
      hwWaitText: "Самое старое сдано: 3 дня назад",
      reviewWaitDays: 6,
      reviewWaitText: "Ожидает: 6 дней • план до 07.11",
      studentsList: [],
      isLive: false
    };
  }

  return {
    login,
    getSchedule,
    getTasks,
    getSession,
    saveSession,
    clearSession,
    isAuthenticated
  };
})();

if (typeof window !== 'undefined') {
  window.OmniClient = OmniClient;
}

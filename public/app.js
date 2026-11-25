// Omni TOP Teacher Schedule App - Frontend Logic & Theme/Auth Engine (100% Native Standalone)

const state = {
  currentWeekOffset: 0,
  selectedDayId: null, // Auto-selected to today if available
  selectedBranches: new Set(), // Set of branch IDs
  tempSelectedBranches: new Set(),
  theme: localStorage.getItem('omni_theme') || 'dark',
  isAuthenticated: localStorage.getItem('omni_auth') === 'true',
  data: null
};

// Helper: escape HTML entities to prevent XSS when rendering portal data
function escapeHtml(str) {
  if (str === null || str === undefined) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

// Helper: get local ISO date string "YYYY-MM-DD"
function getLocalIso(d = new Date()) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

// Curated high-contrast palette of 16 distinct accessible colors
const BRANCH_PALETTE = (window.OmniParser && window.OmniParser.BRANCH_PALETTE) || [
  '#3B82F6', '#10B981', '#F59E0B', '#8B5CF6', '#EC4899', '#06B6D4',
  '#F97316', '#14B8A6', '#6366F1', '#84CC16', '#E11D48', '#A855F7',
  '#0EA5E9', '#D97706', '#2DD4BF', '#EAB308'
];

function getBranchColor(branchId) {
  if (window.OmniParser && typeof window.OmniParser.getBranchColor === 'function') {
    return window.OmniParser.getBranchColor(branchId);
  }
  const str = String(branchId || '0');
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) - hash) + str.charCodeAt(i);
    hash |= 0;
  }
  const index = Math.abs(hash) % BRANCH_PALETTE.length;
  return BRANCH_PALETTE[index];
}

// -------------------------------------------------------------
// Theme Management Engine (Light & Dark Mode)
// -------------------------------------------------------------
function initTheme() {
  setTheme(state.theme, false);
}

function setTheme(newTheme, saveToStorage = true) {
  state.theme = newTheme;
  if (saveToStorage) {
    localStorage.setItem('omni_theme', newTheme);
  }
  document.documentElement.setAttribute('data-theme', newTheme);

  // Update controls inside Profile Modal
  const btnDark = document.getElementById('theme-btn-dark');
  const btnLight = document.getElementById('theme-btn-light');
  const label = document.getElementById('theme-current-label');
  const icon = document.getElementById('theme-current-icon');
  const loginIcon = document.getElementById('login-theme-icon');

  if (newTheme === 'light') {
    if (btnLight) btnLight.className = 'flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-semibold transition bg-blue-600 text-white shadow-sm cursor-pointer';
    if (btnDark) btnDark.className = 'flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-medium text-slate-500 theme-text-secondary hover:text-slate-800 transition cursor-pointer';
    if (label) label.textContent = 'Светлая тема активна';
    if (icon) icon.textContent = '☀️';
    if (loginIcon) loginIcon.textContent = '☀️';
  } else {
    if (btnDark) btnDark.className = 'flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-semibold transition bg-blue-600 text-white shadow-sm cursor-pointer';
    if (btnLight) btnLight.className = 'flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-medium text-slate-400 theme-text-secondary hover:text-white transition cursor-pointer';
    if (label) label.textContent = 'Тёмная тема активна';
    if (icon) icon.textContent = '🌙';
    if (loginIcon) loginIcon.textContent = '🌙';
  }

  renderDayRibbon();
  renderLessons();
}

function toggleTheme() {
  const nextTheme = state.theme === 'dark' ? 'light' : 'dark';
  setTheme(nextTheme);
}

// -------------------------------------------------------------
// Profile Menu & Authentication (Вход / Выход)
// -------------------------------------------------------------
function openProfileMenu() {
  const modal = document.getElementById('profile-modal');
  if (modal) modal.classList.remove('hidden');
  updateAuthUI();
}

function closeProfileMenu() {
  const modal = document.getElementById('profile-modal');
  if (modal) modal.classList.add('hidden');
}

function updateAuthUI() {
  const overlay = document.getElementById('login-overlay');
  const appHeader = document.getElementById('app-header');
  const appFooter = document.getElementById('app-footer');
  const authBtn = document.getElementById('auth-action-btn');
  const authBtnText = document.getElementById('auth-btn-text');

  const session = (window.OmniClient && typeof OmniClient.getSession === 'function') ? OmniClient.getSession() : null;
  const userEmail = (session && session.username) || localStorage.getItem('omni_user') || 'teacher@top-academy.ru';
  const userNameEl = document.getElementById('modal-user-name');
  const userEmailEl = document.getElementById('modal-user-email');

  if (state.isAuthenticated) {
    if (overlay) overlay.classList.add('hidden');
    if (appHeader) appHeader.classList.remove('hidden');
    if (appFooter) appFooter.classList.remove('hidden');
    if (authBtnText) authBtnText.textContent = 'Выйти из аккаунта';
    if (userEmailEl) userEmailEl.textContent = userEmail;
    if (userNameEl) userNameEl.textContent = (session && session.displayName) || 'Преподаватель';
  } else {
    if (overlay) overlay.classList.remove('hidden');
    if (appHeader) appHeader.classList.add('hidden');
    if (appFooter) appFooter.classList.add('hidden');
    if (authBtnText) authBtnText.textContent = 'Войти в аккаунт';
    if (userEmailEl) userEmailEl.textContent = 'teacher@top-academy.ru';
    if (userNameEl) userNameEl.textContent = 'Преподаватель';
  }
}

function handleAuthAction() {
  if (state.isAuthenticated) {
    logoutTeacher();
  } else {
    loginTeacher();
  }
}

async function logoutTeacher() {
  state.isAuthenticated = false;
  localStorage.setItem('omni_auth', 'false');
  localStorage.removeItem('omni_user');
  state.selectedBranches.clear();
  state.selectedDayId = null;
  if (window.OmniClient && typeof OmniClient.clearSession === 'function') {
    OmniClient.clearSession();
  }
  closeProfileMenu();
  updateAuthUI();
  await fetchSchedule();
}

async function loginTeacher() {
  const emailInput = document.getElementById('login-input-email');
  const passInput = document.getElementById('login-input-pass');
  const errorBox = document.getElementById('login-error-msg');
  const submitBtn = document.getElementById('login-submit-btn');
  const submitText = document.getElementById('login-submit-text');

  const username = emailInput ? emailInput.value.trim() : '';
  const password = passInput ? passInput.value.trim() : '';

  if (errorBox) {
    errorBox.textContent = '';
    errorBox.classList.add('hidden');
  }

  if (!username || !password) {
    if (errorBox) {
      errorBox.textContent = 'Пожалуйста, введите почту и пароль от Omni TOP';
      errorBox.classList.remove('hidden');
    }
    return;
  }

  if (submitBtn) submitBtn.disabled = true;
  if (submitText) submitText.textContent = 'Проверка в Omni TOP...';

  try {
    if (!window.OmniClient) {
      throw new Error('OmniClient не инициализирован');
    }

    const res = await OmniClient.login(username, password);

    if (res && res.success) {
      state.isAuthenticated = true;
      localStorage.setItem('omni_auth', 'true');
      localStorage.setItem('omni_user', username);
      closeProfileMenu();
      updateAuthUI();
      state.selectedBranches.clear();
      state.selectedDayId = null;
      await fetchSchedule();
    } else {
      if (errorBox) {
        errorBox.textContent = (res && res.error) || 'Неверная почта или пароль';
        errorBox.classList.remove('hidden');
      }
    }
  } catch (err) {
    if (errorBox) {
      errorBox.textContent = err.message || 'Ошибка связи с Omni TOP. Повторите попытку.';
      errorBox.classList.remove('hidden');
    }
  } finally {
    if (submitBtn) submitBtn.disabled = false;
    if (submitText) submitText.textContent = 'Войти в личный кабинет';
  }
}

// Russian grammar helper for lesson counts (1 пара, 2/3/4 пары, 5-20 пар, 22 пары)
function pluralizeLessons(count) {
  const n = Math.abs(count);
  const n100 = n % 100;
  const n10 = n % 10;
  if (n100 >= 11 && n100 <= 19) {
    return `${count} пар`;
  }
  if (n10 === 1) {
    return `${count} пара`;
  }
  if (n10 >= 2 && n10 <= 4) {
    return `${count} пары`;
  }
  return `${count} пар`;
}

// Russian grammar helper for reviews (1 отзыв, 2/3/4 отзыва, 5-20 отзывов, 31 отзыв)
function pluralizeReviews(count) {
  const n = Math.abs(count);
  const n100 = n % 100;
  const n10 = n % 10;
  if (n100 >= 11 && n100 <= 19) return `${count} отзывов`;
  if (n10 === 1) return `${count} отзыв`;
  if (n10 >= 2 && n10 <= 4) return `${count} отзыва`;
  return `${count} отзывов`;
}

// Real-time ticking deadline countdown
function updateDeadlineCountdown() {
  const headerDeadline = document.getElementById('tasks-header-deadline');
  const modalDeadline = document.getElementById('tasks-modal-deadline');
  
  if (!state.data || !state.data.tasks) return;

  const targetTime = state.data.tasks.deadlineTargetTimestamp || (Date.now() + 25 * 3600 * 1000 + 48 * 60 * 1000);
  const diffMs = targetTime - Date.now();

  if (diffMs <= 0) {
    if (headerDeadline) headerDeadline.textContent = 'дедлайн истёк';
    if (modalDeadline) modalDeadline.textContent = 'Осталось до дедлайна: истёк';
    return;
  }

  const hours = Math.floor(diffMs / 3600000);
  const minutes = Math.floor((diffMs % 3600000) / 60000);
  const timeText = `${hours} ч ${minutes} мин`;

  if (headerDeadline) headerDeadline.textContent = `осталось: ${timeText}`;
  if (modalDeadline) modalDeadline.textContent = `Осталось до дедлайна: ${timeText}`;
}

// Fetch multi-branch real schedule using OmniClient (standalone native)
async function fetchSchedule() {
  try {
    let filterBranch = null;
    if (state.data && state.data.branches && state.selectedBranches.size > 0 && state.selectedBranches.size < state.data.branches.length) {
      filterBranch = Array.from(state.selectedBranches).join(',');
    }

    if (window.OmniClient) {
      state.data = await OmniClient.getSchedule(state.currentWeekOffset, filterBranch);
    } else if (window.OMNI_SNAPSHOT_WEEKS) {
      state.data = window.OMNI_SNAPSHOT_WEEKS[String(state.currentWeekOffset)] || window.OMNI_SNAPSHOT_WEEKS['0'];
    }

    // Initialize selectedBranches on first load to all branches
    if (state.selectedBranches.size === 0 && state.data && state.data.branches) {
      state.data.branches.forEach(b => state.selectedBranches.add(b.id));
    }

    // Auto-select day:
    // Uses local date (Friday 30.10) if week offset is 0
    if (state.data && state.data.days) {
      const todayIso = state.data.todayIso || getLocalIso();
      const todayMatch = state.data.days.find(d => d.isoDate === todayIso);

      if (!state.selectedDayId) {
        if (state.currentWeekOffset === 0 && todayMatch) {
          state.selectedDayId = todayMatch.id;
        } else {
          const firstWithLessons = state.data.days.find(d => d.lessons.length > 0);
          state.selectedDayId = firstWithLessons ? firstWithLessons.id : 'fri';
        }
      } else {
        const curDay = state.data.days.find(d => d.id === state.selectedDayId);
        if (!curDay) {
          state.selectedDayId = state.data.days[0].id;
        }
      }
    }

    updateHeaderAndWeekControls();
    updateLocationLabels();
    updateDeadlineCountdown();
    renderDayRibbon();
    renderLessons();
  } catch (err) {
    console.error('Failed to load teacher schedule', err);
  }
}

// Update Header, Teacher Name, Week Controls & Grammar Badges
function updateHeaderAndWeekControls() {
  if (!state.data) return;

  const countEl = document.getElementById('tasks-header-count');
  if (countEl && state.data.tasks) {
    const reviewsCount = state.data.tasks.pendingReviewsCount || 31;
    countEl.textContent = `${state.data.tasks.newHomeworkToCheck || 2} ДЗ • ${pluralizeReviews(reviewsCount)}`;
  }

  const weekLabel = document.getElementById('week-range-label');
  if (weekLabel) {
    weekLabel.textContent = state.data.week || '26.10 – 01.11';
  }

  const weekStatus = document.getElementById('week-status-badge');
  if (weekStatus) {
    if (state.currentWeekOffset === 0) {
      weekStatus.textContent = 'Текущая';
      weekStatus.className = 'text-[9px] bg-blue-500/20 text-blue-300 border border-blue-500/30 px-1.5 py-0.2 rounded font-normal';
    } else if (state.currentWeekOffset > 0) {
      weekStatus.textContent = `+${state.currentWeekOffset} нед.`;
      weekStatus.className = 'text-[9px] bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 px-1.5 py-0.2 rounded font-normal';
    } else {
      weekStatus.textContent = `${state.currentWeekOffset} нед.`;
      weekStatus.className = 'text-[9px] bg-slate-700/60 text-slate-300 border border-slate-600 px-1.5 py-0.2 rounded font-normal';
    }
  }
}

// Week Navigation Handlers
async function changeWeek(delta) {
  state.currentWeekOffset += delta;
  state.selectedDayId = null;
  await fetchSchedule();
}

async function resetToCurrentWeek() {
  if (state.currentWeekOffset === 0) return;
  state.currentWeekOffset = 0;
  state.selectedDayId = null;
  await fetchSchedule();
}

// Format Location Label:
function updateLocationLabels() {
  if (!state.data || !state.data.branches) return;
  const branches = state.data.branches;
  
  const selectedBranchesList = branches.filter(b => state.selectedBranches.has(b.id));
  const isAll = selectedBranchesList.length === branches.length || state.selectedBranches.size === 0;

  let totalCount = 0;
  if (isAll) {
    totalCount = state.data.totalLessons || branches.reduce((sum, b) => sum + (b.lessonsCount || 0), 0);
  } else {
    totalCount = selectedBranchesList.reduce((sum, b) => sum + (b.lessonsCount || 0), 0);
  }

  let labelText = '';
  if (isAll) {
    labelText = `Все места (${totalCount})`;
  } else if (selectedBranchesList.length === 1) {
    labelText = `${selectedBranchesList[0].name} (${totalCount})`;
  } else if (selectedBranchesList.length === 2) {
    labelText = `${selectedBranchesList[0].name}, ${selectedBranchesList[1].name} (${totalCount})`;
  } else if (selectedBranchesList.length > 2) {
    labelText = `${selectedBranchesList[0].name} +${selectedBranchesList.length - 1} (${totalCount})`;
  } else {
    labelText = `Ничего не выбрано (0)`;
  }

  const textEl = document.getElementById('location-bar-text');
  if (textEl) textEl.textContent = labelText;
}

// -------------------------------------------------------------
// 2. Окно выбора источников (Sources Selection Modal)
// -------------------------------------------------------------
function openSourcesModal() {
  if (!state.data || !state.data.branches) return;
  
  state.tempSelectedBranches = new Set(state.selectedBranches);
  
  const searchInput = document.getElementById('sources-search-input');
  if (searchInput) searchInput.value = '';
  
  renderSourcesModalList();
  
  const modal = document.getElementById('sources-modal');
  if (modal) modal.classList.remove('hidden');
}

function closeSourcesModal() {
  const modal = document.getElementById('sources-modal');
  if (modal) modal.classList.add('hidden');
}

function renderSourcesModalList(filterQuery = '') {
  const container = document.getElementById('sources-modal-items');
  if (!container || !state.data || !state.data.branches) return;

  const q = filterQuery.toLowerCase().trim();
  const branches = state.data.branches.filter(b => !q || b.name.toLowerCase().includes(q));

  if (branches.length === 0) {
    container.innerHTML = `<div class="py-6 text-center text-xs text-slate-500 theme-text-secondary">Источники не найдены</div>`;
    return;
  }

  container.innerHTML = branches.map(b => {
    const isChecked = state.tempSelectedBranches.has(b.id);
    const branchColor = getBranchColor(b.id);
    const safeId = escapeHtml(b.id);
    return `
      <label class="flex items-center justify-between p-2 rounded-xl hover:bg-slate-800/60 theme-item-bg cursor-pointer select-none transition">
        <div class="flex items-center gap-2.5">
          <input type="checkbox" onchange="toggleTempBranch('${safeId}', this.checked)" ${isChecked ? 'checked' : ''} class="w-4 h-4 rounded bg-[#121c2e] theme-input-bg border-slate-700 theme-border text-blue-600 accent-blue-600 cursor-pointer">
          <span class="w-2.5 h-2.5 rounded-full shrink-0" style="background:${branchColor}"></span>
          <span class="text-xs font-medium text-slate-200 theme-text-primary">${escapeHtml(b.name)}</span>
        </div>
        <span class="text-xs text-slate-400 theme-text-secondary font-mono font-medium">${escapeHtml(b.lessonsCount)}</span>
      </label>
    `;
  }).join('');
}

function toggleTempBranch(branchId, checked) {
  if (checked) {
    state.tempSelectedBranches.add(branchId);
  } else {
    state.tempSelectedBranches.delete(branchId);
  }
}

function filterSourcesModalList() {
  const input = document.getElementById('sources-search-input');
  renderSourcesModalList(input ? input.value : '');
}

function resetSourcesSelection() {
  if (!state.data || !state.data.branches) return;
  state.data.branches.forEach(b => state.tempSelectedBranches.add(b.id));
  const input = document.getElementById('sources-search-input');
  renderSourcesModalList(input ? input.value : '');
}

async function applySourcesSelection() {
  state.selectedBranches = new Set(state.tempSelectedBranches);
  closeSourcesModal();
  await fetchSchedule();
}

// -------------------------------------------------------------
// Days Ribbon with Adaptive Theme Support (Contrast Optimized)
// -------------------------------------------------------------
function renderDayRibbon() {
  const container = document.getElementById('day-ribbon');
  if (!container || !state.data) return;

  const todayIso = state.data.todayIso || getLocalIso();
  const isLight = state.theme === 'light';

  const html = state.data.days.map(d => {
    const isSelected = d.id === state.selectedDayId;
    const isToday = (state.currentWeekOffset === 0 && d.isoDate === todayIso);
    const count = d.lessons.length;

    if (isToday) {
      // TODAY: Visibly larger (64x72 vs 46x58)
      let todayClasses = '';
      if (isSelected) {
        todayClasses = 'bg-blue-600 text-white border-2 border-amber-400 font-bold shadow-md';
      } else {
        todayClasses = isLight
          ? 'bg-white text-slate-800 hover:bg-slate-50 border-2 border-blue-500 shadow-sm'
          : 'bg-slate-900 text-slate-100 hover:bg-slate-800 border-2 border-amber-500';
      }

      const todayBadgeColor = isSelected
        ? 'text-amber-300'
        : (isLight ? 'text-blue-600' : 'text-amber-300');

      return `
        <button onclick="selectDay('${escapeHtml(d.id)}')" class="flex-1 min-w-[54px] max-w-[84px] flex flex-col items-center justify-between h-[72px] py-1.5 px-0.5 rounded-2xl transition-colors duration-150 relative cursor-pointer ${todayClasses}">
          <span class="text-3xs font-black uppercase tracking-wider ${todayBadgeColor}">Сегодня</span>
          <span class="text-xs uppercase font-extrabold ${isSelected ? 'text-white' : (isLight ? 'text-slate-900' : 'text-slate-100')} -mt-0.5">${escapeHtml(d.dayName)}</span>
          ${d.date ? `<span class="text-2xs font-mono opacity-80 -mt-1">${escapeHtml(d.date)}</span>` : ''}
          <div class="flex items-center gap-1">
            <span class="text-xs font-black">${count}</span>
            <span class="w-1.5 h-1.5 rounded-full ${isSelected ? 'bg-white' : (isLight ? 'bg-blue-600' : 'bg-amber-400')}"></span>
          </div>
        </button>
      `;
    }

    // Other days
    let regularClasses = '';
    if (isSelected) {
      regularClasses = 'bg-blue-600 text-white border border-blue-500 font-bold shadow-md';
    } else {
      regularClasses = isLight
        ? 'bg-white text-slate-700 hover:bg-slate-50 hover:text-slate-900 border border-slate-200 shadow-xs'
        : 'bg-slate-900/80 text-slate-400 hover:bg-slate-800 hover:text-slate-200 border border-slate-800';
    }

    return `
      <button onclick="selectDay('${escapeHtml(d.id)}')" class="flex-1 min-w-[42px] max-w-[70px] flex flex-col items-center justify-between h-[58px] py-1.5 px-0.5 rounded-2xl transition-colors duration-150 relative cursor-pointer ${regularClasses}">
        <span class="text-2xs uppercase font-semibold ${isSelected ? 'text-white' : (isLight ? 'text-slate-600' : 'text-slate-400')}">${escapeHtml(d.dayName)}</span>
        ${d.date ? `<span class="text-3xs font-mono opacity-70 -mt-1">${escapeHtml(d.date)}</span>` : ''}
        <div class="flex items-center gap-1">
          <span class="text-xs font-bold">${count}</span>
          ${count > 0 ? `<span class="w-1 h-1 rounded-full ${isSelected ? 'bg-white' : 'bg-blue-400'}"></span>` : ''}
        </div>
      </button>
    `;
  }).join('');

  container.innerHTML = html;
}

// -------------------------------------------------------------
// Render Lessons Stream with Theme Support
// -------------------------------------------------------------
function renderLessons() {
  const container = document.getElementById('lessons-list');
  if (!container || !state.data) return;

  const day = state.data.days.find(d => d.id === state.selectedDayId);
  if (!day) return;

  const isLight = state.theme === 'light';

  if (day.lessons.length === 0) {
    container.innerHTML = `
      <div class="py-20 text-center flex flex-col items-center justify-center text-slate-500 theme-text-secondary">
        <div class="w-14 h-14 rounded-2xl bg-slate-900/80 theme-item-bg border border-slate-800 theme-border flex items-center justify-center mb-3 text-slate-400">
          <svg class="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
        </div>
        <p class="text-sm font-semibold text-slate-300 theme-text-primary">В этот день пар нет</p>
        <p class="text-xs text-slate-500 theme-text-secondary mt-1">Свободно от преподавательских занятий</p>
      </div>
    `;
    return;
  }

  const cardsHtml = day.lessons.map(l => {
    let liveBadgeHtml = '';
    let cardClass = 'glass-panel rounded-2xl p-4 transition cursor-pointer';

    if (l.isLiveActive) {
      cardClass = isLight
        ? 'glass-panel border-2 border-emerald-500 rounded-2xl p-4 transition cursor-pointer shadow-sm'
        : 'glass-panel border border-emerald-500/80 rounded-2xl p-4 transition cursor-pointer';
      liveBadgeHtml = `
        <div class="absolute top-0 right-0 bg-emerald-500 text-slate-950 font-bold text-3xs uppercase tracking-wider px-2.5 py-0.5 rounded-bl-lg shadow-xs">
          Идёт пара
        </div>
      `;
    } else if (l.isNextUpcoming) {
      cardClass = isLight
        ? 'glass-panel border-2 border-amber-500 rounded-2xl p-4 transition cursor-pointer shadow-sm'
        : 'glass-panel border border-amber-500/80 rounded-2xl p-4 transition cursor-pointer';
      liveBadgeHtml = `
        <div class="absolute top-0 right-0 bg-amber-500 text-slate-950 font-bold text-3xs uppercase tracking-wider px-2.5 py-0.5 rounded-bl-lg shadow-xs">
          Следующая пара
        </div>
      `;
    }

    const branchColor = getBranchColor(l.branchId);

    return `
      <div class="${cardClass} relative overflow-hidden" onclick="openLessonModal('${escapeHtml(l.id)}')">
        ${liveBadgeHtml}

        <!-- Top row: Time & Pair Index -->
        <div class="flex items-center justify-between mb-2">
          <div class="flex items-center gap-2">
            <span class="text-xs font-semibold ${isLight ? 'text-slate-700' : 'text-slate-200'} flex items-center gap-1">
              <svg class="w-3.5 h-3.5 ${isLight ? 'text-slate-500' : 'text-slate-400'}" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
              ${escapeHtml(l.time)}
            </span>
            <span class="text-xs-plus ${isLight ? 'text-slate-500' : 'text-slate-500'}">• пара ${escapeHtml(l.pairIndex)}</span>
          </div>
        </div>

        <!-- Discipline Subject (Prominent & bold) -->
        <h3 class="text-lg font-bold ${isLight ? 'text-slate-900' : 'text-white'} tracking-tight mb-1">
          ${escapeHtml(l.subject)}
        </h3>

        <!-- Branch / Location Indicator with stable color dot -->
        <div class="text-xs-plus ${isLight ? 'text-slate-600' : 'text-slate-400'} flex items-center gap-1.5 mb-2.5">
          <span class="w-2 h-2 rounded-full shrink-0" style="background:${branchColor}"></span>
          <span class="font-medium ${isLight ? 'text-slate-800' : 'text-slate-300'}">${escapeHtml(l.branchLocation)}</span>
        </div>

        <!-- Bottom row: Group on Left, Classroom on Right -->
        <div class="flex items-center justify-between text-xs pt-2.5 border-t ${isLight ? 'border-slate-200' : 'border-slate-800/80'}">
          <span class="font-mono ${isLight ? 'text-slate-800' : 'text-slate-300'} text-xs font-semibold tracking-wide">
            ${escapeHtml(l.group)}
          </span>

          <span class="${isLight ? 'text-slate-700' : 'text-slate-300'} flex items-center gap-1.5 text-xs font-medium">
            <svg class="w-3.5 h-3.5 text-amber-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"/></svg>
            <span class="${isLight ? 'text-amber-600' : 'text-amber-300'} font-semibold">${escapeHtml(l.room)}</span>
          </span>
        </div>
      </div>
    `;
  }).join('');

  container.innerHTML = cardsHtml;
}

// Open Lesson Details Modal
function openLessonModal(id) {
  let found = null;
  for (const d of state.data.days) {
    const l = d.lessons.find(item => item.id === id);
    if (l) {
      found = { ...l, fullDay: d.fullDay, date: d.date };
      break;
    }
  }
  if (!found) return;

  const modal = document.getElementById('lesson-modal');
  const content = document.getElementById('lesson-modal-content');
  const branchColor = getBranchColor(found.branchId);

  content.innerHTML = `
    <div class="flex items-center justify-between pb-3 border-b border-slate-800 theme-border">
      <div>
        <div class="flex items-center gap-2">
          <span class="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded border border-slate-700 theme-border bg-slate-800 theme-item-bg text-slate-300 theme-text-primary">
            ${escapeHtml(found.typeName)}
          </span>
          <span class="text-[11px] text-slate-400 theme-text-secondary font-medium flex items-center gap-1.5">
            <span class="w-2 h-2 rounded-full shrink-0" style="background:${branchColor}"></span>
            <span>${escapeHtml(found.branchLocation)}</span>
          </span>
        </div>
        <h2 class="text-base font-bold text-white theme-text-primary mt-1.5">${escapeHtml(found.subject)}</h2>
      </div>
      <button onclick="closeLessonModal()" class="theme-close-btn" title="Закрыть">
        ✕
      </button>
    </div>

    <div class="py-3 space-y-2.5 text-xs">
      <div class="flex items-center justify-between bg-slate-900/60 theme-item-bg p-2.5 rounded-xl border border-slate-800 theme-border">
        <span class="text-slate-400 theme-text-secondary">День и время:</span>
        <span class="font-mono text-slate-200 theme-text-primary font-semibold">${escapeHtml(found.fullDay)} ${found.date ? `(${escapeHtml(found.date)})` : ''} • ${escapeHtml(found.time)}</span>
      </div>

      <div class="flex items-center justify-between bg-slate-900/60 theme-item-bg p-2.5 rounded-xl border border-slate-800 theme-border">
        <span class="text-slate-400 theme-text-secondary">Место / Филиал:</span>
        <span class="font-bold text-slate-200 theme-text-primary flex items-center gap-1.5">
          <span class="w-2 h-2 rounded-full shrink-0" style="background:${branchColor}"></span>
          <span>${escapeHtml(found.branchLocation)}</span>
        </span>
      </div>

      <div class="flex items-center justify-between bg-slate-900/60 theme-item-bg p-2.5 rounded-xl border border-slate-800 theme-border">
        <span class="text-slate-400 theme-text-secondary">Учебная группа:</span>
        <span class="font-bold text-slate-200 theme-text-primary font-mono text-sm">${escapeHtml(found.group)}</span>
      </div>

      <div class="flex items-center justify-between bg-slate-900/60 theme-item-bg p-2.5 rounded-xl border border-slate-800 theme-border">
        <span class="text-slate-400 theme-text-secondary">Аудитория:</span>
        <span class="text-amber-500 font-bold">${escapeHtml(found.room)}</span>
      </div>

      <div class="flex items-center justify-between bg-slate-900/60 theme-item-bg p-2.5 rounded-xl border border-slate-800 theme-border">
        <span class="text-slate-400 theme-text-secondary">Лента пары:</span>
        <span class="text-slate-200 theme-text-primary font-semibold">Пара №${escapeHtml(found.pairIndex)}</span>
      </div>

      <div class="pt-2">
        <button onclick="openOmniJournal('${escapeHtml(found.group)}')" class="w-full bg-blue-600 hover:bg-blue-500 text-white font-semibold py-2.5 rounded-xl transition flex items-center justify-center gap-1.5 cursor-pointer active:scale-95 shadow-md shadow-blue-600/30">
          <span>Открыть электронный журнал</span>
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"/></svg>
        </button>
        <p class="text-3xs text-slate-400 theme-text-secondary text-center mt-1.5">Откроется защищённая сессия Omni TOP в браузере</p>
      </div>
    </div>
  `;

  modal.classList.remove('hidden');
}

// Open Omni TOP Journal in browser / in-app Custom Tab
async function openOmniJournal(groupName = '') {
  const url = 'https://omni.top-academy.ru/journal/index';
  try {
    if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.Browser) {
      await window.Capacitor.Plugins.Browser.open({ url });
    } else {
      window.open(url, '_blank') || (window.location.href = url);
    }
  } catch (err) {
    console.error('Error opening journal in browser:', err);
    window.location.href = url;
  }
}

function closeLessonModal() {
  const modal = document.getElementById('lesson-modal');
  if (modal) modal.classList.add('hidden');
}

function openTasksModal() {
  const modal = document.getElementById('tasks-modal');
  if (!modal) return;

  const tasks = (state.data && state.data.tasks) ? state.data.tasks : null;
  const hwCountEl = document.getElementById('tasks-modal-hw-count');
  const reviewsCountEl = document.getElementById('tasks-modal-reviews-count');
  const hwAgeEl = document.getElementById('tasks-modal-hw-age');
  const reviewAgeEl = document.getElementById('tasks-modal-review-age');
  const listBox = document.getElementById('tasks-students-list-box');
  const container = document.getElementById('tasks-students-container');

  if (tasks) {
    if (hwCountEl) hwCountEl.textContent = tasks.newHomeworkToCheck != null ? tasks.newHomeworkToCheck : 0;
    if (reviewsCountEl) reviewsCountEl.textContent = tasks.pendingReviewsCount != null ? tasks.pendingReviewsCount : 0;
    if (hwAgeEl && tasks.hwWaitText) hwAgeEl.textContent = tasks.hwWaitText;
    if (reviewAgeEl && tasks.reviewWaitText) reviewAgeEl.textContent = tasks.reviewWaitText;

    if (tasks.studentsList && tasks.studentsList.length > 0 && container && listBox) {
      listBox.classList.remove('hidden');
      const sortedStudents = [...tasks.studentsList].sort((a, b) => {
        const da = a.dueDate || a.dateVizit || '9999-99-99';
        const db = b.dueDate || b.dateVizit || '9999-99-99';
        if (da !== db) return da.localeCompare(db);
        return (a.name || '').localeCompare(b.name || '');
      });
      container.innerHTML = sortedStudents.map((s, idx) => `
        <div class="bg-slate-800/80 theme-item-bg p-2 rounded-xl border border-slate-700/60 theme-border flex items-center justify-between text-2xs">
          <div class="truncate mr-2">
            <div class="font-bold text-slate-100 theme-text-primary truncate">${idx + 1}. ${escapeHtml(s.name)}</div>
            <div class="text-[10px] text-slate-400 theme-text-secondary truncate">${escapeHtml(s.group)} • ${escapeHtml(s.subject)}</div>
          </div>
          <span class="text-[9px] font-mono bg-blue-500/20 text-blue-300 border border-blue-500/30 px-1.5 py-0.5 rounded shrink-0">${s.dueDate ? escapeHtml(s.dueDate.split('-').slice(1).reverse().join('.')) : 'план'}</span>
        </div>
      `).join('');
    } else if (listBox) {
      listBox.classList.add('hidden');
    }
  }

  modal.classList.remove('hidden');
}

function closeTasksModal() {
  const modal = document.getElementById('tasks-modal');
  if (modal) modal.classList.add('hidden');
}

function selectDay(dayId) {
  state.selectedDayId = dayId;
  renderDayRibbon();
  renderLessons();
}

function initHorizontalScrollContainers() {
  const containers = [
    document.getElementById('day-ribbon')
  ].filter(Boolean);

  containers.forEach(container => {
    // 1. Mouse wheel horizontal scrolling
    container.addEventListener('wheel', (e) => {
      if (Math.abs(e.deltaY) > Math.abs(e.deltaX)) {
        e.preventDefault();
        container.scrollLeft += e.deltaY * 1.2;
      }
    }, { passive: false });

    // 2. Mouse drag-to-scroll
    let isDown = false;
    let startX = 0;
    let initialScrollLeft = 0;
    let hasMoved = false;

    container.addEventListener('mousedown', (e) => {
      isDown = true;
      hasMoved = false;
      startX = e.pageX - container.offsetLeft;
      initialScrollLeft = container.scrollLeft;
    });

    window.addEventListener('mouseup', () => {
      isDown = false;
    });

    container.addEventListener('mousemove', (e) => {
      if (!isDown) return;
      const x = e.pageX - container.offsetLeft;
      const walk = (x - startX) * 1.5;
      if (Math.abs(walk) > 5) {
        hasMoved = true;
      }
      container.scrollLeft = initialScrollLeft - walk;
    });

    // Prevent click on drag release if user was dragging
    container.addEventListener('click', (e) => {
      if (hasMoved) {
        e.preventDefault();
        e.stopPropagation();
      }
    }, true);
  });
}

function updateClock() {
  const clockEl = document.getElementById('phone-clock');
  if (clockEl) {
    const d = new Date();
    const h = String(d.getHours()).padStart(2, '0');
    const m = String(d.getMinutes()).padStart(2, '0');
    clockEl.textContent = `${h}:${m}`;
  }
}

window.addEventListener('DOMContentLoaded', async () => {
  const urlParams = new URLSearchParams(window.location.search);
  if (urlParams.get('logout') === '1') {
    state.isAuthenticated = false;
    localStorage.setItem('omni_auth', 'false');
  }

  initTheme();
  updateAuthUI();
  updateClock();
  setInterval(updateClock, 10000); // Ticks every 10 seconds for crisp deadline countdown
  initHorizontalScrollContainers();
  if (state.isAuthenticated) {
    await fetchSchedule();
  }

  // Smooth dismissal of the startup splash screen
  setTimeout(() => {
    const splash = document.getElementById('startup-splash');
    if (splash) {
      splash.style.opacity = '0';
      setTimeout(() => splash.remove(), 400);
    }
  }, 1100);
});

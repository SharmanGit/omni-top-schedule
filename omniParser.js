/**
 * Omni TOP Dynamic Schedule Normalizer & Multi-Branch Parser
 * Accurately parses schedules across all cities, colleges, schools and branches.
 */

(function(global) {
const WEEKDAY_NAMES = ["", "ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"];
const WEEKDAY_FULL = ["", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"];

/**
 * Pluralize in Russian (1 пара, 2/3/4 пары, 5-20 пар, 22 пары)
 */
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

// Curated high-contrast palette of 16 distinct accessible colors
const BRANCH_PALETTE = [
  '#3B82F6', // Blue
  '#10B981', // Emerald
  '#F59E0B', // Amber
  '#8B5CF6', // Purple
  '#EC4899', // Pink
  '#06B6D4', // Cyan
  '#F97316', // Orange
  '#14B8A6', // Teal
  '#6366F1', // Indigo
  '#84CC16', // Lime
  '#E11D48', // Rose
  '#A855F7', // Violet
  '#0EA5E9', // Sky
  '#D97706', // Ochre
  '#2DD4BF', // Turquoise
  '#EAB308'  // Yellow
];

/**
 * Pure deterministic hash function for any branchId (string or number).
 * Guarantees that a branch (e.g. 95) gets the EXACT same color across all teachers,
 * regardless of how many locations are in the account or their order.
 */
function getBranchColor(branchId) {
  const str = String(branchId || '0');
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) - hash) + str.charCodeAt(i);
    hash |= 0;
  }
  const index = Math.abs(hash) % BRANCH_PALETTE.length;
  return BRANCH_PALETTE[index];
}

/**
 * Dynamically classify location / branch type and color scheme
 */
function classifyBranch(branchName = '', item = {}) {
  const bLower = (branchName || '').toLowerCase();
  const spec = (item.name_spec || '').toLowerCase();
  const group = (item.groups || '').toLowerCase().trim();
  const idForm = String(item.id_form || '');

  const baseCity = branchName.replace(/\s+(колледж|школа|академия|онлайн)/gi, '').trim() || 'Барнаул';

  // 1. College (Колледж / СПО)
  if (bLower.includes('колледж') || spec.includes('колледж') || group.includes('гд') || group.includes('к-') || group.includes('9/4') || group.includes('11/') || idForm === '430' || idForm === '2') {
    return {
      type: 'college',
      badge: 'КОЛЛЕДЖ',
      branchIdSuffix: '-col',
      branchName: `${baseCity} Колледж`,
      color: '#10B981', // Emerald green
      accentBg: 'rgba(16, 185, 129, 0.15)',
      accentBorder: 'rgba(16, 185, 129, 0.35)'
    };
  }

  // 2. School / Школа
  if (bLower.includes('школа') || spec.includes('школа') || idForm === '607') {
    return {
      type: 'school',
      badge: 'ШКОЛА',
      branchIdSuffix: '-sch',
      branchName: `${baseCity} Школа`,
      color: '#F59E0B', // Amber
      accentBg: 'rgba(245, 158, 11, 0.15)',
      accentBorder: 'rgba(245, 158, 11, 0.35)'
    };
  }

  // 3. Online
  if (bLower.includes('онлайн') || spec.includes('онлайн') || (item.num_rooms && item.num_rooms.toLowerCase().includes('онлайн'))) {
    return {
      type: 'online',
      badge: 'ОНЛАЙН',
      branchIdSuffix: '-online',
      branchName: 'Онлайн',
      color: '#8B5CF6', // Purple
      accentBg: 'rgba(139, 92, 246, 0.15)',
      accentBorder: 'rgba(139, 92, 246, 0.35)'
    };
  }

  // 4. Default: Academy (Академия / МКА)
  return {
    type: 'academy',
    badge: 'АКАДЕМИЯ',
    branchIdSuffix: '',
    branchName: baseCity,
    color: '#3B82F6', // Blue
    accentBg: 'rgba(59, 130, 246, 0.15)',
    accentBorder: 'rgba(59, 130, 246, 0.35)'
  };
}

/**
 * Clean classroom format (e.g. "4 (коричневая дизайнерская)" -> "ауд. 4 (коричневая дизайнерская)")
 */
function formatRoom(rawRoom) {
  if (!rawRoom) return '—';
  const trimmed = rawRoom.trim();
  if (trimmed.startsWith('а') || trimmed.startsWith('А')) {
    return trimmed;
  }
  return `ауд. ${trimmed}`;
}

/**
 * Clean discipline / subject name
 */
function cleanSubjectName(rawName) {
  if (!rawName) return 'Занятие';
  return rawName.trim();
}

/**
 * Format ISO date "2026-10-26" -> "26.10"
 */
function formatShortDate(isoDate) {
  if (!isoDate || typeof isoDate !== 'string') return '';
  const parts = isoDate.split('-');
  if (parts.length === 3) {
    return `${parts[2]}.${parts[1]}`;
  }
  return isoDate;
}

/**
 * Parse single branch body: { lenta: { weekday: { ... } } }
 */
function parseSingleBranchBody(body, branchInfo, daysMap, seenLessonIds) {
  if (!body || typeof body !== 'object') return;

  Object.keys(body).forEach(lentaNum => {
    const lentaObj = body[lentaNum];
    if (!lentaObj || typeof lentaObj !== 'object') return;

    Object.keys(lentaObj).forEach(wdayNum => {
      const item = lentaObj[wdayNum];
      if (!item) return;

      const dayIndex = parseInt(wdayNum, 10);
      const dayItem = daysMap[dayIndex];
      if (!dayItem) return;

      const pairIndex = parseInt(item.lenta || lentaNum, 10);
      const timeStr = (item.l_start && item.l_end) ? `${item.l_start}–${item.l_end}` : '';
      const branch = classifyBranch(branchInfo.cityName, item);
      const isDynamic = branchInfo.isDynamic !== false;
      const effectiveBranchId = isDynamic
        ? `${branchInfo.cityId || '95'}${branch.branchIdSuffix}`
        : (branchInfo.cityId || '0');
      const effectiveBranchName = isDynamic
        ? branch.branchName
        : (branchInfo.cityName || branch.branchName);

      const lessonUid = `lesson-${effectiveBranchId}-${wdayNum}-${pairIndex}-${item.groups || ''}-${item.l_start || ''}`;
      if (seenLessonIds.has(lessonUid)) return;
      seenLessonIds.add(lessonUid);

      dayItem.lessons.push({
        id: lessonUid,
        pairIndex: pairIndex,
        startTime: item.l_start || '',
        endTime: item.l_end || '',
        time: timeStr,
        subject: cleanSubjectName(item.name_spec),
        group: (item.groups || '').trim(),
        room: formatRoom(item.num_rooms),
        rawRoom: item.num_rooms,
        branchId: effectiveBranchId,
        branchName: effectiveBranchName,
        branchLocation: effectiveBranchName,
        type: branch.type,
        typeName: branch.badge,
        badgeColor: getBranchColor(effectiveBranchId),
        accentBg: branch.accentBg,
        accentBorder: branch.accentBorder,
        scheduleType: item.scheduleType || 'planned',
        lessonsCount: item.lessons_count || ''
      });
    });
  });
}

/**
 * Universal Parser: handles both multi-branch JSON ({ branches: [...] }) and legacy single-branch JSON
 */
function parseOmniSchedule(rawData, fallbackWeekLabel = "26.10–01.11") {
  const daysMap = {
    1: { id: "mon", dayNum: 1, dayName: "ПН", fullDay: "Понедельник", date: "", isoDate: "", lessons: [] },
    2: { id: "tue", dayNum: 2, dayName: "ВТ", fullDay: "Вторник", date: "", isoDate: "", lessons: [] },
    3: { id: "wed", dayNum: 3, dayName: "СР", fullDay: "Среда", date: "", isoDate: "", lessons: [] },
    4: { id: "thu", dayNum: 4, dayName: "ЧТ", fullDay: "Четверг", date: "", isoDate: "", lessons: [] },
    5: { id: "fri", dayNum: 5, dayName: "ПТ", fullDay: "Пятница", date: "", isoDate: "", lessons: [] },
    6: { id: "sat", dayNum: 6, dayName: "СБ", fullDay: "Суббота", date: "", isoDate: "", lessons: [] },
    7: { id: "sun", dayNum: 7, dayName: "ВС", fullDay: "Воскресенье", date: "", isoDate: "", lessons: [] }
  };

  const seenLessonIds = new Set();
  const branchesMeta = [];
  let foundDates = null;

  if (rawData && Array.isArray(rawData.branches)) {
    // Multi-branch payload from Omni TOP
    for (const b of rawData.branches) {
      const cityId = String(b.cityId || '');
      const cityName = b.cityName || 'Филиал';
      const bData = b.data || {};
      const body = bData.body ? bData.body : (b.body || bData);
      
      if (!foundDates && bData.dates) {
        foundDates = bData.dates;
      }

      const beforeCount = seenLessonIds.size;
      parseSingleBranchBody(body, { cityId, cityName, isDynamic: false }, daysMap, seenLessonIds);
      const added = seenLessonIds.size - beforeCount;

      const classification = classifyBranch(cityName);
      branchesMeta.push({
        id: cityId,
        name: cityName,
        type: classification.type,
        badge: classification.badge,
        color: getBranchColor(cityId),
        lessonsCount: added
      });
    }
  } else {
    // Single body payload (e.g. from live POST /schedule/get-schedule)
    const body = (rawData && rawData.body) ? rawData.body : (rawData && rawData.schedule) ? rawData.schedule : rawData || {};
    if (rawData && rawData.dates) {
      foundDates = rawData.dates;
    }
    parseSingleBranchBody(body, { cityId: '95', cityName: 'Барнаул', isDynamic: true }, daysMap, seenLessonIds);

    // Dynamically collect all distinct branches that actually have lessons
    const dynamicBranchesMap = new Map();
    Object.values(daysMap).forEach(d => {
      d.lessons.forEach(l => {
        if (!dynamicBranchesMap.has(l.branchId)) {
          dynamicBranchesMap.set(l.branchId, {
            id: l.branchId,
            name: l.branchLocation,
            type: l.type,
            badge: l.typeName,
            color: l.badgeColor,
            lessonsCount: 0
          });
        }
        dynamicBranchesMap.get(l.branchId).lessonsCount++;
      });
    });

    if (dynamicBranchesMap.size > 0) {
      branchesMeta.push(...Array.from(dynamicBranchesMap.values()));
    } else {
      branchesMeta.push({
        id: '95',
        name: 'Барнаул',
        type: 'academy',
        badge: 'АКАДЕМИЯ',
        color: getBranchColor('95'),
        lessonsCount: 0
      });
    }
  }

  // Populate calendar dates into each day
  let computedWeekLabel = fallbackWeekLabel;
  if (foundDates) {
    for (let dayNum = 1; dayNum <= 7; dayNum++) {
      const iso = foundDates[String(dayNum)];
      if (iso) {
        daysMap[dayNum].isoDate = iso;
        daysMap[dayNum].date = formatShortDate(iso);
      }
    }
    if (foundDates["1"] && foundDates["7"]) {
      computedWeekLabel = `${formatShortDate(foundDates["1"])} – ${formatShortDate(foundDates["7"])}`;
    }
  }

  // Sort each day's lessons strictly chronologically
  const resultDays = Object.values(daysMap).map(day => {
    day.lessons.sort((a, b) => {
      if (a.startTime && b.startTime) {
        return a.startTime.localeCompare(b.startTime);
      }
      return a.pairIndex - b.pairIndex;
    });
    return day;
  });

  return {
    week: computedWeekLabel,
    totalLessons: resultDays.reduce((acc, d) => acc + d.lessons.length, 0),
    branches: branchesMeta,
    days: resultDays
  };
}

const OmniParser = {
  parseOmniSchedule,
  classifyBranch,
  formatRoom,
  pluralizeLessons,
  getBranchColor,
  formatShortDate,
  BRANCH_PALETTE
};

if (typeof module !== 'undefined' && module.exports) {
  module.exports = OmniParser;
}
global.OmniParser = OmniParser;
})(typeof window !== 'undefined' ? window : this);

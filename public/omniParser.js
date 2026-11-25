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

const CITY_TIMEZONE_ENTRIES = [
  ['калининград', 'Europe/Kaliningrad'],
  ['москва', 'Europe/Moscow'],
  ['санкт-петербург', 'Europe/Moscow'],
  ['петербург', 'Europe/Moscow'],
  ['ярославль', 'Europe/Moscow'],
  ['казань', 'Europe/Moscow'],
  ['нижний новгород', 'Europe/Moscow'],
  ['ростов', 'Europe/Moscow'],
  ['краснодар', 'Europe/Moscow'],
  ['воронеж', 'Europe/Moscow'],
  ['волгоград', 'Europe/Moscow'],
  ['сочи', 'Europe/Moscow'],
  ['тула', 'Europe/Moscow'],
  ['тверь', 'Europe/Moscow'],
  ['рязань', 'Europe/Moscow'],
  ['калуга', 'Europe/Moscow'],
  ['белгород', 'Europe/Moscow'],
  ['курск', 'Europe/Moscow'],
  ['липецк', 'Europe/Moscow'],
  ['ставрополь', 'Europe/Moscow'],
  ['смоленск', 'Europe/Moscow'],
  ['брянск', 'Europe/Moscow'],
  ['орёл', 'Europe/Moscow'],
  ['орел', 'Europe/Moscow'],
  ['владимир', 'Europe/Moscow'],
  ['иваново', 'Europe/Moscow'],
  ['кострома', 'Europe/Moscow'],
  ['тамбов', 'Europe/Moscow'],
  ['пенза', 'Europe/Moscow'],
  ['саранск', 'Europe/Moscow'],
  ['чебоксары', 'Europe/Moscow'],
  ['йошкар-ола', 'Europe/Moscow'],
  ['киров', 'Europe/Moscow'],
  ['архангельск', 'Europe/Moscow'],
  ['мурманск', 'Europe/Moscow'],
  ['петрозаводск', 'Europe/Moscow'],
  ['новгород', 'Europe/Moscow'],
  ['псков', 'Europe/Moscow'],
  ['вологда', 'Europe/Moscow'],
  ['сыктывкар', 'Europe/Moscow'],
  ['махачкала', 'Europe/Moscow'],
  ['грозный', 'Europe/Moscow'],
  ['нальчик', 'Europe/Moscow'],
  ['владикавказ', 'Europe/Moscow'],
  ['симферополь', 'Europe/Moscow'],
  ['севастополь', 'Europe/Moscow'],
  ['минск', 'Europe/Minsk'],
  ['самара', 'Europe/Samara'],
  ['тольятти', 'Europe/Samara'],
  ['саратов', 'Europe/Samara'],
  ['ижевск', 'Europe/Samara'],
  ['ульяновск', 'Europe/Samara'],
  ['астрахань', 'Europe/Samara'],
  ['екатеринбург', 'Asia/Yekaterinburg'],
  ['челябинск', 'Asia/Yekaterinburg'],
  ['уфа', 'Asia/Yekaterinburg'],
  ['пермь', 'Asia/Yekaterinburg'],
  ['тюмень', 'Asia/Yekaterinburg'],
  ['сургут', 'Asia/Yekaterinburg'],
  ['нижневартовск', 'Asia/Yekaterinburg'],
  ['нижний тагил', 'Asia/Yekaterinburg'],
  ['курган', 'Asia/Yekaterinburg'],
  ['оренбург', 'Asia/Yekaterinburg'],
  ['магнитогорск', 'Asia/Yekaterinburg'],
  ['орск', 'Asia/Yekaterinburg'],
  ['стерлитамак', 'Asia/Yekaterinburg'],
  ['омск', 'Asia/Omsk'],
  ['барнаул', 'Asia/Barnaul'],
  ['бийск', 'Asia/Barnaul'],
  ['рубцовск', 'Asia/Barnaul'],
  ['новосибирск', 'Asia/Novosibirsk'],
  ['томск', 'Asia/Tomsk'],
  ['кемерово', 'Asia/Novokuznetsk'],
  ['новокузнецк', 'Asia/Novokuznetsk'],
  ['красноярск', 'Asia/Krasnoyarsk'],
  ['абакан', 'Asia/Krasnoyarsk'],
  ['кызыл', 'Asia/Krasnoyarsk'],
  ['норильск', 'Asia/Krasnoyarsk'],
  ['иркутск', 'Asia/Irkutsk'],
  ['улан-удэ', 'Asia/Irkutsk'],
  ['братск', 'Asia/Irkutsk'],
  ['чита', 'Asia/Chita'],
  ['благовещенск', 'Asia/Yakutsk'],
  ['якутск', 'Asia/Yakutsk'],
  ['владивосток', 'Asia/Vladivostok'],
  ['хабаровск', 'Asia/Vladivostok'],
  ['уссурийск', 'Asia/Vladivostok'],
  ['комсомольск', 'Asia/Vladivostok'],
  ['сахалин', 'Asia/Sakhalin'],
  ['магадан', 'Asia/Magadan'],
  ['камчат', 'Asia/Kamchatka']
];

function getBranchTimeZone(branchName = '') {
  const lower = String(branchName || '').toLowerCase().trim();
  if (lower) {
    for (let i = 0; i < CITY_TIMEZONE_ENTRIES.length; i++) {
      if (lower.includes(CITY_TIMEZONE_ENTRIES[i][0])) {
        return CITY_TIMEZONE_ENTRIES[i][1];
      }
    }
  }
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

/**
 * Convert a branch-local YYYY-MM-DD + HH:MM into an exact UTC Date instant.
 */
function toBranchDate(isoDate, hhmm, branchName = '') {
  if (!isoDate || !hhmm) return null;
  const dateParts = String(isoDate).trim().split('-').map(Number);
  const timeParts = String(hhmm).trim().split(':').map(Number);
  const [y, m, d] = dateParts;
  const [hh, mm] = timeParts;
  if (!y || !m || !d || isNaN(hh) || isNaN(mm)) return null;

  const timeZone = getBranchTimeZone(branchName);
  const utcGuess = Date.UTC(y, m - 1, d, hh, mm, 0);
  try {
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone,
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
      hourCycle: 'h23'
    }).formatToParts(new Date(utcGuess));
    const map = {};
    parts.forEach(p => { map[p.type] = p.value; });
    const tzAsUtc = Date.UTC(
      Number(map.year), Number(map.month) - 1, Number(map.day),
      Number(map.hour), Number(map.minute), Number(map.second)
    );
    return new Date(utcGuess - (tzAsUtc - utcGuess));
  } catch (e) {
    return new Date(`${isoDate}T${hhmm}:00`);
  }
}

const OmniParser = {
  parseOmniSchedule,
  classifyBranch,
  formatRoom,
  pluralizeLessons,
  getBranchColor,
  formatShortDate,
  getBranchTimeZone,
  toBranchDate,
  BRANCH_PALETTE
};

if (typeof module !== 'undefined' && module.exports) {
  module.exports = OmniParser;
}
global.OmniParser = OmniParser;
})(typeof window !== 'undefined' ? window : this);

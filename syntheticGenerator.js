/**
 * Synthetic Schedule & Homework Content Generator
 * Generates realistic academic curriculums for college & academy across arbitrary weeks.
 */

// Disciplines catalogue with syllabus progression, teachers, and rooms
const CURRICULUM = {
  college: [
    {
      subject: "UI/UX дизайн",
      teacher: "Морозов Д. С.",
      room: "а.4",
      group: "9/4-ГД-23/72",
      icon: "🎨💻",
      topics: [
        "Исследование пользователей и CJM (Customer Journey Map)",
        "Информационная архитектура и вайрфрейминг мобильных систем",
        "Проектирование виджетов рабочего стола 4x2 и 2x2",
        "Юзабилити-тестирование прототипов и метрики SUS/CSAT",
        "Адаптивный дизайн под iOS и Android гайдлайны",
        "Компонентные UI-киты и токенизация дизайна",
        "Защита дизайн-проектов студенческих мобильных сервисов"
      ],
      homeworkPool: [
        "Спроектировать сетку экрана рабочего стола и макет виджета 4x2",
        "Собрать интерактивный кликабельный flow в Figma",
        "Составить карту эмпатии и прото-персоны для учебного портала",
        "Оформить UI Kit: цвета, типографика, состояния кнопок"
      ]
    },
    {
      subject: "HTML & CSS",
      teacher: "Ковалёва Е. И.",
      room: "а.4",
      group: "9/4-ГД-23/72",
      icon: "🌐",
      topics: [
        "Flexbox-контейнеры и вёрстка адаптивных навигационных баров",
        "CSS Grid Layout: сетки расписания и именованные области",
        "Медиа-запросы и responsive-типографика под мобильные экраны",
        "Анимация SVG: круговой прогресс-бар и пульсирующие рамки",
        "CSS Custom Properties и реализация темной темы оформления",
        "Семантическая разметка и доступность (WAI-ARIA)"
      ],
      homeworkPool: [
        "Сверстать карточку занятия с горизонтальным бейджем",
        "Реализовать адаптивную ленту дней недели на Flexbox",
        "Написать анимацию обратного отсчета на SVG stroke-dasharray",
        "Подготовить стили для тёмной темы расписания"
      ]
    }
  ],
  academy: [
    {
      subject: "Arduino",
      teacher: "Смирнов А. В.",
      room: "а.6",
      group: "1311",
      icon: "🤖",
      topics: [
        "Подключение аналоговых датчиков и ШИМ-сигналы",
        "Работа с сервоприводами SG90 и потенциометрами",
        "Ультразвуковой дальномер HC-SR04 и вывод в Serial",
        "Колесная платформа: двухмоторный привод и драйвер L298N",
        "Сенсоры линии и П-регулятор автономного движения",
        "Интеграция датчиков с LCD-дисплеем 1602 по I2C"
      ],
      homeworkPool: [
        "Написать скетч фильтрации дребезга кнопки",
        "Собрать схему дальномера и протестировать на макетке",
        "Запрограммировать П-регулятор движения по черной линии"
      ]
    },
    {
      subject: "Figma UX/UI",
      teacher: "Морозов Д. С.",
      room: "а.роболаборатория",
      group: "1511",
      icon: "✨",
      topics: [
        "Компоненты и варианты (Variants, Component Properties)",
        "Auto Layout 5.0 и адаптивная резинка интерфейсов",
        "Переменные (Variables), токены и режимы темы",
        "Интерактивная анимация Smart Animate и переходы экранов"
      ],
      homeworkPool: [
        "Собрать интерактивный переключатель табов с Smart Animate",
        "Создать компонентную карточку с динамическим бейджем"
      ]
    },
    {
      subject: "BBC Micro:bit",
      teacher: "Кузнецов И. П.",
      room: "а.2",
      group: "C2411",
      icon: "📟",
      topics: [
        "Встроенный акселерометр, компас и светодиодная матрица 5x5",
        "Радиоканал MakeCode: беспроводной обмен пакетами данных",
        "Bluetooth low energy телеметрия на смартфон",
        "Игровые механики на датчиках движения"
      ],
      homeworkPool: [
        "Реализовать шагомер с выводом счетчика на светодиодный экран",
        "Написать радио-рацию для передачи коротких текстовых сообщений"
      ]
    },
    {
      subject: "Инновации",
      teacher: "Федоров М. А.",
      room: "а.2",
      group: "B4411",
      icon: "🚀",
      topics: [
        "Lean Canvas: сегменты ЦА, ценностное предложение и каналы",
        "Unit-экономика: CAC, LTV, конверсии и когортный анализ",
        "CustDev-интервью и формулирование проблемных гипотез",
        "Подготовка питч-дека для Demo Day и презентация проекта"
      ],
      homeworkPool: [
        "Заполнить шаблон Lean Canvas для студенческого мобильного приложения",
        "Провести 3 проблемных интервью с одногруппниками и записать инсайты"
      ]
    }
  ]
};

// Slots template for standard weekly grid matching original desktop Omni TOP schedule
const WEEKLY_TEMPLATE = [
  { dayIndex: 0, dayName: "ПН", slots: [] },
  {
    dayIndex: 1,
    dayName: "ВТ",
    slots: [
      { time: "15:00–16:20", startTime: "15:00", endTime: "16:20", pairIndex: 1, category: "academy", disciplineIndex: 0 },
      { time: "16:30–17:50", startTime: "16:30", endTime: "17:50", pairIndex: 2, category: "academy", disciplineIndex: 0 }
    ]
  },
  {
    dayIndex: 2,
    dayName: "СР",
    slots: [
      { time: "09:40–11:10", startTime: "09:40", endTime: "11:10", pairIndex: 1, category: "college", disciplineIndex: 1 },
      { time: "11:50–13:20", startTime: "11:50", endTime: "13:20", pairIndex: 2, category: "college", disciplineIndex: 1 },
      { time: "15:00–16:20", startTime: "15:00", endTime: "16:20", pairIndex: 3, category: "academy", disciplineIndex: 1 },
      { time: "16:30–17:50", startTime: "16:30", endTime: "17:50", pairIndex: 4, category: "academy", disciplineIndex: 1 }
    ]
  },
  {
    dayIndex: 3,
    dayName: "ЧТ",
    slots: [
      { time: "09:40–11:10", startTime: "09:40", endTime: "11:10", pairIndex: 1, category: "college", disciplineIndex: 0 },
      { time: "11:50–13:20", startTime: "11:50", endTime: "13:20", pairIndex: 2, category: "college", disciplineIndex: 0 }
    ]
  },
  {
    dayIndex: 4,
    dayName: "ПТ",
    slots: [
      { time: "09:40–11:10", startTime: "09:40", endTime: "11:10", pairIndex: 1, category: "college", disciplineIndex: 0 },
      { time: "11:50–13:20", startTime: "11:50", endTime: "13:20", pairIndex: 2, category: "college", disciplineIndex: 0 },
      { time: "13:30–15:00", startTime: "13:30", endTime: "15:00", pairIndex: 3, category: "college", disciplineIndex: 0 },
      { time: "15:30–17:00", startTime: "15:30", endTime: "17:00", pairIndex: 4, category: "college", disciplineIndex: 0 }
    ]
  },
  {
    dayIndex: 5,
    dayName: "СБ",
    slots: [
      { time: "09:00–10:20", startTime: "09:00", endTime: "10:20", pairIndex: 1, category: "academy", disciplineIndex: 2 },
      { time: "10:30–11:50", startTime: "10:30", endTime: "11:50", pairIndex: 2, category: "academy", disciplineIndex: 2 },
      { time: "12:00–13:20", startTime: "12:00", endTime: "13:20", pairIndex: 3, category: "academy", disciplineIndex: 0 },
      { time: "13:30–14:50", startTime: "13:30", endTime: "14:50", pairIndex: 4, category: "academy", disciplineIndex: 0 },
      { time: "15:00–16:20", startTime: "15:00", endTime: "16:20", pairIndex: 5, category: "academy", disciplineIndex: 3 },
      { time: "16:30–17:50", startTime: "16:30", endTime: "17:50", pairIndex: 6, category: "academy", disciplineIndex: 3 }
    ]
  },
  {
    dayIndex: 6,
    dayName: "ВС",
    slots: [
      { time: "09:00–10:20", startTime: "09:00", endTime: "10:20", pairIndex: 1, category: "academy", disciplineIndex: 3 },
      { time: "10:30–11:50", startTime: "10:30", endTime: "11:50", pairIndex: 2, category: "academy", disciplineIndex: 3 },
      { time: "12:00–13:20", startTime: "12:00", endTime: "13:20", pairIndex: 3, category: "academy", disciplineIndex: 3 },
      { time: "13:30–14:50", startTime: "13:30", endTime: "14:50", pairIndex: 4, category: "academy", disciplineIndex: 3 }
    ]
  }
];

const DAY_IDS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"];
const DAY_FULL_NAMES = [
  "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
];

const MONTH_NAMES = [
  "января", "февраля", "марта", "апреля", "мая", "июня",
  "июля", "августа", "сентября", "октября", "ноября", "декабря"
];

// Helper: format DD.MM
function pad2(n) { return n < 10 ? '0' + n : '' + n; }

// Reference base Monday: 2026-10-26 (Week 9 in original screenshot: 26.10–01.11)
const BASE_MONDAY = new Date(2026, 9, 26); // Month 9 is October in JS Date

class SyntheticGenerator {
  constructor() {
    this.weeksCache = new Map();
    this.homeworks = [];
    this.initDefaultWeeks();
  }

  // Pre-generate initial 4 weeks ahead
  initDefaultWeeks() {
    for (let offset = 0; offset <= 4; offset++) {
      this.getOrGenerateWeek(offset);
    }
  }

  /**
   * Generates or retrieves schedule for a given week offset from base (0 = 26.10–01.11)
   */
  getOrGenerateWeek(weekOffset) {
    if (this.weeksCache.has(weekOffset)) {
      return this.weeksCache.get(weekOffset);
    }

    const weekNumber = 9 + weekOffset;
    const mondayDate = new Date(BASE_MONDAY);
    mondayDate.setDate(BASE_MONDAY.getDate() + weekOffset * 7);

    const sundayDate = new Date(mondayDate);
    sundayDate.setDate(mondayDate.getDate() + 6);

    const weekLabel = `${pad2(mondayDate.getDate())}.${pad2(mondayDate.getMonth() + 1)}–${pad2(sundayDate.getDate())}.${pad2(sundayDate.getMonth() + 1)}`;

    const days = WEEKLY_TEMPLATE.map((tmpl, idx) => {
      const curDate = new Date(mondayDate);
      curDate.setDate(mondayDate.getDate() + idx);
      const dayDateStr = `${pad2(curDate.getDate())}.${pad2(curDate.getMonth() + 1)}`;
      const fullDateStr = `${DAY_FULL_NAMES[idx]}, ${curDate.getDate()} ${MONTH_NAMES[curDate.getMonth()]}`;

      const lessons = tmpl.slots.map((slot, sIdx) => {
        const discList = CURRICULUM[slot.category];
        const disc = discList[slot.disciplineIndex % discList.length];
        
        // Advance topic based on week offset
        const topicIdx = (weekOffset * 2 + slot.pairIndex - 1) % disc.topics.length;
        const topic = disc.topics[topicIdx];

        const lessonId = `w${weekOffset}-${DAY_IDS[idx]}-${sIdx + 1}`;
        const hasHw = (slot.pairIndex === 1 || slot.pairIndex === 3) && (weekOffset >= 0);

        return {
          id: lessonId,
          pairIndex: slot.pairIndex,
          startTime: slot.startTime,
          endTime: slot.endTime,
          time: slot.time,
          subject: disc.subject,
          type: slot.category,
          typeName: slot.category === 'college' ? 'Колледж' : 'Академия',
          group: disc.group,
          room: disc.room,
          teacher: disc.teacher,
          topic: topic,
          icon: disc.icon,
          hasHomework: hasHw,
          weekOffset: weekOffset
        };
      });

      return {
        id: DAY_IDS[idx],
        dayName: tmpl.dayName,
        date: dayDateStr,
        fullDate: fullDateStr,
        lessons
      };
    });

    const weekData = {
      weekOffset,
      week: weekLabel,
      weekNumber,
      days
    };

    this.weeksCache.set(weekOffset, weekData);

    // Auto-synthesize associated homework for newly generated week
    this.synthesizeHomeworkForWeek(weekData);

    // Ensure week 0 has exactly 14 homework assignments matching the original UI badge 'ДЗ: 14'
    if (weekData.weekOffset === 0 && this.homeworks.length < 14) {
      const extraHw = [
        {
          id: "hw-base-1",
          lessonId: "w0-tue-1",
          weekOffset: 0,
          subject: "Arduino",
          type: "academy",
          title: "Скетч для ультразвукового датчика HC-SR04",
          deadline: "до 31.10",
          status: "pending",
          priority: "medium",
          description: "Измерение расстояния до препятствия и вывод в Serial Monitor с частотой 10 Гц.",
          room: "а.6",
          teacher: "Смирнов А. В."
        },
        {
          id: "hw-base-2",
          lessonId: "w0-wed-1",
          weekOffset: 0,
          subject: "HTML & CSS",
          type: "college",
          title: "Адаптивная верстка расписания на Flexbox & Grid",
          deadline: "до 01.11",
          status: "pending",
          priority: "high",
          description: "Обеспечить корректное отображение на мобильных экранах от 360px до 420px.",
          room: "а.4",
          teacher: "Ковалёва Е. И."
        },
        {
          id: "hw-base-3",
          lessonId: "w0-sat-5",
          weekOffset: 0,
          subject: "Инновации",
          type: "academy",
          title: "Заполнение Lean Canvas для стартапа студенческого сервиса",
          deadline: "до 03.11",
          status: "pending",
          priority: "low",
          description: "Описать сегменты целевой аудитории, проблему, уникальное ценностное предложение и каналы.",
          room: "а.2",
          teacher: "Федоров М. А."
        },
        {
          id: "hw-base-4",
          lessonId: "w0-thu-1",
          weekOffset: 0,
          subject: "UI/UX дизайн",
          type: "college",
          title: "Дизайн-система: сетки, типографика и токены цветов",
          deadline: "до 02.11",
          status: "pending",
          priority: "medium",
          description: "Вынести в компоненты стили карточек расписания и бейджей 'Колледж'/'Академия'.",
          room: "а.4",
          teacher: "Морозов Д. С."
        }
      ];

      extraHw.forEach(item => {
        if (!this.homeworks.some(h => h.id === item.id)) {
          this.homeworks.push(item);
        }
      });
    }

    return weekData;
  }

  /**
   * Generates realistic homeworks linked to this week's classes
   */
  synthesizeHomeworkForWeek(weekData) {
    weekData.days.forEach(day => {
      day.lessons.forEach(lesson => {
        if (!lesson.hasHomework) return;

        const hwId = `hw-${lesson.id}`;
        // Prevent duplicate creation
        if (this.homeworks.some(h => h.id === hwId)) return;

        const disc = [...CURRICULUM.college, ...CURRICULUM.academy].find(d => d.subject === lesson.subject);
        if (!disc) return;

        // Distinct, meaningful task directly linked to the lesson's syllabus topic
        this.homeworks.push({
          id: hwId,
          lessonId: lesson.id,
          weekOffset: weekData.weekOffset,
          subject: lesson.subject,
          type: lesson.type,
          title: `Практика: «${lesson.topic}»`,
          deadline: `до ${day.date}`,
          status: 'pending',
          priority: lesson.type === 'college' ? 'high' : 'medium',
          description: `Практическое задание по теме занятия. Оформить результаты и сдать преподавателю (${lesson.teacher}, ${lesson.room}).`,
          room: lesson.room,
          teacher: lesson.teacher
        });
      });
    });
  }

  getHomeworks() {
    return this.homeworks;
  }

  toggleHomework(id) {
    const item = this.homeworks.find(h => h.id === id);
    if (!item) return null;
    item.status = item.status === 'pending' ? 'completed' : 'pending';
    return item;
  }
}

module.exports = new SyntheticGenerator();

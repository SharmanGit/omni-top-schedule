import Foundation

enum BranchTimeZoneResolver {
    private static let cityZoneMap: [(String, String)] = [
        // UTC+2
        ("калининград", "Europe/Kaliningrad"),
        // UTC+3 (Moscow time)
        ("москва", "Europe/Moscow"),
        ("санкт-петербург", "Europe/Moscow"),
        ("петербург", "Europe/Moscow"),
        ("ярославль", "Europe/Moscow"),
        ("казань", "Europe/Moscow"),
        ("нижний новгород", "Europe/Moscow"),
        ("ростов", "Europe/Moscow"),
        ("краснодар", "Europe/Moscow"),
        ("воронеж", "Europe/Moscow"),
        ("волгоград", "Europe/Moscow"),
        ("сочи", "Europe/Moscow"),
        ("тула", "Europe/Moscow"),
        ("тверь", "Europe/Moscow"),
        ("рязань", "Europe/Moscow"),
        ("калуга", "Europe/Moscow"),
        ("белгород", "Europe/Moscow"),
        ("курск", "Europe/Moscow"),
        ("липецк", "Europe/Moscow"),
        ("ставрополь", "Europe/Moscow"),
        ("смоленск", "Europe/Moscow"),
        ("брянск", "Europe/Moscow"),
        ("орёл", "Europe/Moscow"),
        ("орел", "Europe/Moscow"),
        ("владимир", "Europe/Moscow"),
        ("иваново", "Europe/Moscow"),
        ("кострома", "Europe/Moscow"),
        ("тамбов", "Europe/Moscow"),
        ("пенза", "Europe/Moscow"),
        ("саранск", "Europe/Moscow"),
        ("чебоксары", "Europe/Moscow"),
        ("йошкар-ола", "Europe/Moscow"),
        ("киров", "Europe/Moscow"),
        ("архангельск", "Europe/Moscow"),
        ("мурманск", "Europe/Moscow"),
        ("петрозаводск", "Europe/Moscow"),
        ("новгород", "Europe/Moscow"),
        ("псков", "Europe/Moscow"),
        ("вологда", "Europe/Moscow"),
        ("сыктывкар", "Europe/Moscow"),
        ("махачкала", "Europe/Moscow"),
        ("грозный", "Europe/Moscow"),
        ("нальчик", "Europe/Moscow"),
        ("владикавказ", "Europe/Moscow"),
        ("симферополь", "Europe/Moscow"),
        ("севастополь", "Europe/Moscow"),
        ("минск", "Europe/Minsk"),
        // UTC+4
        ("самара", "Europe/Samara"),
        ("тольятти", "Europe/Samara"),
        ("саратов", "Europe/Samara"),
        ("ижевск", "Europe/Samara"),
        ("ульяновск", "Europe/Samara"),
        ("астрахань", "Europe/Samara"),
        // UTC+5
        ("екатеринбург", "Asia/Yekaterinburg"),
        ("челябинск", "Asia/Yekaterinburg"),
        ("уфа", "Asia/Yekaterinburg"),
        ("пермь", "Asia/Yekaterinburg"),
        ("тюмень", "Asia/Yekaterinburg"),
        ("сургут", "Asia/Yekaterinburg"),
        ("нижневартовск", "Asia/Yekaterinburg"),
        ("нижний тагил", "Asia/Yekaterinburg"),
        ("курган", "Asia/Yekaterinburg"),
        ("оренбург", "Asia/Yekaterinburg"),
        ("магнитогорск", "Asia/Yekaterinburg"),
        ("орск", "Asia/Yekaterinburg"),
        ("стерлитамак", "Asia/Yekaterinburg"),
        // UTC+6
        ("омск", "Asia/Omsk"),
        // UTC+7
        ("барнаул", "Asia/Barnaul"),
        ("бийск", "Asia/Barnaul"),
        ("рубцовск", "Asia/Barnaul"),
        ("новосибирск", "Asia/Novosibirsk"),
        ("томск", "Asia/Tomsk"),
        ("кемерово", "Asia/Novokuznetsk"),
        ("новокузнецк", "Asia/Novokuznetsk"),
        ("красноярск", "Asia/Krasnoyarsk"),
        ("абакан", "Asia/Krasnoyarsk"),
        ("кызыл", "Asia/Krasnoyarsk"),
        ("норильск", "Asia/Krasnoyarsk"),
        // UTC+8
        ("иркутск", "Asia/Irkutsk"),
        ("улан-удэ", "Asia/Irkutsk"),
        ("братск", "Asia/Irkutsk"),
        // UTC+9
        ("чита", "Asia/Chita"),
        ("благовещенск", "Asia/Yakutsk"),
        ("якутск", "Asia/Yakutsk"),
        // UTC+10
        ("владивосток", "Asia/Vladivostok"),
        ("хабаровск", "Asia/Vladivostok"),
        ("уссурийск", "Asia/Vladivostok"),
        ("комсомольск", "Asia/Vladivostok"),
        // UTC+11
        ("сахалин", "Asia/Sakhalin"),
        ("магадан", "Asia/Magadan"),
        // UTC+12
        ("камчат", "Asia/Kamchatka")
    ]

    static func resolveTimeZone(branchName: String) -> TimeZone {
        let lower = branchName.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        if !lower.isEmpty {
            for (cityKey, zoneIdentifier) in cityZoneMap where lower.contains(cityKey) {
                if let tz = TimeZone(identifier: zoneIdentifier) {
                    return tz
                }
            }
        }
        return TimeZone.current
    }

    static func lessonStartDate(_ lesson: Lesson) -> Date? {
        toDate(isoDate: lesson.date, hhmm: lesson.startTime, branchName: lesson.branchName)
    }

    static func lessonEndDate(_ lesson: Lesson) -> Date? {
        toDate(isoDate: lesson.date, hhmm: lesson.endTime, branchName: lesson.branchName)
    }

    static func toDate(isoDate: String, hhmm: String, branchName: String) -> Date? {
        let dateParts = isoDate.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: "-")
        let timeParts = hhmm.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: ":")
        guard dateParts.count == 3,
              timeParts.count >= 2,
              let year = Int(dateParts[0]),
              let month = Int(dateParts[1]),
              let day = Int(dateParts[2]),
              let hour = Int(timeParts[0]),
              let minute = Int(timeParts[1]) else {
            return nil
        }

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = resolveTimeZone(branchName: branchName)

        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        components.hour = hour
        components.minute = minute
        components.second = 0

        return calendar.date(from: components)
    }

    static func branchLocalTodayISO(branchName: String, now: Date = Date()) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = resolveTimeZone(branchName: branchName)
        let c = calendar.dateComponents([.year, .month, .day], from: now)
        guard let y = c.year, let m = c.month, let d = c.day else { return "" }
        return String(format: "%04d-%02d-%02d", y, m, d)
    }
}

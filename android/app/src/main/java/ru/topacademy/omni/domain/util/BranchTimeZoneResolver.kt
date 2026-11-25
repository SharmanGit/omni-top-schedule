package ru.topacademy.omni.domain.util

import ru.topacademy.omni.domain.model.Lesson
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Resolves the IANA [ZoneId] for an Omni TOP branch by its city/branch name.
 * Lesson times from the Omni API are always expressed in the branch's local time,
 * so multi-branch teachers need per-branch timezone conversion before comparing
 * against the current instant.
 */
object BranchTimeZoneResolver {

    private val CITY_ZONE_MAP: List<Pair<String, String>> = listOf(
        // UTC+2
        "калининград" to "Europe/Kaliningrad",
        // UTC+3 (Moscow time)
        "москва" to "Europe/Moscow",
        "санкт-петербург" to "Europe/Moscow",
        "петербург" to "Europe/Moscow",
        "ярославль" to "Europe/Moscow",
        "казань" to "Europe/Moscow",
        "нижний новгород" to "Europe/Moscow",
        "ростов" to "Europe/Moscow",
        "краснодар" to "Europe/Moscow",
        "воронеж" to "Europe/Moscow",
        "волгоград" to "Europe/Moscow",
        "сочи" to "Europe/Moscow",
        "тула" to "Europe/Moscow",
        "тверь" to "Europe/Moscow",
        "рязань" to "Europe/Moscow",
        "калуга" to "Europe/Moscow",
        "белгород" to "Europe/Moscow",
        "курск" to "Europe/Moscow",
        "липецк" to "Europe/Moscow",
        "ставрополь" to "Europe/Moscow",
        "смоленск" to "Europe/Moscow",
        "брянск" to "Europe/Moscow",
        "орёл" to "Europe/Moscow",
        "орел" to "Europe/Moscow",
        "владимир" to "Europe/Moscow",
        "иваново" to "Europe/Moscow",
        "кострома" to "Europe/Moscow",
        "тамбов" to "Europe/Moscow",
        "пенза" to "Europe/Moscow",
        "саранск" to "Europe/Moscow",
        "чебоксары" to "Europe/Moscow",
        "йошкар-ола" to "Europe/Moscow",
        "киров" to "Europe/Moscow",
        "архангельск" to "Europe/Moscow",
        "мурманск" to "Europe/Moscow",
        "петрозаводск" to "Europe/Moscow",
        "новгород" to "Europe/Moscow",
        "псков" to "Europe/Moscow",
        "вологда" to "Europe/Moscow",
        "сыктывкар" to "Europe/Moscow",
        "махачкала" to "Europe/Moscow",
        "грозный" to "Europe/Moscow",
        "нальчик" to "Europe/Moscow",
        "владикавказ" to "Europe/Moscow",
        "симферополь" to "Europe/Moscow",
        "севастополь" to "Europe/Moscow",
        "минск" to "Europe/Minsk",
        // UTC+4
        "самара" to "Europe/Samara",
        "тольятти" to "Europe/Samara",
        "саратов" to "Europe/Samara",
        "ижевск" to "Europe/Samara",
        "ульяновск" to "Europe/Samara",
        "астрахань" to "Europe/Samara",
        // UTC+5
        "екатеринбург" to "Asia/Yekaterinburg",
        "челябинск" to "Asia/Yekaterinburg",
        "уфа" to "Asia/Yekaterinburg",
        "пермь" to "Asia/Yekaterinburg",
        "тюмень" to "Asia/Yekaterinburg",
        "сургут" to "Asia/Yekaterinburg",
        "нижневартовск" to "Asia/Yekaterinburg",
        "нижний тагил" to "Asia/Yekaterinburg",
        "курган" to "Asia/Yekaterinburg",
        "оренбург" to "Asia/Yekaterinburg",
        "магнитогорск" to "Asia/Yekaterinburg",
        "орск" to "Asia/Yekaterinburg",
        "стерлитамак" to "Asia/Yekaterinburg",
        // UTC+6
        "омск" to "Asia/Omsk",
        // UTC+7
        "барнаул" to "Asia/Barnaul",
        "бийск" to "Asia/Barnaul",
        "рубцовск" to "Asia/Barnaul",
        "новосибирск" to "Asia/Novosibirsk",
        "томск" to "Asia/Tomsk",
        "кемерово" to "Asia/Novokuznetsk",
        "новокузнецк" to "Asia/Novokuznetsk",
        "красноярск" to "Asia/Krasnoyarsk",
        "абакан" to "Asia/Krasnoyarsk",
        "кызыл" to "Asia/Krasnoyarsk",
        "норильск" to "Asia/Krasnoyarsk",
        // UTC+8
        "иркутск" to "Asia/Irkutsk",
        "улан-удэ" to "Asia/Irkutsk",
        "братск" to "Asia/Irkutsk",
        // UTC+9
        "чита" to "Asia/Chita",
        "благовещенск" to "Asia/Yakutsk",
        "якутск" to "Asia/Yakutsk",
        // UTC+10
        "владивосток" to "Asia/Vladivostok",
        "хабаровск" to "Asia/Vladivostok",
        "уссурийск" to "Asia/Vladivostok",
        "комсомольск" to "Asia/Vladivostok",
        // UTC+11
        "сахалин" to "Asia/Sakhalin",
        "магадан" to "Asia/Magadan",
        // UTC+12
        "камчат" to "Asia/Kamchatka"
    )

    fun resolveZoneId(branchName: String): ZoneId {
        val lower = branchName.lowercase().trim()
        if (lower.isNotEmpty()) {
            for ((cityKey, zoneStr) in CITY_ZONE_MAP) {
                if (lower.contains(cityKey)) {
                    return try {
                        ZoneId.of(zoneStr)
                    } catch (_: Exception) {
                        ZoneId.systemDefault()
                    }
                }
            }
        }
        return ZoneId.systemDefault()
    }

    fun lessonStartInstant(lesson: Lesson): Instant? =
        toInstant(lesson.date, lesson.startTime, lesson.branchName)

    fun lessonEndInstant(lesson: Lesson): Instant? =
        toInstant(lesson.date, lesson.endTime, lesson.branchName)

    fun toInstant(isoDate: String, hhmm: String, branchName: String): Instant? {
        val date = try {
            LocalDate.parse(isoDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            return null
        }
        val time = parseTime(hhmm) ?: return null
        val zone = resolveZoneId(branchName)
        return LocalDateTime.of(date, time).atZone(zone).toInstant()
    }

    fun parseTime(timeStr: String): LocalTime? {
        return try {
            val parts = timeStr.trim().split(":")
            if (parts.size >= 2) {
                LocalTime.of(parts[0].toInt(), parts[1].toInt())
            } else null
        } catch (_: Exception) {
            null
        }
    }
}

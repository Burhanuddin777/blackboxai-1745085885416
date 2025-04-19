package com.example.attendanceapp.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class AcademicEvent(val date: LocalDate, val isHoliday: Boolean)

data class MonthlyAttendance(
    val subject: String,
    val month: String,
    val attendedLectures: Int,
    val totalLectures: Int
)

object AttendanceUtils {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /**
     * Parses academic calendar text extracted from PDF to a list of AcademicEvent.
     * Assumes the text contains lines with date and event type (e.g., holiday).
     */
    fun parseAcademicCalendar(pdfText: String): List<AcademicEvent> {
        val events = mutableListOf<AcademicEvent>()
        val lines = pdfText.lines()
        for (line in lines) {
            // Example line format: "15/09/2023 Holiday"
            val parts = line.trim().split(" ")
            if (parts.size >= 2) {
                val dateStr = parts[0]
                val eventType = parts[1].toLowerCase()
                try {
                    val date = LocalDate.parse(dateStr, dateFormatter)
                    val isHoliday = eventType.contains("holiday")
                    events.add(AcademicEvent(date, isHoliday))
                } catch (e: Exception) {
                    // Ignore lines that don't parse correctly
                }
            }
        }
        return events
    }

    /**
     * Calculates monthly attendance per subject based on timetable and academic calendar.
     * @param timetable Map of day of week to list of subjects.
     * @param academicEvents List of academic events (holidays).
     * @param startDate Start date of the academic period.
     * @param endDate End date of the academic period.
     * @param attendanceRecords Map of subject to attended lecture dates.
     * @return List of MonthlyAttendance per subject.
     */
    fun calculateMonthlyAttendance(
        timetable: Map<String, List<String>>,
        academicEvents: List<AcademicEvent>,
        startDate: LocalDate,
        endDate: LocalDate,
        attendanceRecords: Map<String, List<LocalDate>>
    ): List<MonthlyAttendance> {
        val holidays = academicEvents.filter { it.isHoliday }.map { it.date }.toSet()
        val monthlyAttendanceMap = mutableMapOf<String, MutableMap<String, Pair<Int, Int>>>()
        // month -> subject -> (attended, total)

        var date = startDate
        while (!date.isAfter(endDate)) {
            val dayOfWeek = date.dayOfWeek.name.capitalize()
            val subjects = timetable[dayOfWeek] ?: emptyList()
            val isHoliday = holidays.contains(date)
            if (!isHoliday) {
                val month = date.month.name.capitalize()
                for (subject in subjects) {
                    val attendedDates = attendanceRecords[subject] ?: emptyList()
                    val attended = if (attendedDates.contains(date)) 1 else 0
                    val monthMap = monthlyAttendanceMap.getOrPut(subject) { mutableMapOf() }
                    val current = monthMap.getOrDefault(month, Pair(0, 0))
                    monthMap[month] = Pair(current.first + attended, current.second + 1)
                }
            }
            date = date.plusDays(1)
        }

        val result = mutableListOf<MonthlyAttendance>()
        for ((subject, monthMap) in monthlyAttendanceMap) {
            for ((month, counts) in monthMap) {
                result.add(
                    MonthlyAttendance(
                        subject = subject,
                        month = month,
                        attendedLectures = counts.first,
                        totalLectures = counts.second
                    )
                )
            }
        }
        return result
    }
}

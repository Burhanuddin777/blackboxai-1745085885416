package com.example.attendanceapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.attendanceapp.ui.theme.AttendanceAppTheme
import android.content.Context
import android.util.Log
import com.github.barteksc.pdfviewer.PDFView
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Context
import com.example.attendanceapp.ui.theme.AttendanceAppTheme
import com.example.attendanceapp.utils.AttendanceUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AttendanceAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AttendanceApp()
                }
            }
        }
    }
}

data class Lecture(val subject: String, val count: Int)

@Composable
fun AttendanceApp() {
    val context = LocalContext.current
    var minAttendance by remember { mutableStateOf(75) }
    var currentAttendance by remember { mutableStateOf(80) } // Placeholder for demo
    var showWarning by remember { mutableStateOf(false) }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfText by remember { mutableStateOf("") }
    var timetable by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) } // day -> list of subjects
    var monthlyAttendance by remember { mutableStateOf<List<AttendanceUtils.MonthlyAttendance>>(emptyList()) }

    LaunchedEffect(currentAttendance, minAttendance) {
        showWarning = currentAttendance < minAttendance
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pdfUri = it
            pdfText = readPdfText(context, it)
            val academicEvents = AttendanceUtils.parseAcademicCalendar(pdfText)
            // For demo, assume academic period is current month
            val startDate = LocalDate.now().withDayOfMonth(1)
            val endDate = LocalDate.now().withDayOfMonth(startDate.lengthOfMonth())
            // Placeholder attendance records: assume attended all lectures
            val attendanceRecords = mutableMapOf<String, List<LocalDate>>()
            timetable.values.flatten().distinct().forEach { subject ->
                attendanceRecords[subject] = generateSequence(startDate) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(endDate) }
                    .filter { date -> timetable[date.dayOfWeek.name.capitalize()]?.contains(subject) == true }
                    .toList()
            }
            monthlyAttendance = AttendanceUtils.calculateMonthlyAttendance(
                timetable,
                academicEvents,
                startDate,
                endDate,
                attendanceRecords
            )
            currentAttendance = if (monthlyAttendance.isNotEmpty()) {
                val totalAttended = monthlyAttendance.sumOf { it.attendedLectures }
                val totalLectures = monthlyAttendance.sumOf { it.totalLectures }
                if (totalLectures > 0) (totalAttended * 100) / totalLectures else 0
            } else 0
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Attendance Dashboard", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { pdfPickerLauncher.launch("application/pdf") }) {
            Text("Upload Academic Calendar PDF")
        }
        pdfUri?.let {
            Text("PDF Selected: ${it.lastPathSegment}", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("Set Minimum Attendance (%)")
        OutlinedTextField(
            value = minAttendance.toString(),
            onValueChange = {
                val value = it.toIntOrNull()
                if (value != null && value in 0..100) {
                    minAttendance = value
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("Current Attendance: $currentAttendance%")
        if (showWarning) {
            WarningMessage()
        }

        Spacer(modifier = Modifier.height(24.dp))

        TimetableInput(timetable) { newTimetable ->
            timetable = newTimetable
            // Recalculate monthly attendance when timetable changes
            val academicEvents = AttendanceUtils.parseAcademicCalendar(pdfText)
            val startDate = LocalDate.now().withDayOfMonth(1)
            val endDate = LocalDate.now().withDayOfMonth(startDate.lengthOfMonth())
            val attendanceRecords = mutableMapOf<String, List<LocalDate>>()
            timetable.values.flatten().distinct().forEach { subject ->
                attendanceRecords[subject] = generateSequence(startDate) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(endDate) }
                    .filter { date -> timetable[date.dayOfWeek.name.capitalize()]?.contains(subject) == true }
                    .toList()
            }
            monthlyAttendance = AttendanceUtils.calculateMonthlyAttendance(
                timetable,
                academicEvents,
                startDate,
                endDate,
                attendanceRecords
            )
            currentAttendance = if (monthlyAttendance.isNotEmpty()) {
                val totalAttended = monthlyAttendance.sumOf { it.attendedLectures }
                val totalLectures = monthlyAttendance.sumOf { it.totalLectures }
                if (totalLectures > 0) (totalAttended * 100) / totalLectures else 0
            } else 0
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Monthly Attendance per Subject", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(monthlyAttendance) { attendance ->
                Text(
                    "${attendance.subject} - ${attendance.month}: ${attendance.attendedLectures}/${attendance.totalLectures}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun TimetableInput(
    timetable: Map<String, List<String>>,
    onTimetableChange: (Map<String, List<String>>) -> Unit
) {
    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val subjectsInput = remember { mutableStateMapOf<String, String>() }

    Column {
        Text("Weekly Timetable Input", style = MaterialTheme.typography.titleMedium)
        daysOfWeek.forEach { day ->
            var text by remember { mutableStateOf(subjectsInput[day] ?: "") }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    subjectsInput[day] = it
                    val subjects = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
                    val newTimetable = timetable.toMutableMap()
                    newTimetable[day] = subjects
                    onTimetableChange(newTimetable)
                },
                label = { Text(day) },
                placeholder = { Text("Enter subjects separated by commas") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun WarningMessage() {
    val alphaAnim by animateFloatAsState(targetValue = 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .alpha(alphaAnim),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Warning: Attendance below minimum required!",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

fun readPdfText(context: Context, uri: Uri): String {
    // Placeholder function to read PDF text content
    // Real implementation would use a PDF parsing library
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val text = reader.readText()
        reader.close()
        text
    } catch (e: Exception) {
        ""
    }
}

package com.example.report

import com.example.data.model.DoseLog
import com.example.data.model.Medicine
import com.example.data.model.Profile
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Pure-Kotlin builder that turns a profile's medicines + dose logs into a fully
 * self-contained, printable HTML report. Output includes inline CSS so it renders
 * correctly in any WebView, browser, or shared file viewer without external assets.
 *
 * The report intentionally uses a clean, monospaced-friendly clinical layout so it
 * is easy for a doctor or pharmacist to read at a glance.
 */
object HtmlReportBuilder {

    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("MMMM d, yyyy")
    private val DISPLAY_TIME_FMT = DateTimeFormatter.ofPattern("h:mm a")
    private val GENERATED_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")

    /**
     * @param profile the profile whose medicines + logs are summarized.
     * @param medicines all medicines registered for the profile (active + ended).
     * @param allLogs every dose log for the profile across all dates.
     * @param windowDays how many days back from today the day-by-day section covers.
     */
    fun build(
        profile: Profile,
        medicines: List<Medicine>,
        allLogs: List<DoseLog>,
        windowDays: Int = 30
    ): String {
        val today = LocalDate.now()
        val windowStart = today.minusDays((windowDays - 1).toLong())

        val logsInWindow = allLogs.filter { log ->
            runCatching { LocalDate.parse(log.dateStr, DATE_FMT) }
                .getOrNull()?.let { d -> !d.isBefore(windowStart) && !d.isAfter(today) } == true
        }

        val totals = computeTotals(logsInWindow)
        val perMedicine = computePerMedicine(medicines, logsInWindow)
        val byDay = logsInWindow.groupBy { it.dateStr }.toSortedMap(reverseOrder())

        return buildString {
            append("<!DOCTYPE html>\n")
            append("<html lang=\"en\"><head>\n")
            append("<meta charset=\"UTF-8\"/>\n")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>\n")
            append("<title>MediTracker Report — ").append(escape(profile.name)).append("</title>\n")
            append("<style>").append(STYLE).append("</style>\n")
            append("</head><body>\n")

            // ───── Header
            append("<header class=\"hdr\">\n")
            append("<div class=\"hdr-brand\"><span class=\"shield\">⛨</span> MediTracker Report</div>\n")
            append("<h1>").append(escape(profile.name)).append("</h1>\n")
            append("<div class=\"hdr-meta\">")
            append(windowStart.format(DISPLAY_DATE_FMT))
            append(" &nbsp;→&nbsp; ")
            append(today.format(DISPLAY_DATE_FMT))
            append(" &nbsp;•&nbsp; Generated ")
            append(LocalDateTime.now().format(GENERATED_FMT))
            append("</div>\n")
            append("</header>\n")

            // ───── At-a-glance summary
            append("<section class=\"section\">\n")
            append("<h2>At a glance</h2>\n")
            append("<div class=\"stats\">\n")
            append(stat("Overall adherence", "${totals.adherencePct}%", "primary"))
            append(stat("Doses taken", totals.taken.toString(), "good"))
            append(stat("Missed", totals.missed.toString(), "bad"))
            append(stat("Skipped", totals.skipped.toString(), "muted"))
            append(stat("Total scheduled", totals.totalScheduled.toString(), "muted"))
            append("</div>\n")
            append("</section>\n")

            // ───── Per-medicine breakdown
            append("<section class=\"section\">\n")
            append("<h2>Per-medicine breakdown</h2>\n")
            if (perMedicine.isEmpty()) {
                append("<p class=\"empty\">No medicines registered for this profile.</p>\n")
            } else {
                append("<table>\n")
                append("<thead><tr>")
                append("<th>Medicine</th><th>Dosage</th><th>Schedule</th>")
                append("<th class=\"num\">Taken</th><th class=\"num\">Missed</th>")
                append("<th class=\"num\">Skipped</th><th class=\"num\">Adherence</th>")
                append("</tr></thead>\n<tbody>\n")
                perMedicine.forEach { row ->
                    append("<tr>")
                    append("<td><strong>").append(escape(row.name)).append("</strong></td>")
                    append("<td>").append(escape(row.dosage)).append("</td>")
                    append("<td>").append(escape(row.schedule)).append("</td>")
                    append("<td class=\"num good\">").append(row.taken).append("</td>")
                    append("<td class=\"num bad\">").append(row.missed).append("</td>")
                    append("<td class=\"num muted\">").append(row.skipped).append("</td>")
                    append("<td class=\"num primary\">").append(row.adherencePct).append("%</td>")
                    append("</tr>\n")
                }
                append("</tbody></table>\n")
            }
            append("</section>\n")

            // ───── Day-by-day log
            append("<section class=\"section\">\n")
            append("<h2>Day-by-day log <span class=\"sub\">(last ").append(windowDays).append(" days)</span></h2>\n")
            if (byDay.isEmpty()) {
                append("<p class=\"empty\">No dose logs in this period.</p>\n")
            } else {
                byDay.forEach { (dateStr, logs) ->
                    val parsed = runCatching { LocalDate.parse(dateStr, DATE_FMT) }.getOrNull()
                    val display = parsed?.format(DISPLAY_DATE_FMT) ?: dateStr
                    append("<div class=\"day\">\n")
                    append("<div class=\"day-header\">").append(escape(display)).append("</div>\n")
                    append("<ul class=\"doses\">\n")
                    logs.sortedBy { it.scheduledTime }.forEach { log ->
                        val timeDisplay = runCatching {
                            LocalTime.parse(log.scheduledTime).format(DISPLAY_TIME_FMT)
                        }.getOrDefault(log.scheduledTime)
                        append("<li class=\"dose dose-").append(log.status.lowercase()).append("\">")
                        append("<span class=\"dot\"></span>")
                        append("<span class=\"time\">").append(escape(timeDisplay)).append("</span>")
                        append("<span class=\"name\">").append(escape(log.medicineName)).append("</span>")
                        append("<span class=\"dose-meta\">").append(escape(log.dosage)).append("</span>")
                        append("<span class=\"status\">").append(escape(log.status)).append("</span>")
                        append("</li>\n")
                    }
                    append("</ul>\n</div>\n")
                }
            }
            append("</section>\n")

            // ───── Doctor notes
            append("<section class=\"section notes\">\n")
            append("<h2>Notes for your doctor</h2>\n")
            append("<div class=\"notes-lines\">")
            repeat(8) { append("<div class=\"line\"></div>") }
            append("</div>\n")
            append("</section>\n")

            // ───── Footer disclaimer
            append("<footer class=\"foot\">\n")
            append("Generated by MediTracker. This report summarizes self-reported medication adherence ")
            append("for educational discussion with your healthcare provider. It is not a clinical record ")
            append("and should not be used as the sole basis for medical decisions.\n")
            append("</footer>\n")

            append("</body></html>\n")
        }
    }

    private fun stat(label: String, value: String, tone: String): String =
        "<div class=\"stat $tone\"><div class=\"stat-value\">$value</div>" +
            "<div class=\"stat-label\">${escape(label)}</div></div>\n"

    // ────────────── Aggregations ──────────────

    private data class Totals(
        val totalScheduled: Int,
        val taken: Int,
        val missed: Int,
        val skipped: Int,
        val pending: Int
    ) {
        val adherencePct: Int
            get() {
                val countable = taken + missed + skipped
                if (countable == 0) return 0
                return ((taken.toDouble() / countable) * 100).toInt()
            }
    }

    private fun computeTotals(logs: List<DoseLog>): Totals {
        var taken = 0; var missed = 0; var skipped = 0; var pending = 0
        logs.forEach {
            when (it.status) {
                "Taken" -> taken++
                "Missed" -> missed++
                "Skipped" -> skipped++
                else -> pending++
            }
        }
        return Totals(logs.size, taken, missed, skipped, pending)
    }

    private data class MedicineRow(
        val name: String,
        val dosage: String,
        val schedule: String,
        val taken: Int,
        val missed: Int,
        val skipped: Int,
        val adherencePct: Int
    )

    private fun computePerMedicine(
        medicines: List<Medicine>,
        logsInWindow: List<DoseLog>
    ): List<MedicineRow> {
        val byMedId = logsInWindow.groupBy { it.medicineId }
        return medicines.map { med ->
            val logs = byMedId[med.id].orEmpty()
            val taken = logs.count { it.status == "Taken" }
            val missed = logs.count { it.status == "Missed" }
            val skipped = logs.count { it.status == "Skipped" }
            val countable = taken + missed + skipped
            val pct = if (countable == 0) 0 else ((taken.toDouble() / countable) * 100).toInt()

            val scheduleDisplay = med.getTimesList()
                .mapNotNull { runCatching { LocalTime.parse(it).format(DISPLAY_TIME_FMT) }.getOrNull() }
                .joinToString(", ")

            MedicineRow(
                name = med.name,
                dosage = med.dosage,
                schedule = scheduleDisplay.ifBlank { "—" },
                taken = taken,
                missed = missed,
                skipped = skipped,
                adherencePct = pct
            )
        }.sortedByDescending { it.taken + it.missed + it.skipped }
    }

    /** Minimal HTML escape for user-supplied strings. */
    private fun escape(input: String): String =
        input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    // ────────────── Inline stylesheet ──────────────

    private val STYLE = """
        :root {
          --primary: #00897B;
          --primary-soft: #E0F2F1;
          --good: #2E7D32;
          --good-soft: #E8F5E9;
          --bad: #C62828;
          --bad-soft: #FFEBEE;
          --muted: #6B6B6B;
          --muted-soft: #F2F2F2;
          --bg: #FAFAFA;
          --ink: #1B1B1B;
          --rule: #E0E0E0;
        }
        * { box-sizing: border-box; }
        html, body {
          margin: 0; padding: 0;
          background: var(--bg);
          color: var(--ink);
          font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, system-ui, sans-serif;
          font-size: 14px;
          line-height: 1.45;
        }
        header.hdr {
          background: linear-gradient(135deg, #00897B 0%, #4DB6AC 100%);
          color: white;
          padding: 28px 24px;
        }
        .hdr-brand {
          font-size: 11px;
          letter-spacing: 0.18em;
          text-transform: uppercase;
          font-weight: 800;
          opacity: 0.85;
        }
        .shield { display: inline-block; transform: translateY(1px); margin-right: 4px; }
        h1 {
          margin: 6px 0 6px 0;
          font-size: 26px;
          font-weight: 800;
          letter-spacing: -0.01em;
        }
        .hdr-meta { font-size: 12px; opacity: 0.92; }

        .section {
          background: white;
          margin: 16px;
          padding: 18px 20px;
          border-radius: 12px;
          border: 1px solid var(--rule);
        }
        h2 {
          margin: 0 0 12px 0;
          font-size: 14px;
          font-weight: 800;
          letter-spacing: 0.06em;
          text-transform: uppercase;
          color: var(--primary);
        }
        h2 .sub {
          font-weight: 500;
          font-size: 11px;
          color: var(--muted);
          letter-spacing: normal;
          text-transform: none;
          margin-left: 6px;
        }

        .stats {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
          gap: 10px;
        }
        .stat {
          padding: 12px 14px;
          border-radius: 10px;
          background: var(--muted-soft);
        }
        .stat-value {
          font-size: 22px;
          font-weight: 800;
          letter-spacing: -0.02em;
        }
        .stat-label {
          font-size: 11px;
          text-transform: uppercase;
          letter-spacing: 0.08em;
          color: var(--muted);
          margin-top: 2px;
        }
        .stat.primary { background: var(--primary-soft); }
        .stat.primary .stat-value { color: var(--primary); }
        .stat.good { background: var(--good-soft); }
        .stat.good .stat-value { color: var(--good); }
        .stat.bad { background: var(--bad-soft); }
        .stat.bad .stat-value { color: var(--bad); }

        table {
          width: 100%;
          border-collapse: collapse;
          font-size: 13px;
        }
        th, td {
          text-align: left;
          padding: 10px 8px;
          border-bottom: 1px solid var(--rule);
        }
        th {
          font-size: 11px;
          letter-spacing: 0.06em;
          text-transform: uppercase;
          color: var(--muted);
          font-weight: 700;
        }
        td.num, th.num { text-align: right; font-variant-numeric: tabular-nums; }
        td.good { color: var(--good); font-weight: 600; }
        td.bad { color: var(--bad); font-weight: 600; }
        td.muted { color: var(--muted); }
        td.primary { color: var(--primary); font-weight: 700; }

        .day {
          padding: 10px 0;
          border-bottom: 1px solid var(--rule);
        }
        .day:last-child { border-bottom: none; }
        .day-header {
          font-weight: 700;
          font-size: 13px;
          color: var(--primary);
          margin-bottom: 6px;
        }
        ul.doses { list-style: none; padding: 0; margin: 0; }
        li.dose {
          display: grid;
          grid-template-columns: 14px 80px 1fr auto auto;
          gap: 10px;
          align-items: center;
          padding: 6px 4px;
          font-size: 13px;
        }
        li.dose .dot {
          width: 8px; height: 8px; border-radius: 50%;
          background: var(--muted);
        }
        li.dose .time { color: var(--muted); font-variant-numeric: tabular-nums; }
        li.dose .name { font-weight: 600; }
        li.dose .dose-meta { color: var(--muted); font-size: 12px; }
        li.dose .status {
          font-size: 11px;
          font-weight: 700;
          padding: 2px 8px;
          border-radius: 999px;
          background: var(--muted-soft);
          color: var(--muted);
          text-transform: uppercase;
          letter-spacing: 0.06em;
        }
        li.dose-taken .dot { background: var(--good); }
        li.dose-taken .status { background: var(--good-soft); color: var(--good); }
        li.dose-missed .dot { background: var(--bad); }
        li.dose-missed .status { background: var(--bad-soft); color: var(--bad); }
        li.dose-skipped .dot { background: var(--muted); }
        li.dose-pending .dot { background: var(--primary); }
        li.dose-pending .status { background: var(--primary-soft); color: var(--primary); }

        .notes-lines { padding: 4px 0; }
        .notes-lines .line {
          height: 1px;
          background: var(--rule);
          margin: 22px 0;
        }
        .empty { color: var(--muted); font-style: italic; padding: 8px 0; }

        footer.foot {
          margin: 16px;
          padding: 14px 20px;
          font-size: 11px;
          color: var(--muted);
          text-align: center;
          line-height: 1.5;
        }
    """.trimIndent()
}

package com.githabit.prototype.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.githabit.prototype.R
import com.githabit.prototype.domain.RepoStatus
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Display locale only: never used to identify persisted dates or parse raw form inputs. */
@Composable
internal fun displayLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

fun formatAmount(value: Double, locale: Locale = Locale.getDefault()): String =
    DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(locale)).format(value)

fun dateLabel(day: LocalDate, locale: Locale = Locale.getDefault()): String =
    day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

@Composable
internal fun amountLabel(value: Double): String = formatAmount(value, displayLocale())

@Composable
internal fun localizedDateLabel(day: LocalDate): String = dateLabel(day, displayLocale())

@Composable
internal fun weekdayLabel(day: DayOfWeek, style: TextStyle = TextStyle.FULL): String =
    day.getDisplayName(style, displayLocale())

@Composable
internal fun repoStatusLabel(status: RepoStatus): String = stringResource(when (status) {
    RepoStatus.ACTIVE -> R.string.repo_filter_active
    RepoStatus.PAUSED -> R.string.repo_filter_paused
    RepoStatus.ARCHIVED -> R.string.repo_filter_archived
})

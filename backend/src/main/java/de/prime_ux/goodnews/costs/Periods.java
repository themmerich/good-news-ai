package de.prime_ux.goodnews.costs;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Where today, this week, this month and this year begin.
 *
 * <p>Worked out here rather than in the query, because a boundary is a calendar question and the
 * calendar depends on a zone: the day begins at midnight where the reader sits, not at midnight
 * UTC. The zone is the machine's own. That holds as long as server and reader are in the same
 * one; a deployment that moves the server to UTC would need this to become a setting.
 *
 * <p>The week begins on Monday, as it does everywhere this application is read.
 *
 * @param day the first moment of today
 * @param week the first moment of Monday this week
 * @param month the first moment of the first of this month
 * @param year the first moment of the first of January
 */
public record Periods(Instant day, Instant week, Instant month, Instant year) {

	/** Takes a clock rather than asking the system, so the boundaries can be tested. */
	public static Periods startingAt(Clock clock) {
		ZonedDateTime now = ZonedDateTime.now(clock);
		ZoneId zone = now.getZone();
		LocalDate today = now.toLocalDate();
		return new Periods(
				today.atStartOfDay(zone).toInstant(),
				today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant(),
				today.withDayOfMonth(1).atStartOfDay(zone).toInstant(),
				today.withDayOfYear(1).atStartOfDay(zone).toInstant());
	}
}

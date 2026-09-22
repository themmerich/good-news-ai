package de.prime_ux.goodnews.costs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/**
 * Where the four periods begin. The one piece of this feature that is easy to get wrong by a day:
 * a week that starts on Sunday, or a boundary taken in UTC while the reader is in Berlin, both
 * put the wrong rows into the wrong tile without anything looking broken.
 */
class PeriodsTest {

	private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

	/** A Tuesday morning, so the week's start is plainly behind us and not today. */
	private static final ZonedDateTime TUESDAY = ZonedDateTime.of(2026, 9, 22, 6, 28, 49, 0, BERLIN);

	@Test
	void takesTheDayFromMidnightInTheReadersZone() {
		Periods periods = Periods.startingAt(fixedAt(TUESDAY));

		assertThat(periods.day()).isEqualTo(ZonedDateTime.of(2026, 9, 22, 0, 0, 0, 0, BERLIN).toInstant());
	}

	@Test
	void startsTheWeekOnMonday() {
		Periods periods = Periods.startingAt(fixedAt(TUESDAY));

		assertThat(periods.week()).isEqualTo(ZonedDateTime.of(2026, 9, 21, 0, 0, 0, 0, BERLIN).toInstant());
	}

	/** On a Monday the week starts today, not seven days ago. */
	@Test
	void startsTheWeekTodayWhenTodayIsMonday() {
		Periods periods = Periods.startingAt(fixedAt(ZonedDateTime.of(2026, 9, 21, 9, 0, 0, 0, BERLIN)));

		assertThat(periods.week()).isEqualTo(ZonedDateTime.of(2026, 9, 21, 0, 0, 0, 0, BERLIN).toInstant());
	}

	/** And on a Sunday it started six days ago, which is the case an off-by-one gets wrong. */
	@Test
	void keepsSundayInTheWeekThatBeganTheMondayBefore() {
		Periods periods = Periods.startingAt(fixedAt(ZonedDateTime.of(2026, 9, 27, 23, 0, 0, 0, BERLIN)));

		assertThat(periods.week()).isEqualTo(ZonedDateTime.of(2026, 9, 21, 0, 0, 0, 0, BERLIN).toInstant());
	}

	@Test
	void takesTheMonthAndTheYearFromTheirFirstDays() {
		Periods periods = Periods.startingAt(fixedAt(TUESDAY));

		assertThat(periods.month()).isEqualTo(ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, BERLIN).toInstant());
		assertThat(periods.year()).isEqualTo(ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, BERLIN).toInstant());
	}

	/**
	 * Just after midnight in Berlin it is still the day before in UTC. Boundaries read off the
	 * wrong zone would put the last hours of an evening's run into yesterday.
	 */
	@Test
	void takesMidnightWhereTheReaderIsRatherThanInUtc() {
		Periods periods = Periods.startingAt(fixedAt(ZonedDateTime.of(2026, 9, 22, 0, 30, 0, 0, BERLIN)));

		assertThat(periods.day()).isEqualTo(ZonedDateTime.of(2026, 9, 22, 0, 0, 0, 0, BERLIN).toInstant());
	}

	private static Clock fixedAt(ZonedDateTime moment) {
		return Clock.fixed(moment.toInstant(), moment.getZone());
	}
}

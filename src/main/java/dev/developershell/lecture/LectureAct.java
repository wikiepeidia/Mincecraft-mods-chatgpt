package dev.developershell.lecture;

import java.util.Optional;

/** Stable identities and Standard health floors for Professor Infinite Slides' three acts. */
public enum LectureAct {
	SLIDE_DECK(1, 80),
	SURPRISE_QUIZ(2, 40),
	ATTENDANCE_CHECK(3, 0);

	private final int number;
	private final int healthThreshold;

	LectureAct(int number, int healthThreshold) {
		this.number = number;
		this.healthThreshold = healthThreshold;
	}

	public int number() {
		return number;
	}

	public int healthThreshold() {
		return healthThreshold;
	}

	public int windUpTicks(LectureRules rules) {
		java.util.Objects.requireNonNull(rules, "rules");
		return switch (this) {
			case SLIDE_DECK -> rules.slideDeckTelegraphTicks();
			case SURPRISE_QUIZ -> rules.quizTelegraphTicks();
			case ATTENDANCE_CHECK -> rules.attendanceTelegraphTicks();
		};
	}

	public Optional<LectureAct> next() {
		return switch (this) {
			case SLIDE_DECK -> Optional.of(SURPRISE_QUIZ);
			case SURPRISE_QUIZ -> Optional.of(ATTENDANCE_CHECK);
			case ATTENDANCE_CHECK -> Optional.empty();
		};
	}
}

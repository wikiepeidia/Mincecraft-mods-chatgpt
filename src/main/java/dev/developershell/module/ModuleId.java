package dev.developershell.module;

public enum ModuleId {
	GRADUATION_ANYFAIL("graduation_anyfail"),
	METADATA_ROULETTE("metadata_roulette"),
	PYTHON_TOOLS("python_tools"),
	CODEX_RICH_KID_TERMINAL("codex_rich_kid_terminal"),
	GIT_HAPPENS("git_happens"),
	STACK_OVERFLOW_TOTEM("stack_overflow_totem"),
	RUBBER_DUCK_ENGINEERING("rubber_duck_engineering"),
	THREE_DAY_DEADLINE("three_day_deadline");

	private final String serializedName;

	ModuleId(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}
}

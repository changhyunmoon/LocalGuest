package com.team6.module.ai.policy;

public final class ScoreWeight {

    private ScoreWeight() {
    }

    public static final int REGION = 30;
    /** {@link com.team6.module.ai.policy.RegionMatchPolicy} 인접 지역(이동 가능권) 부분 일치. */
    public static final int REGION_ADJACENT = 15;
    public static final int STYLE = 25;
    public static final int BUDGET = 15;
    /** {@link com.team6.module.ai.policy.BudgetMatchPolicy} 인접 티어(낮음↔중간↔높음) 부분 일치. */
    public static final int BUDGET_ADJACENT = 7;
    public static final int ACTIVITY = 10;
    public static final int LANGUAGE = 10;
}
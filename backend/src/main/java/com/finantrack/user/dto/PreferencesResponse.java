package com.finantrack.user.dto;

import com.finantrack.user.entity.Theme;

public record PreferencesResponse(String currency, Theme theme, boolean notifyBudget, boolean notifyDueDates) {
}

namespace FinanTrack.Web.DTOs;

public sealed record UpdateProfileRequest(string Name);

public sealed record ChangePasswordRequest(string CurrentPassword, string NewPassword);

public sealed record EmailChangeRequest(string NewEmail, string CurrentPassword);

public sealed record DeletionRequest(string CurrentPassword);

public sealed record PreferencesDto(string Currency, string Theme, bool NotifyBudget, bool NotifyDueDates);

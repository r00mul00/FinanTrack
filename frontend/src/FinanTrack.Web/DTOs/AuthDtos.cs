namespace FinanTrack.Web.DTOs;

public sealed record LoginRequest(string Email, string Password);

public sealed record RegisterRequest(string Name, string Email, string Password);

public sealed record EmailRequest(string Email);

public sealed record TokenRequest(string Token);

public sealed record ResetPasswordRequest(string Token, string NewPassword);

public sealed record TokenResponse(string AccessToken, string TokenType, long ExpiresIn);

public sealed record UserResponse(Guid Id, string Name, string Email, string Currency, string Theme);

public sealed record ProblemResponse(string? Detail, string? Code, Dictionary<string, string>? Errors);

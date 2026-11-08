using FinanTrack.Web.Models;

namespace FinanTrack.Web.DTOs;

public sealed record CategoryRequest(string Name, CategoryType Type, string Icon, string Color, bool Archived);

public sealed record CategoryResponse(Guid Id, string Name, CategoryType Type, string Icon, string Color, bool Archived);

public sealed record CategoryUsageResponse(long References);

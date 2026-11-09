using FinanTrack.Web.Models;

namespace FinanTrack.Web.DTOs;

public sealed record SearchResult(SearchResultType Type, Guid Id, string Title, string? Detail);

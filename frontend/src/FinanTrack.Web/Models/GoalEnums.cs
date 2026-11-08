using System.Text.Json.Serialization;

namespace FinanTrack.Web.Models;

[JsonConverter(typeof(JsonStringEnumConverter<GoalStatus>))]
public enum GoalStatus
{
    ACTIVE,
    COMPLETED
}

[JsonConverter(typeof(JsonStringEnumConverter<GoalMovementType>))]
public enum GoalMovementType
{
    DEPOSIT,
    WITHDRAWAL
}

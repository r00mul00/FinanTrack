using System.ComponentModel.DataAnnotations;
using System.Text;

namespace FinanTrack.Web.Validation;

/// <summary>Validação de conveniência; a regra definitiva está na API.</summary>
public static class AuthRules
{
    private const int MinPasswordChars = 8;
    private const int MaxPasswordBytes = 72;

    private static readonly EmailAddressAttribute EmailFormat = new();

    public static string? Email(string? value) =>
        !string.IsNullOrWhiteSpace(value) && EmailFormat.IsValid(value.Trim()) ? null : "Informe um e-mail válido.";

    public static string? NewPassword(string? value)
    {
        if (string.IsNullOrEmpty(value) || value.Length < MinPasswordChars)
        {
            return "A senha deve ter pelo menos 8 caracteres.";
        }
        return Encoding.UTF8.GetByteCount(value) > MaxPasswordBytes ? "A senha é longa demais." : null;
    }
}

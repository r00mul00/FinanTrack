namespace FinanTrack.Web.State;

/// <summary>
/// Itens de uma lista com ação em andamento (efetivar, pausar, excluir...). Enquanto a
/// requisição roda, os botões do item ficam desativados: o segundo clique não chega à API
/// e o usuário vê que algo está acontecendo.
/// </summary>
public sealed class PendingActions
{
    private readonly HashSet<Guid> _pending = [];

    public bool IsPending(Guid id) => _pending.Contains(id);

    /// <summary>Ignora o pedido se o item já tiver uma ação em andamento.</summary>
    public async Task RunAsync(Guid id, Func<Task> action)
    {
        if (!_pending.Add(id))
        {
            return;
        }
        try
        {
            await action();
        }
        finally
        {
            _pending.Remove(id);
        }
    }
}

package CausalMulticast;

/**
 * Interface de callback para recebimento de mensagens do middleware.
 * Clientes do pacote CausalMulticast devem implementar esta interface.
 */
public interface ICausalMulticast {

    /**
     * Callback invocado pelo middleware quando uma mensagem está pronta
     * para entrega (respeitando a ordem causal).
     *
     * @param msg conteúdo textual da mensagem entregue
     */
    void deliver(String msg);
}

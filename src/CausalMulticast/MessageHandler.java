package CausalMulticast;

/**
 * Interface funcional de callback para recepção de datagramas UDP.
 * Implementada pela classe CausalMulticast (Módulo 3) para processar
 * mensagens recebidas.
 */
@FunctionalInterface
public interface MessageHandler {

    /**
     * Invocado pelo UDPReceiver quando um datagrama é recebido.
     *
     * @param data   bytes do datagrama recebido (já recortado no tamanho real)
     * @param length número de bytes válidos no array
     */
    void onReceive(byte[] data, int length);
}

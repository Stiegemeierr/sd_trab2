package CausalMulticast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Responsável por enviar datagramas UDP unicast.
 */
public class UDPSender {
    private final DatagramSocket socket;
    public static final int MAX_DATAGRAM_SIZE = 65507;

    /**
     * Construtor do sender.
     *
     * @param socket socket UDP compartilhado
     */
    public UDPSender(DatagramSocket socket) {
        this.socket = socket;
    }

    /**
     * Envia um datagrama UDP unicast para o endereço e porta especificados.
     *
     * @param data    bytes a serem enviados
     * @param address endereço IP de destino
     * @param port    porta de destino
     * @throws IOException se ocorrer um erro de I/O
     * @throws IllegalArgumentException se o datagrama for maior que o máximo permitido
     */
    public void send(byte[] data, InetAddress address, int port) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("Array de dados não pode ser nulo.");
        }
        if (data.length > MAX_DATAGRAM_SIZE) {
            throw new IllegalArgumentException("O datagrama excede o tamanho máximo permitido de " + MAX_DATAGRAM_SIZE + " bytes.");
        }
        
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
    }
}

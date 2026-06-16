package CausalMulticast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;

/**
 * Thread receptora que escuta datagramas UDP continuamente e repassa via callback.
 */
public class UDPReceiver implements Runnable {
    private final DatagramSocket socket;
    private final MessageHandler handler;
    private volatile boolean running;
    private static final int MAX_DATAGRAM_SIZE = 65507;

    /**
     * Construtor do receptor.
     *
     * @param socket  socket UDP compartilhado
     * @param handler callback para mensagens recebidas
     */
    public UDPReceiver(DatagramSocket socket, MessageHandler handler) {
        this.socket = socket;
        this.handler = handler;
        this.running = true;
    }

    /**
     * Seta running = false para encerrar o loop graciosamente.
     */
    public void stop() {
        this.running = false;
    }

    /**
     * Loop principal de recepção.
     */
    @Override
    public void run() {
        while (running) {
            try {
                byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                
                if (running) {
                    byte[] receivedBytes = Arrays.copyOf(packet.getData(), packet.getLength());
                    handler.onReceive(receivedBytes, receivedBytes.length);
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[UDPReceiver] Erro ao receber datagrama: " + e.getMessage());
                }
                // Se running == false, a exceção (como SocketException gerada ao fechar o socket externamente)
                // é ignorada e o loop encerra naturalmente.
            }
        }
    }
}

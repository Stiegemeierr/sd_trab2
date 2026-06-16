import CausalMulticast.MessageHandler;
import CausalMulticast.UDPReceiver;
import CausalMulticast.UDPSender;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * Classe de teste para o Módulo 2 (Comunicação UDP).
 */
public class TestModulo2 {
    /**
     * Ponto de entrada do teste do Módulo 2.
     *
     * @param args argumentos de linha de comando
     */
    public static void main(String[] args) {
        try {
            System.out.println("Iniciando TestModulo2...");

            // 1. Cria socket e estado de teste
            DatagramSocket socket = new DatagramSocket(9999);
            final boolean[] messageReceived = {false};

            // 2. Instancia receptor e handler
            MessageHandler handler = (data, length) -> {
                String str = new String(data, 0, length, StandardCharsets.UTF_8);
                System.out.println("Receptor: " + str);
                if ("0|1,0,0|Hello UDP".equals(str)) {
                    messageReceived[0] = true;
                }
            };

            UDPReceiver receiver = new UDPReceiver(socket, handler);
            Thread receiverThread = new Thread(receiver, "UDPReceiver");
            receiverThread.setDaemon(true);
            receiverThread.start();

            // 3. Instancia sender
            UDPSender sender = new UDPSender(socket);

            // 4. Envia mensagem
            String msg = "0|1,0,0|Hello UDP";
            byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
            sender.send(bytes, InetAddress.getByName("localhost"), 9999);

            // 5. Aguarda processamento
            Thread.sleep(500);

            if (!messageReceived[0]) {
                throw new Exception("Falha: mensagem nao recebida ou nao bate com o esperado");
            }

            // 6. Validacao de limite de tamanho
            try {
                byte[] bigData = new byte[65508];
                sender.send(bigData, InetAddress.getByName("localhost"), 9999);
                throw new Exception("Falha: deveria ter lancado excecao no datagrama gigante");
            } catch (IllegalArgumentException e) {
                System.out.println("Validacao OK: " + e.getMessage());
            }

            // 7. Stop gracioso
            receiver.stop();
            socket.close();

            System.out.println("TESTE MÓDULO 2: OK");

        } catch (Exception e) {
            System.err.println("TESTE MÓDULO 2: FALHOU");
            e.printStackTrace();
        }
    }
}

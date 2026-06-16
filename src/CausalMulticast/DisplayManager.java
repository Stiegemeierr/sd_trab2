package CausalMulticast;

import java.util.Arrays;
import java.util.List;

/**
 * Utilitário para formatar e imprimir o estado do middleware no terminal.
 */
public class DisplayManager {
    private final int selfIndex;
    private final String selfLabel;

    /**
     * Constrói o DisplayManager.
     *
     * @param selfIndex o índice do processo local
     * @param ip endereço IP do processo
     * @param port porta do processo
     */
    public DisplayManager(int selfIndex, String ip, int port) {
        this.selfIndex = selfIndex;
        this.selfLabel = "Processo " + selfIndex + " - " + ip + ":" + port;
    }

    /**
     * Imprime o estado atual do middleware.
     *
     * @param event o evento que engatilhou a impressão (ex: "ENVIO", "RECEPÇÃO")
     * @param vc relógio vetorial atual
     * @param stabilityMatrix matriz de estabilidade atual
     * @param buffer buffer de mensagens pendentes
     * @param delayedQueue fila de mensagens atrasadas
     */
    public void printState(String event, int[] vc, int[][] stabilityMatrix, MessageBuffer buffer, List<CausalMulticast.DelayedEntry> delayedQueue) {
        System.out.println("========================================");
        System.out.println("[EVENTO: " + event + "] " + selfLabel);
        System.out.println("── Relógio Vetorial: " + Arrays.toString(vc));
        System.out.println("── Matriz de Estabilidade:");
        for (int i = 0; i < stabilityMatrix.length; i++) {
            System.out.println("   P" + i + ": " + Arrays.toString(stabilityMatrix[i]));
        }
        
        System.out.println("── Buffer (mensagens pendentes): " + buffer.size());
        if (buffer.size() == 0) {
            System.out.println("   (vazio)");
        } else {
            for (Message m : buffer.getAll()) {
                System.out.println("   " + m.toString());
            }
        }
        
        System.out.println("── Mensagens atrasadas: " + delayedQueue.size());
        if (delayedQueue.size() == 0) {
            System.out.println("   (nenhuma)");
        } else {
            for (int i = 0; i < delayedQueue.size(); i++) {
                System.out.println("   [" + i + "] " + delayedQueue.get(i).getDescription());
            }
        }
        System.out.println("========================================");
    }
}

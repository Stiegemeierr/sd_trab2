package CausalMulticast;

import java.util.ArrayList;
import java.util.List;

/**
 * Buffer de mensagens pendentes com lógica de consulta para entrega causal e detecção de estabilidade.
 */
public class MessageBuffer {
    private final ArrayList<Message> pending;

    /**
     * Constrói um novo MessageBuffer vazio.
     */
    public MessageBuffer() {
        this.pending = new ArrayList<>();
    }

    /**
     * Adiciona uma nova mensagem ao buffer.
     *
     * @param msg a mensagem recebida a ser adicionada
     */
    public void add(Message msg) {
        pending.add(msg);
    }

    /**
     * Retorna uma lista com todas as mensagens que já podem ser entregues,
     * respeitando a ordem causal atual dada pelo relógio vetorial.
     *
     * @param vc o relógio vetorial atual
     * @return lista de mensagens prontas para entrega
     */
    public List<Message> getDeliverableMessages(int[] vc) {
        List<Message> deliverable = new ArrayList<>();
        for (Message m : pending) {
            if (Message.canDeliver(vc, m.getTimestamp(), m.getSenderIndex())) {
                deliverable.add(m);
            }
        }
        return deliverable;
    }

    /**
     * Remove uma mensagem específica do buffer.
     *
     * @param msg a mensagem a ser removida
     */
    public void remove(Message msg) {
        pending.remove(msg);
    }

    /**
     * Remove uma lista de mensagens do buffer.
     *
     * @param msgs lista de mensagens a serem removidas
     */
    public void removeAll(List<Message> msgs) {
        pending.removeAll(msgs);
    }

    /**
     * Retorna a lista de mensagens que já se tornaram estáveis,
     * baseando-se na matriz de estabilidade.
     *
     * @param stabilityMatrix a matriz de estabilidade atual
     * @return lista de mensagens estáveis
     */
    public List<Message> getStableMessages(int[][] stabilityMatrix) {
        List<Message> stable = new ArrayList<>();
        int N = stabilityMatrix.length;
        for (Message m : pending) {
            int j = m.getSenderIndex();
            int msgSeq = m.getTimestamp()[j];
            boolean isStable = true;
            for (int k = 0; k < N; k++) {
                if (stabilityMatrix[k][j] < msgSeq) {
                    isStable = false;
                    break;
                }
            }
            if (isStable) {
                stable.add(m);
            }
        }
        return stable;
    }

    /**
     * Retorna uma cópia da lista de todas as mensagens no buffer.
     *
     * @return lista de mensagens
     */
    public List<Message> getAll() {
        return new ArrayList<>(pending);
    }

    /**
     * Retorna a quantidade de mensagens no buffer.
     *
     * @return o tamanho do buffer
     */
    public int size() {
        return pending.size();
    }
}

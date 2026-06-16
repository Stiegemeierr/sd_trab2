package CausalMulticast;

import java.util.ArrayList;
import java.util.List;

/**
 * Buffer de mensagens pendentes com lógica de consulta para entrega causal e detecção de estabilidade.
 */
public class MessageBuffer {
    private final ArrayList<Message> pending;

    public MessageBuffer() {
        this.pending = new ArrayList<>();
    }

    public void add(Message msg) {
        pending.add(msg);
    }

    public List<Message> getDeliverableMessages(VectorClock vc) {
        List<Message> deliverable = new ArrayList<>();
        for (Message m : pending) {
            if (vc.canDeliver(m.getTimestamp(), m.getSenderIndex())) {
                deliverable.add(m);
            }
        }
        return deliverable;
    }

    public void remove(Message msg) {
        pending.remove(msg);
    }

    public void removeAll(List<Message> msgs) {
        pending.removeAll(msgs);
    }

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

    public List<Message> getAll() {
        return new ArrayList<>(pending);
    }

    public int size() {
        return pending.size();
    }
}

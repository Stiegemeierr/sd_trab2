package CausalMulticast;

import java.util.Arrays;

/**
 * Relógio vetorial para controle de ordenação causal.
 */
public class VectorClock {
    private int[] clock;
    private final int size;

    /**
     * Constrói um VectorClock com um tamanho específico.
     *
     * @param size tamanho do vetor
     */
    public VectorClock(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Tamanho do VectorClock deve ser positivo");
        }
        this.size = size;
        this.clock = new int[size];
    }

    /**
     * Incrementa o relógio na posição indicada.
     *
     * @param index posição do processo a ser incrementada
     * @throws IndexOutOfBoundsException se índice for inválido
     */
    public void increment(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Índice fora dos limites: " + index);
        }
        clock[index]++;
    }

    /**
     * Retorna o valor atual do relógio para um processo.
     *
     * @param index posição do processo
     * @return valor do relógio
     * @throws IndexOutOfBoundsException se índice for inválido
     */
    public int get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Índice fora dos limites: " + index);
        }
        return clock[index];
    }

    /**
     * Define um valor específico para o relógio de um processo.
     *
     * @param index posição do processo
     * @param value novo valor do relógio
     * @throws IndexOutOfBoundsException se índice for inválido
     */
    public void set(int index, int value) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Índice fora dos limites: " + index);
        }
        clock[index] = value;
    }

    /**
     * Retorna uma cópia defensiva do vetor interno.
     *
     * @return cópia do relógio
     */
    public int[] copy() {
        return Arrays.copyOf(clock, size);
    }

    /**
     * Atualiza o relógio combinando-o com outro, pegando o máximo de cada posição.
     *
     * @param other vetor de relógio para merge
     * @throws IllegalArgumentException se tamanhos forem diferentes
     */
    public void merge(int[] other) {
        if (other == null || other.length != size) {
            throw new IllegalArgumentException("Tamanho do vetor incompatível para merge");
        }
        for (int i = 0; i < size; i++) {
            clock[i] = Math.max(clock[i], other[i]);
        }
    }

    /**
     * Verifica se uma mensagem pode ser entregue causalmente.
     * 1. VT[j] == clock[j] + 1
     * 2. ∀k ≠ j : VT[k] <= clock[k]
     *
     * @param msgTimestamp vetor de relógio da mensagem recebida
     * @param senderIndex  índice do processo remetente (j)
     * @return true se puder entregar, false caso contrário
     */
    public boolean canDeliver(int[] msgTimestamp, int senderIndex) {
        if (msgTimestamp == null || msgTimestamp.length != size) {
            return false;
        }
        if (senderIndex < 0 || senderIndex >= size) {
            return false;
        }

        if (msgTimestamp[senderIndex] != clock[senderIndex] + 1) {
            return false;
        }

        for (int k = 0; k < size; k++) {
            if (k != senderIndex) {
                if (msgTimestamp[k] > clock[k]) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Retorna o tamanho do vetor do relógio.
     *
     * @return tamanho do vetor
     */
    public int getSize() {
        return size;
    }

    /**
     * Representação legível do relógio vetorial.
     *
     * @return string no formato [v0, v1, ..., vN-1]
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < size; i++) {
            sb.append(clock[i]);
            if (i < size - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}

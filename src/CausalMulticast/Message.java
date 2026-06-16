package CausalMulticast;

import java.util.Arrays;

/**
 * Representa uma mensagem com metadados para ordenação causal.
 */
public class Message {
    private final int senderIndex;
    private final int[] timestamp;
    private final String payload;

    /**
     * Constrói uma nova mensagem.
     *
     * @param senderIndex Índice do remetente na lista de membros do grupo (0-indexed)
     * @param timestamp   Vetor de relógio no momento do envio
     * @param payload     Conteúdo textual da mensagem
     */
    public Message(int senderIndex, int[] timestamp, String payload) {
        this.senderIndex = senderIndex;
        this.timestamp = Arrays.copyOf(timestamp, timestamp.length);
        this.payload = payload;
    }

    /**
     * Retorna o índice do remetente.
     *
     * @return índice numérico do processo remetente.
     */
    public int getSenderIndex() {
        return senderIndex;
    }

    /**
     * Retorna uma cópia defensiva do vetor de relógio.
     *
     * @return cópia do timestamp (array de ints).
     */
    public int[] getTimestamp() {
        return Arrays.copyOf(timestamp, timestamp.length);
    }

    /**
     * Retorna o conteúdo da mensagem.
     *
     * @return string com o payload.
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Serializa a mensagem no formato de protocolo.
     * Formato: senderIndex|vc0,vc1,...,vcN-1|payload
     *
     * @return string serializada.
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(senderIndex).append("|");
        for (int i = 0; i < timestamp.length; i++) {
            sb.append(timestamp[i]);
            if (i < timestamp.length - 1) {
                sb.append(",");
            }
        }
        sb.append("|").append(payload);
        return sb.toString();
    }

    /**
     * Desserializa uma string no formato de protocolo para criar uma Message.
     *
     * @param raw string formatada recebida
     * @return objeto Message instanciado
     * @throws IllegalArgumentException se o formato for inválido
     */
    public static Message deserialize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("String bruta não pode ser nula");
        }
        
        int firstPipe = raw.indexOf('|');
        if (firstPipe == -1) {
            throw new IllegalArgumentException("Formato inválido: falta o primeiro pipe");
        }
        
        int secondPipe = raw.indexOf('|', firstPipe + 1);
        if (secondPipe == -1) {
            throw new IllegalArgumentException("Formato inválido: falta o segundo pipe");
        }
        
        String senderStr = raw.substring(0, firstPipe);
        String vcStr = raw.substring(firstPipe + 1, secondPipe);
        String payloadStr = raw.substring(secondPipe + 1);
        
        int senderIndex;
        try {
            senderIndex = Integer.parseInt(senderStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Formato inválido: senderIndex não numérico", e);
        }
        
        String[] vcTokens = vcStr.split(",");
        int[] timestamp;
        
        if (vcStr.trim().isEmpty()) {
            timestamp = new int[0];
        } else {
            timestamp = new int[vcTokens.length];
            try {
                for (int i = 0; i < vcTokens.length; i++) {
                    timestamp[i] = Integer.parseInt(vcTokens[i].trim());
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Formato inválido: componente do VectorClock não numérico", e);
            }
        }
        
        return new Message(senderIndex, timestamp, payloadStr);
    }

    /**
     * Retorna representação legível para debug/display.
     *
     * @return string formatada
     */
    @Override
    public String toString() {
        return String.format("[P%d | VC=%s | \"%s\"]", senderIndex, Arrays.toString(timestamp).replaceAll(" ", ""), payload);
    }
}

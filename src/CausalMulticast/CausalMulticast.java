package CausalMulticast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Classe principal do middleware. Orquestra todos os componentes.
 */
public class CausalMulticast {

    /**
     * Representa uma entrada atrasada na fila de envio de mensagens.
     */
    public static class DelayedEntry {
        private final byte[] datagram;
        private final InetAddress destAddr;
        private final int destPort;
        private final int destIndex;
        private final String description;

        /**
         * Constrói uma nova entrada atrasada.
         *
         * @param datagram dados do datagrama a serem enviados
         * @param destAddr endereço de destino
         * @param destPort porta de destino
         * @param destIndex índice do processo de destino
         * @param description descrição da mensagem
         */
        public DelayedEntry(byte[] datagram, InetAddress destAddr, int destPort, int destIndex, String description) {
            this.datagram = datagram;
            this.destAddr = destAddr;
            this.destPort = destPort;
            this.destIndex = destIndex;
            this.description = description;
        }

        /**
         * Retorna os dados do datagrama.
         *
         * @return array de bytes contendo o datagrama
         */
        public byte[] getDatagram() { return datagram; }
        
        /**
         * Retorna o endereço de destino.
         *
         * @return InetAddress de destino
         */
        public InetAddress getDestAddr() { return destAddr; }
        
        /**
         * Retorna a porta de destino.
         *
         * @return número da porta de destino
         */
        public int getDestPort() { return destPort; }
        
        /**
         * Retorna o índice do processo de destino.
         *
         * @return índice do processo
         */
        public int getDestIndex() { return destIndex; }
        
        /**
         * Retorna a descrição da mensagem.
         *
         * @return string contendo a descrição
         */
        public String getDescription() { return description; }
    }

    private final ICausalMulticast client;
    private final GroupConfig groupConfig;
    private final int selfIndex;
    private final int[] vectorClock;
    private final int[][] stabilityMatrix;
    private final MessageBuffer messageBuffer;
    private final UDPSender udpSender;
    private final UDPReceiver udpReceiver;
    private final DatagramSocket socket;
    private final Thread receiverThread;
    private final Object lock = new Object();
    private static final String CONFIG_PATH = "group.cfg";

    private final ArrayList<DelayedEntry> delayedQueue;
    private final DisplayManager displayManager;
    private final Scanner scanner;

    /**
     * Inicializa o middleware CausalMulticast para um processo específico.
     *
     * @param ip endereço IP local do processo
     * @param port porta local do processo
     * @param client referência para a aplicação cliente que receberá os callbacks
     */
    public CausalMulticast(String ip, Integer port, ICausalMulticast client) {
        try {
            this.client = client;
            this.groupConfig = GroupConfig.load(CONFIG_PATH);
            
            this.selfIndex = groupConfig.indexOf(ip, port);
            if (this.selfIndex == -1) {
                throw new RuntimeException("Processo ip:port não encontrado no group.cfg");
            }
            
            int N = groupConfig.size();
            this.vectorClock = new int[N];
            this.stabilityMatrix = new int[N][N];
            this.messageBuffer = new MessageBuffer();
            
            this.socket = new DatagramSocket(port, InetAddress.getByName(ip));
            this.udpSender = new UDPSender(socket);
            
            this.udpReceiver = new UDPReceiver(socket, this::handleReceive);
            this.receiverThread = new Thread(udpReceiver, "UDPReceiver");
            this.receiverThread.setDaemon(true);
            this.receiverThread.start();

            this.delayedQueue = new ArrayList<>();
            this.displayManager = new DisplayManager(selfIndex, ip, port);
            this.scanner = new Scanner(System.in);
            
            System.out.println("[CausalMulticast] Processo " + selfIndex + " (" + ip + ":" + port + ") iniciado.");
        } catch (IOException e) {
            throw new RuntimeException("Erro ao inicializar CausalMulticast", e);
        }
    }

    /**
     * Envia uma mensagem em multicast (Causal Multicast) para todos os membros do grupo.
     *
     * @param msg conteúdo da mensagem a ser enviada
     * @param cliente referência para a aplicação cliente
     */
    public void mcsend(String msg, ICausalMulticast cliente) {
        boolean[] sendNow = new boolean[groupConfig.size()];
        for (int j = 0; j < groupConfig.size(); j++) {
            if (j != selfIndex) {
                GroupConfig.Member member = groupConfig.getMember(j);
                System.out.print("Enviar para Processo " + j + " (" + member.getIp() + ":" + member.getPort() + ")? [S/n]: ");
                String resp = scanner.nextLine().trim();
                sendNow[j] = !resp.equalsIgnoreCase("n");
            }
        }

        synchronized (lock) {
            vectorClock[selfIndex]++;
            Message m = new Message(selfIndex, Arrays.copyOf(vectorClock, vectorClock.length), msg);
            String serialized = m.serialize();
            byte[] data = serialized.getBytes(StandardCharsets.UTF_8);

            for (int j = 0; j < groupConfig.size(); j++) {
                if (j != selfIndex) {
                    GroupConfig.Member member = groupConfig.getMember(j);
                    try {
                        InetAddress addr = InetAddress.getByName(member.getIp());
                        
                        if (!sendNow[j]) {
                            String description = "Para P" + j + ": " + m.toString();
                            DelayedEntry entry = new DelayedEntry(data, addr, member.getPort(), j, description);
                            delayedQueue.add(entry);
                            System.out.println("[ATRASO] Mensagem retida para Processo " + j);
                        } else {
                            udpSender.send(data, addr, member.getPort());
                        }
                    } catch (IOException e) {
                        System.err.println("[CausalMulticast] Erro ao enviar para " + member.getIp() + ":" + member.getPort() + " - " + e.getMessage());
                    }
                }
            }

            stabilityMatrix[selfIndex] = Arrays.copyOf(vectorClock, vectorClock.length);
            cliente.deliver(msg);
            garbageCollect();
            displayManager.printState("ENVIO", vectorClock, stabilityMatrix, messageBuffer, delayedQueue);
        }
    }

    /**
     * Trata o recebimento de uma nova mensagem UDP, decodifica e verifica as condições de entrega.
     *
     * @param data dados da mensagem recebida
     * @param length tamanho dos dados recebidos
     */
    private void handleReceive(byte[] data, int length) {
        synchronized (lock) {
            String raw = new String(data, 0, length, StandardCharsets.UTF_8);
            Message m;
            try {
                m = Message.deserialize(raw);
            } catch (IllegalArgumentException e) {
                System.err.println("[CausalMulticast] Erro ao desserializar mensagem: " + e.getMessage());
                return;
            }

            messageBuffer.add(m);

            int N = groupConfig.size();
            for (int i = 0; i < N; i++) {
                stabilityMatrix[m.getSenderIndex()][i] = Math.max(stabilityMatrix[m.getSenderIndex()][i], m.getTimestamp()[i]);
            }

            boolean delivered = true;
            while (delivered) {
                delivered = false;
                List<Message> deliverable = messageBuffer.getDeliverableMessages(vectorClock);
                for (Message dm : deliverable) {
                    vectorClock[dm.getSenderIndex()] = dm.getTimestamp()[dm.getSenderIndex()];
                    stabilityMatrix[selfIndex] = Arrays.copyOf(vectorClock, vectorClock.length);
                    client.deliver(dm.getPayload());
                    messageBuffer.remove(dm);
                    delivered = true;
                }
            }

            garbageCollect();
            displayManager.printState("RECEPÇÃO", vectorClock, stabilityMatrix, messageBuffer, delayedQueue);
        }
    }

    /**
     * Realiza a coleta de lixo, descartando mensagens que já se tornaram estáveis em todos os processos.
     */
    private void garbageCollect() {
        List<Message> stable = messageBuffer.getStableMessages(stabilityMatrix);
        if (!stable.isEmpty()) {
            for (Message msg : stable) {
                System.out.println("[GC] Mensagem estável descartada: " + msg);
            }
            messageBuffer.removeAll(stable);
        }
    }

    /**
     * Libera o envio de uma mensagem que estava retida na fila de atraso.
     *
     * @param index índice da mensagem na fila de mensagens atrasadas
     */
    public void releaseDelayed(int index) {
        synchronized (lock) {
            if (index < 0 || index >= delayedQueue.size()) {
                System.err.println("Índice inválido.");
                return;
            }
            DelayedEntry entry = delayedQueue.get(index);
            try {
                udpSender.send(entry.getDatagram(), entry.getDestAddr(), entry.getDestPort());
                delayedQueue.remove(index);
                System.out.println("[LIBERADO] Mensagem enviada para Processo " + entry.getDestIndex());
                displayManager.printState("LIBERAÇÃO", vectorClock, stabilityMatrix, messageBuffer, delayedQueue);
            } catch (IOException e) {
                System.err.println("[CausalMulticast] Erro ao liberar mensagem: " + e.getMessage());
            }
        }
    }

    /**
     * Retorna a lista de mensagens atrasadas.
     *
     * @return lista com as mensagens atrasadas
     */
    public List<DelayedEntry> getDelayedQueue() { return new ArrayList<>(delayedQueue); }
    
    /**
     * Retorna o tamanho da fila de mensagens atrasadas.
     *
     * @return número de mensagens atrasadas na fila
     */
    public int getDelayedQueueSize() { return delayedQueue.size(); }
    
    /**
     * Retorna o relógio vetorial local.
     *
     * @return array de ints
     */
    public int[] getVectorClock() { return vectorClock.clone(); }
    
    /**
     * Retorna a matriz de estabilidade.
     *
     * @return matriz de inteiros representando o conhecimento das mensagens recebidas por cada processo
     */
    public int[][] getStabilityMatrix() {
        int[][] copy = new int[stabilityMatrix.length][];
        for (int i = 0; i < stabilityMatrix.length; i++) {
            copy[i] = stabilityMatrix[i].clone();
        }
        return copy;
    }
    
    /**
     * Retorna o buffer de mensagens recebidas pendentes.
     *
     * @return instância de MessageBuffer
     */
    public MessageBuffer getMessageBuffer() { return messageBuffer; }
    
    /**
     * Retorna a configuração do grupo.
     *
     * @return instância de GroupConfig contendo as definições do grupo
     */
    public GroupConfig getGroupConfig() { return groupConfig; }
    
    /**
     * Retorna o índice próprio no grupo.
     *
     * @return inteiro representando o índice deste processo
     */
    public int getSelfIndex() { return selfIndex; }
    
    /**
     * Retorna o objeto de sincronização interna.
     *
     * @return o objeto usado como lock
     */
    public Object getLock() { return lock; }
    
    /**
     * Retorna o objeto responsável por enviar datagramas UDP.
     *
     * @return instância de UDPSender
     */
    public UDPSender getUdpSender() { return udpSender; }
}

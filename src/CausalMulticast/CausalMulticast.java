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

    public static class DelayedEntry {
        private final byte[] datagram;
        private final InetAddress destAddr;
        private final int destPort;
        private final int destIndex;
        private final String description;

        public DelayedEntry(byte[] datagram, InetAddress destAddr, int destPort, int destIndex, String description) {
            this.datagram = datagram;
            this.destAddr = destAddr;
            this.destPort = destPort;
            this.destIndex = destIndex;
            this.description = description;
        }

        public byte[] getDatagram() { return datagram; }
        public InetAddress getDestAddr() { return destAddr; }
        public int getDestPort() { return destPort; }
        public int getDestIndex() { return destIndex; }
        public String getDescription() { return description; }
    }

    private final ICausalMulticast client;
    private final GroupConfig groupConfig;
    private final int selfIndex;
    private final VectorClock vectorClock;
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

    public CausalMulticast(String ip, Integer port, ICausalMulticast client) {
        try {
            this.client = client;
            this.groupConfig = GroupConfig.load(CONFIG_PATH);
            
            this.selfIndex = groupConfig.indexOf(ip, port);
            if (this.selfIndex == -1) {
                throw new RuntimeException("Processo ip:port não encontrado no group.cfg");
            }
            
            int N = groupConfig.size();
            this.vectorClock = new VectorClock(N);
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

    public void mcsend(String msg, ICausalMulticast cliente) {
        synchronized (lock) {
            vectorClock.increment(selfIndex);
            Message m = new Message(selfIndex, vectorClock.copy(), msg);
            String serialized = m.serialize();
            byte[] data = serialized.getBytes(StandardCharsets.UTF_8);

            for (int j = 0; j < groupConfig.size(); j++) {
                if (j != selfIndex) {
                    GroupConfig.Member member = groupConfig.getMember(j);
                    try {
                        InetAddress addr = InetAddress.getByName(member.getIp());
                        
                        System.out.print("Enviar para Processo " + j + " (" + member.getIp() + ":" + member.getPort() + ")? [S/n]: ");
                        String resp = scanner.nextLine().trim();
                        
                        if (resp.equalsIgnoreCase("n")) {
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

            stabilityMatrix[selfIndex] = vectorClock.copy();
            cliente.deliver(msg);
            garbageCollect();
            displayManager.printState("ENVIO", vectorClock, stabilityMatrix, messageBuffer, delayedQueue);
        }
    }

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
                    vectorClock.set(dm.getSenderIndex(), dm.getTimestamp()[dm.getSenderIndex()]);
                    stabilityMatrix[selfIndex] = vectorClock.copy();
                    client.deliver(dm.getPayload());
                    messageBuffer.remove(dm);
                    delivered = true;
                }
            }

            garbageCollect();
            displayManager.printState("RECEPÇÃO", vectorClock, stabilityMatrix, messageBuffer, delayedQueue);
        }
    }

    private void garbageCollect() {
        List<Message> stable = messageBuffer.getStableMessages(stabilityMatrix);
        if (!stable.isEmpty()) {
            for (Message msg : stable) {
                System.out.println("[GC] Mensagem estável descartada: " + msg);
            }
            messageBuffer.removeAll(stable);
        }
    }

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

    public List<DelayedEntry> getDelayedQueue() { return delayedQueue; }
    public int getDelayedQueueSize() { return delayedQueue.size(); }
    public VectorClock getVectorClock() { return vectorClock; }
    public int[][] getStabilityMatrix() { return stabilityMatrix; }
    public MessageBuffer getMessageBuffer() { return messageBuffer; }
    public GroupConfig getGroupConfig() { return groupConfig; }
    public int getSelfIndex() { return selfIndex; }
    public Object getLock() { return lock; }
    public UDPSender getUdpSender() { return udpSender; }
}

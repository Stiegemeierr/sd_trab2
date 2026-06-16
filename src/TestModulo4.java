import CausalMulticast.CausalMulticast;
import CausalMulticast.DisplayManager;
import CausalMulticast.ICausalMulticast;

import java.util.Scanner;

/**
 * Classe de teste interativo para o Módulo 4 (Atraso de Mensagens e Estado).
 */
public class TestModulo4 implements ICausalMulticast {
    
    /**
     * Callback de entrega de mensagem do middleware.
     *
     * @param msg mensagem recebida
     */
    @Override
    public void deliver(String msg) {
        System.out.println("\n[TestModulo4] Entregue (callback disparado): " + msg);
    }

    /**
     * Ponto de entrada do teste do Módulo 4.
     *
     * @param args argumentos de linha de comando
     */
    public static void main(String[] args) {
        System.out.println("Iniciando TestModulo4...");
        
        TestModulo4 cliente = new TestModulo4();
        CausalMulticast cm;
        try {
            cm = new CausalMulticast("127.0.0.1", 7001, cliente);
        } catch (Exception e) {
            System.err.println("Erro ao iniciar CausalMulticast: " + e.getMessage());
            return;
        }

        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\n--- Menu (Processo 0) ---");
            System.out.println("1. Enviar mensagem");
            System.out.println("2. Liberar mensagem atrasada");
            System.out.println("3. Ver estado");
            System.out.println("4. Sair");
            System.out.print("Opção: ");
            
            String optStr = sc.nextLine().trim();
            int opt;
            try {
                opt = Integer.parseInt(optStr);
            } catch (NumberFormatException e) {
                continue;
            }

            if (opt == 1) {
                System.out.print("Texto da mensagem: ");
                String txt = sc.nextLine().trim();
                cm.mcsend(txt, cliente);
            } else if (opt == 2) {
                synchronized(cm.getLock()) {
                    int qtde = cm.getDelayedQueueSize();
                    if (qtde == 0) {
                        System.out.println("Nenhuma mensagem atrasada.");
                    } else {
                        System.out.println("Mensagens atrasadas:");
                        for (int i = 0; i < qtde; i++) {
                            System.out.println("[" + i + "] " + cm.getDelayedQueue().get(i).getDescription());
                        }
                        System.out.print("Índice para liberar: ");
                        try {
                            int idx = Integer.parseInt(sc.nextLine().trim());
                            cm.releaseDelayed(idx);
                        } catch (NumberFormatException e) {
                            System.out.println("Índice inválido.");
                        }
                    }
                }
            } else if (opt == 3) {
                synchronized(cm.getLock()) {
                    DisplayManager tempDisplay = new DisplayManager(cm.getSelfIndex(), "127.0.0.1", 7001);
                    tempDisplay.printState("CONSULTA MANUAL", cm.getVectorClock(), cm.getStabilityMatrix(), cm.getMessageBuffer(), cm.getDelayedQueue());
                }
            } else if (opt == 4) {
                break;
            }
        }
        System.out.println("Saindo...");
        System.exit(0);
    }
}

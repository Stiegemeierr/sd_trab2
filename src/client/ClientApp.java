import CausalMulticast.CausalMulticast;
import CausalMulticast.DisplayManager;
import CausalMulticast.ICausalMulticast;

import java.util.Scanner;

public class ClientApp implements ICausalMulticast {

    @Override
    public void deliver(String msg) {
        System.out.println(">> [ENTREGUE] " + msg);
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Uso: java ClientApp <ip> <porta>");
            System.exit(1);
        }

        String ip = args[0];
        int port;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Porta inválida: " + args[1]);
            System.exit(1);
            return;
        }

        ClientApp clientApp = new ClientApp();
        CausalMulticast cm;
        try {
            cm = new CausalMulticast(ip, port, clientApp);
        } catch (Exception e) {
            System.err.println("Erro ao inicializar middleware: " + e.getMessage());
            System.exit(1);
            return;
        }

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n╔══════════════════════════════════════╗");
            System.out.println("║  Processo " + cm.getSelfIndex() + " - " + ip + ":" + port + " ║");
            System.out.println("╠══════════════════════════════════════╣");
            System.out.println("║  1. Enviar mensagem                  ║");
            System.out.println("║  2. Liberar mensagem atrasada        ║");
            System.out.println("║  3. Ver estado                       ║");
            System.out.println("║  4. Sair                             ║");
            System.out.println("╚══════════════════════════════════════╝");
            System.out.print("Opção: ");

            String optStr = scanner.nextLine().trim();
            int opt;
            try {
                opt = Integer.parseInt(optStr);
            } catch (NumberFormatException e) {
                continue;
            }

            if (opt == 1) {
                System.out.print("Mensagem: ");
                String txt = scanner.nextLine().trim();
                cm.mcsend(txt, clientApp);
            } else if (opt == 2) {
                synchronized (cm.getLock()) {
                    if (cm.getDelayedQueueSize() == 0) {
                        System.out.println("Nenhuma mensagem atrasada.");
                    } else {
                        System.out.println("Mensagens atrasadas:");
                        for (int i = 0; i < cm.getDelayedQueueSize(); i++) {
                            System.out.println("[" + i + "] " + cm.getDelayedQueue().get(i).getDescription());
                        }
                        System.out.print("Índice para liberar (-1 para cancelar): ");
                        try {
                            int idx = Integer.parseInt(scanner.nextLine().trim());
                            if (idx == -1) {
                                continue;
                            }
                            cm.releaseDelayed(idx);
                        } catch (NumberFormatException e) {
                            System.out.println("Índice inválido.");
                        }
                    }
                }
            } else if (opt == 3) {
                synchronized (cm.getLock()) {
                    DisplayManager tempDisplay = new DisplayManager(cm.getSelfIndex(), ip, port);
                    tempDisplay.printState("CONSULTA", cm.getVectorClock(), cm.getStabilityMatrix(), cm.getMessageBuffer(), cm.getDelayedQueue());
                }
            } else if (opt == 4) {
                System.out.println("Encerrando processo...");
                System.exit(0);
            } else {
                System.out.println("Opção inválida.");
            }
        }
    }
}

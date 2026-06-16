import CausalMulticast.CausalMulticast;
import CausalMulticast.ICausalMulticast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Classe de teste para o Módulo 3 (Entrega Causal de Mensagens).
 */
public class TestModulo3 {

    /**
     * Cliente de processo simulado para testes.
     */
    static class ProcessClient implements ICausalMulticast {
        private final int id;
        public final List<String> delivered = Collections.synchronizedList(new ArrayList<>());
        public CausalMulticast cm;

        /**
         * Constrói o cliente do processo.
         *
         * @param id identificador do processo
         */
        public ProcessClient(int id) {
            this.id = id;
        }

        /**
         * Callback de entrega de mensagem do middleware.
         *
         * @param msg mensagem recebida
         */
        @Override
        public void deliver(String msg) {
            delivered.add(msg);
            System.out.println("Processo " + id + " consumiu: " + msg);
        }
    }

    /**
     * Ponto de entrada do teste do Módulo 3.
     *
     * @param args argumentos de linha de comando
     * @throws InterruptedException se a thread for interrompida durante o sleep
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Iniciando TestModulo3...");

        ProcessClient p0 = new ProcessClient(0);
        ProcessClient p1 = new ProcessClient(1);
        ProcessClient p2 = new ProcessClient(2);

        // Simula entrada do usuário para manter o CausalMulticast 100% interativo sem travar o teste
        System.setIn(new java.io.InputStream() {
            private int pos = 0;
            private final byte[] data = "S\n".getBytes();
            @Override
            public int read() {
                int b = data[pos];
                pos = (pos + 1) % data.length;
                return b;
            }
            @Override
            public int read(byte[] b, int off, int len) {
                for(int i=0; i<len; i++) {
                    b[off+i] = data[pos];
                    pos = (pos + 1) % data.length;
                }
                return len;
            }
        });

        // 3. Criar instâncias
        p0.cm = new CausalMulticast("127.0.0.1", 5001, p0);
        p1.cm = new CausalMulticast("127.0.0.1", 5002, p1);
        p2.cm = new CausalMulticast("127.0.0.1", 5003, p2);

        // 4. Aguardar inicialização
        Thread.sleep(500);

        System.out.println("\n--- CENÁRIO 1: Entrega Simples ---");
        p0.cm.mcsend("msg1", p0);
        Thread.sleep(1000);

        boolean c1Ok = p0.delivered.contains("msg1") && p1.delivered.contains("msg1") && p2.delivered.contains("msg1");
        if (c1Ok) {
            System.out.println("TESTE MÓDULO 3 - Cenário 1: OK");
        } else {
            System.out.println("TESTE MÓDULO 3 - Cenário 1: FALHOU");
        }

        p0.delivered.clear();
        p1.delivered.clear();
        p2.delivered.clear();

        System.out.println("\n--- CENÁRIO 2: Ordem Causal Preservada ---");
        // Processo 0 envia A
        p0.cm.mcsend("A", p0);
        
        // Vamos dar tempo para P1 receber A
        Thread.sleep(500);
        
        // P1 envia B, que depende de A
        p1.cm.mcsend("B", p1);
        
        Thread.sleep(1000);

        boolean c2Ok = false;
        if (p2.delivered.size() == 2) {
            if ("A".equals(p2.delivered.get(0)) && "B".equals(p2.delivered.get(1))) {
                c2Ok = true;
            }
        }
        
        if (c2Ok) {
            System.out.println("TESTE MÓDULO 3 - Cenário 2: OK");
        } else {
            System.out.println("TESTE MÓDULO 3 - Cenário 2: FALHOU");
            System.out.println("Entregas de P2: " + p2.delivered);
        }
        
        System.exit(0);
    }
}

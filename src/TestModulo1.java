import CausalMulticast.GroupConfig;
import CausalMulticast.Message;
import CausalMulticast.VectorClock;

public class TestModulo1 {
    public static void main(String[] args) {
        try {
            System.out.println(">>> Testando Message...");
            Message msg = new Message(1, new int[]{0,2,1}, "Olá mundo");
            String serialized = msg.serialize();
            if (!"1|0,2,1|Olá mundo".equals(serialized)) {
                throw new Exception("Falha em serialize: " + serialized);
            }
            
            Message d1 = Message.deserialize("1|0,2,1|Olá mundo");
            if (d1.getSenderIndex() != 1 || d1.getTimestamp()[1] != 2 || !"Olá mundo".equals(d1.getPayload())) {
                throw new Exception("Falha em deserialize");
            }
            
            Message d2 = Message.deserialize("0|1,0|msg|com|pipes");
            if (!"msg|com|pipes".equals(d2.getPayload())) {
                throw new Exception("Falha em deserialize com pipes no payload");
            }
            System.out.println("[OK] Message serializa e desserializa corretamente.");

            System.out.println(">>> Testando VectorClock...");
            VectorClock vc = new VectorClock(3);
            vc.set(0, 2);
            vc.set(1, 0);
            vc.set(2, 1); // VC local = [2, 0, 1]
            
            if (!vc.canDeliver(new int[]{2, 1, 1}, 1)) {
                throw new Exception("Falha canDeliver: deveria ser TRUE para [2, 1, 1] do sender 1");
            }
            if (vc.canDeliver(new int[]{2, 2, 1}, 1)) {
                throw new Exception("Falha canDeliver: deveria ser FALSE para [2, 2, 1] do sender 1");
            }
            if (vc.canDeliver(new int[]{3, 1, 1}, 1)) {
                throw new Exception("Falha canDeliver: deveria ser FALSE para [3, 1, 1] do sender 1");
            }
            System.out.println("[OK] VectorClock.canDeliver funciona corretamente.");

            System.out.println(">>> Testando GroupConfig...");
            GroupConfig gc = GroupConfig.load("group.cfg");
            if (gc.size() != 3) {
                throw new Exception("Tamanho do grupo não é 3");
            }
            if (!"127.0.0.1".equals(gc.getMember(0).getIp()) || gc.getMember(0).getPort() != 5001) {
                throw new Exception("IP/Porta do membro 0 incorreta");
            }
            if (gc.indexOf("127.0.0.1", 5002) != 1) {
                throw new Exception("Índice do membro 5002 incorreto");
            }
            System.out.println("[OK] GroupConfig.load le o arquivo corretamente.");

            System.out.println("\n✅ Todos os testes locais (Criterios de Aceite) passaram com sucesso!");
        } catch (Exception e) {
            System.err.println("\n❌ ERRO NOS TESTES: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

package CausalMulticast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lê o arquivo group.cfg e expõe a lista de membros do grupo.
 */
public class GroupConfig {

    /**
     * Representa um membro do grupo.
     */
    public static class Member {
        private final String ip;
        private final int port;

        /**
         * Construtor do membro do grupo.
         *
         * @param ip endereço IP
         * @param port porta
         */
        public Member(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        /**
         * Retorna o IP do membro.
         *
         * @return string contendo o IP.
         */
        public String getIp() {
            return ip;
        }

        /**
         * Retorna a porta do membro.
         *
         * @return número da porta.
         */
        public int getPort() {
            return port;
        }

        @Override
        public String toString() {
            return ip + ":" + port;
        }
    }

    private final List<Member> members;

    private GroupConfig(List<Member> members) {
        this.members = Collections.unmodifiableList(members);
    }

    /**
     * Lê o arquivo, processa as linhas válidas e retorna a instância com a lista de membros.
     *
     * @param filePath caminho do arquivo de configuração
     * @return instância de GroupConfig
     * @throws IOException se arquivo não existir, erro de I/O, ou formato inválido
     */
    public static GroupConfig load(String filePath) throws IOException {
        List<Member> parsedMembers = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int lastColon = line.lastIndexOf(':');
                if (lastColon == -1) {
                    throw new IOException("Formato inválido: ':' não encontrado na linha -> " + line);
                }

                String ip = line.substring(0, lastColon).trim();
                String portStr = line.substring(lastColon + 1).trim();

                int port;
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    throw new IOException("Porta inválida, não é um número: " + portStr);
                }

                if (port < 1 || port > 65535) {
                    throw new IOException("Porta fora do intervalo permitido (1-65535): " + port);
                }

                parsedMembers.add(new Member(ip, port));
            }
        }

        if (parsedMembers.size() < 2) {
            throw new IOException("O grupo deve ter pelo menos 2 membros. Encontrado: " + parsedMembers.size());
        }

        return new GroupConfig(parsedMembers);
    }

    /**
     * Retorna o número de membros no grupo.
     *
     * @return total de membros
     */
    public int size() {
        return members.size();
    }

    /**
     * Retorna um membro a partir de seu índice.
     *
     * @param index índice do membro (0-indexed)
     * @return objeto Member
     * @throws IndexOutOfBoundsException se índice for inválido
     */
    public Member getMember(int index) {
        return members.get(index);
    }

    /**
     * Retorna o índice do membro que possui o IP e porta especificados.
     *
     * @param ip   endereço IP
     * @param port porta
     * @return índice do membro, ou -1 se não for encontrado
     */
    public int indexOf(String ip, int port) {
        for (int i = 0; i < members.size(); i++) {
            Member m = members.get(i);
            if (m.ip.equals(ip) && m.port == port) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Retorna a lista imutável de membros.
     *
     * @return lista de membros
     */
    public List<Member> getMembers() {
        return members;
    }
}

# Prompt de Implementação — Módulo 4: Controle Interativo e Display

---

## 1. Objetivo

Adicionar ao middleware as duas funcionalidades exigidas pelo enunciado para fins de avaliação:

1. **Controle manual de envio**: antes de cada envio unicast, o middleware pergunta ao usuário se quer enviar ou atrasar a mensagem para aquele destinatário específico. Mensagens atrasadas ficam numa fila e podem ser liberadas depois.
2. **Display permanente de estado**: após cada evento, exibir no terminal o relógio vetorial, a matriz de estabilidade, o buffer de mensagens pendentes e as mensagens atrasadas.

Este módulo **modifica** o `CausalMulticast.java` existente e **cria** o `DisplayManager.java`.

---

## 2. Contexto Necessário

### Projeto
Middleware Java para comunicação multicast com ordenamento causal sobre UDP unicast. Grupo estático. Entrega causal via relógios vetoriais. Estabilização via matriz N×N. Controle manual via teclado para demonstração.

### Stack
- **Java 17 LTS** — sem bibliotecas externas.
- **Build**: `javac -encoding UTF-8`.

### Estrutura de Diretórios

```
sd_trab2/
├── group.cfg
├── src/
│   └── CausalMulticast/
│       ├── ICausalMulticast.java       # [Módulo 1 — não alterar]
│       ├── Message.java               # [Módulo 1 — não alterar]
│       ├── VectorClock.java           # [Módulo 1 — não alterar]
│       ├── GroupConfig.java           # [Módulo 1 — não alterar]
│       ├── MessageHandler.java        # [Módulo 2 — não alterar]
│       ├── UDPSender.java            # [Módulo 2 — não alterar]
│       ├── UDPReceiver.java          # [Módulo 2 — não alterar]
│       ├── MessageBuffer.java        # [Módulo 3 — não alterar]
│       ├── CausalMulticast.java      # [MODIFICAR NESTE MÓDULO]
│       └── DisplayManager.java       # [CRIAR NESTE MÓDULO]
└── bin/
```

### Convenções
- Pacote: `CausalMulticast`.
- Classes: PascalCase. Métodos/variáveis: camelCase. Constantes: UPPER_SNAKE_CASE.
- Javadoc em toda classe e método público.
- Acessos ao estado compartilhado: `synchronized(lock)`.

---

## 3. Código Existente — `CausalMulticast.java` (COMPLETO, PARA REFERÊNCIA)

Abaixo está o arquivo **inteiro** como existe hoje. Leia com atenção antes de modificar.

```java
package CausalMulticast;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Classe principal do middleware. Orquestra todos os componentes.
 */
public class CausalMulticast {
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
                        udpSender.send(data, addr, member.getPort());
                    } catch (IOException e) {
                        System.err.println("[CausalMulticast] Erro ao enviar para " + member.getIp() + ":" + member.getPort() + " - " + e.getMessage());
                    }
                }
            }

            stabilityMatrix[selfIndex] = vectorClock.copy();
            cliente.deliver(msg);
            garbageCollect();
            printState("ENVIO");
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
            printState("RECEPÇÃO");
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

    private void printState(String event) {
        System.out.println("========================================");
        System.out.println("[EVENTO: " + event + "] Processo " + selfIndex + " (" + groupConfig.getMember(selfIndex).getIp() + ":" + groupConfig.getMember(selfIndex).getPort() + ")");
        System.out.println("── Relógio Vetorial: " + vectorClock.toString());
        System.out.println("── Matriz de Estabilidade:");
        for (int i = 0; i < stabilityMatrix.length; i++) {
            System.out.println("   P" + i + ": " + Arrays.toString(stabilityMatrix[i]));
        }
        System.out.println("── Buffer (pendentes): " + messageBuffer.size());
        for (Message m : messageBuffer.getAll()) {
            System.out.println("   " + m.toString());
        }
        System.out.println("========================================");
    }

    public VectorClock getVectorClock() { return vectorClock; }
    public int[][] getStabilityMatrix() { return stabilityMatrix; }
    public MessageBuffer getMessageBuffer() { return messageBuffer; }
    public GroupConfig getGroupConfig() { return groupConfig; }
    public int getSelfIndex() { return selfIndex; }
    public Object getLock() { return lock; }
    public UDPSender getUdpSender() { return udpSender; }
}
```

---

## 4. Contratos deste Módulo

### 4.1 `DisplayManager.java` (CRIAR)

Classe utilitária para formatar e imprimir o estado do middleware no terminal.

**Campos:**

| Campo | Tipo | Visibilidade | Descrição |
|---|---|---|---|
| `selfIndex` | `int` | `private final` | Índice do processo local |
| `selfLabel` | `String` | `private final` | Label ex: `"Processo 0 - 127.0.0.1:5001"` |

**Construtor:**
```java
public DisplayManager(int selfIndex, String ip, int port)
```

**Métodos públicos:**

| Método | Assinatura | Descrição |
|---|---|---|
| `printState` | `void printState(String event, VectorClock vc, int[][] stabilityMatrix, MessageBuffer buffer, List<DelayedEntry> delayedQueue)` | Imprime o estado completo formatado. |

O parâmetro `List<DelayedEntry>` precisa da classe `DelayedEntry` — vide seção 4.2.

**Formato de saída de `printState`:**

```
========================================
[EVENTO: ENVIO] Processo 0 - 127.0.0.1:5001
── Relógio Vetorial: [2, 0, 1]
── Matriz de Estabilidade:
   P0: [2, 0, 1]
   P1: [1, 0, 0]
   P2: [0, 0, 0]
── Buffer (mensagens pendentes): 1
   [P1 | VC=[0,1,0] | "Mensagem teste"]
── Mensagens atrasadas: 2
   [0] Para P2: [P0 | VC=[1,0,0] | "msg retida"]
   [1] Para P1: [P0 | VC=[2,0,0] | "outra retida"]
========================================
```

Regras:
- Se buffer estiver vazio, imprimir `"   (vazio)"` abaixo da linha de contagem.
- Se delayed queue estiver vazia, imprimir `"   (nenhuma)"`.
- Cada entrada atrasada deve exibir um índice numérico `[i]` para o usuário poder referenciá-la ao liberar.

---

### 4.2 Modificações em `CausalMulticast.java`

#### 4.2.1 Nova classe interna `DelayedEntry`

Adicionar como classe interna estática pública:

```java
public static class DelayedEntry {
    private final byte[] datagram;
    private final InetAddress destAddr;
    private final int destPort;
    private final int destIndex;
    private final String description;

    // Construtor e getters
}
```

| Campo | Tipo | Descrição |
|---|---|---|
| `datagram` | `byte[]` | Bytes já serializados, prontos para envio |
| `destAddr` | `InetAddress` | Endereço de destino |
| `destPort` | `int` | Porta de destino |
| `destIndex` | `int` | Índice do processo destinatário |
| `description` | `String` | Representação legível para exibição (ex: `"Para P2: [P0 | VC=[1,0,0] | \"msg\"]"`) |

Getters: `getDatagram()`, `getDestAddr()`, `getDestPort()`, `getDestIndex()`, `getDescription()`.

#### 4.2.2 Novos campos em `CausalMulticast`

| Campo | Tipo | Descrição |
|---|---|---|
| `delayedQueue` | `ArrayList<DelayedEntry>` | Fila de mensagens atrasadas |
| `displayManager` | `DisplayManager` | Instância do display |
| `scanner` | `Scanner` | Para leitura do stdin (controle interativo) |

#### 4.2.3 Alterações no construtor

Após a linha `this.receiverThread.start();`, adicionar:
1. `this.delayedQueue = new ArrayList<>()`.
2. `this.displayManager = new DisplayManager(selfIndex, ip, port)`.
3. `this.scanner = new Scanner(System.in)`.

#### 4.2.4 Modificação do `mcsend`

O loop de envio interno ao `mcsend` deve ser alterado. Onde hoje envia diretamente para cada membro, deve agora:

```
para cada membro j (j != selfIndex):
    1. Exibir prompt: "Enviar para Processo j (ip:porta)? [S/n]: "
    2. Ler resposta do scanner (dentro do synchronized — ver nota)
    3. Se resposta for "n" ou "N":
       - Criar DelayedEntry com (data, addr, port, j, description)
       - Adicionar à delayedQueue
       - Imprimir: "[ATRASO] Mensagem retida para Processo j"
    4. Senão (Enter, "s", "S", ou qualquer outra coisa):
       - Enviar via udpSender.send(data, addr, port)
```

> [!IMPORTANT]
> **Sobre ler do Scanner dentro de `synchronized(lock)`**: Isso vai bloquear a thread de recepção enquanto o usuário digita. Isso é **intencional e desejado** para este trabalho acadêmico — o controle manual é justamente para permitir ao avaliador controlar o ritmo. Em produção seria inaceitável, mas aqui é o comportamento correto.

#### 4.2.5 Substituir `printState` pelo `DisplayManager`

Substituir todas as chamadas de `printState(event)` por:

```java
displayManager.printState(event, vectorClock, stabilityMatrix, messageBuffer, delayedQueue);
```

O método privado `printState(String event)` antigo deve ser **removido**.

#### 4.2.6 Novo método público `releaseDelayed`

```java
public void releaseDelayed(int index)
```

Passos (dentro de `synchronized(lock)`):
1. Validar `index` (0 ≤ index < delayedQueue.size()). Se inválido, imprimir erro em `System.err` e retornar.
2. Obter `DelayedEntry entry = delayedQueue.get(index)`.
3. Enviar: `udpSender.send(entry.getDatagram(), entry.getDestAddr(), entry.getDestPort())`.
4. Remover da fila: `delayedQueue.remove(index)`.
5. Imprimir: `System.out.println("[LIBERADO] Mensagem enviada para Processo " + entry.getDestIndex())`.
6. Imprimir estado via `displayManager`.

Tratamento de erros: capturar `IOException` do send; logar e retornar.

#### 4.2.7 Novo método público `getDelayedQueue`

```java
public List<DelayedEntry> getDelayedQueue()
```

Retorna referência direta à `delayedQueue` (chamador deve usar `synchronized(lock)`).

#### 4.2.8 Novo método público `getDelayedQueueSize`

```java
public int getDelayedQueueSize()
```

Retorna `delayedQueue.size()` — conveniência para o ClientApp (Módulo 5).

---

## 5. Classes Existentes — Contratos Exatos (NÃO MODIFICAR)

### `VectorClock` (Módulo 1)
```java
public VectorClock(int size)
public void increment(int index)
public int get(int index)
public void set(int index, int value)
public int[] copy()
public void merge(int[] other)
public boolean canDeliver(int[] msgTimestamp, int senderIndex)
public int getSize()
public String toString()    // → "[v0, v1, ..., vN-1]"
```

### `Message` (Módulo 1)
```java
public Message(int senderIndex, int[] timestamp, String payload)
public int getSenderIndex()
public int[] getTimestamp()
public String getPayload()
public String serialize()
public static Message deserialize(String raw)
public String toString()    // → "[P1 | VC=[0,2,1] | \"texto\"]"
```

### `GroupConfig` (Módulo 1)
```java
public static GroupConfig load(String filePath) throws IOException
public int size()
public Member getMember(int index)
public int indexOf(String ip, int port)
public List<Member> getMembers()
```

### `MessageBuffer` (Módulo 3)
```java
public void add(Message msg)
public List<Message> getDeliverableMessages(VectorClock vc)
public void remove(Message msg)
public void removeAll(List<Message> msgs)
public List<Message> getStableMessages(int[][] stabilityMatrix)
public List<Message> getAll()
public int size()
```

### `UDPSender` (Módulo 2)
```java
public void send(byte[] data, InetAddress address, int port) throws IOException
```

---

## 6. Restrições

### Bibliotecas permitidas
Apenas a biblioteca padrão do Java 17:
- `java.util.*` (ArrayList, List, Scanner, Arrays)
- `java.net.*` (InetAddress)
- `java.io.IOException`
- `java.nio.charset.StandardCharsets`
- **Nenhuma** biblioteca de terceiros.

### O que NÃO modificar
- Nenhum arquivo dos Módulos 1, 2 e 3 **exceto** `CausalMulticast.java`.
- NÃO alterar a assinatura do construtor `CausalMulticast(String, Integer, ICausalMulticast)`.
- NÃO alterar a assinatura de `mcsend(String, ICausalMulticast)`.
- NÃO alterar a lógica de `handleReceive` (exceto substituir `printState` por `displayManager.printState`).
- NÃO alterar a lógica de `garbageCollect` (exceto que o print de GC pode ser integrado ao display).

### O que NÃO implementar neste módulo
- **ClientApp** (vem no Módulo 5).
- **Makefile** (vem no Módulo 5).

---

## 7. Critério de Aceite

### 7.1 Compilação limpa
```
javac -encoding UTF-8 -d bin -sourcepath src src/CausalMulticast/*.java
```

### 7.2 Teste manual interativo

Criar `src/TestModulo4.java` que:

1. Sobrescreve `group.cfg` com 2 membros (portas 7001, 7002) para simplificar o teste interativo.
2. Inicia **apenas 1** instância do `CausalMulticast` na porta 7001.
3. Em um loop, oferece menu:
   ```
   --- Menu (Processo 0) ---
   1. Enviar mensagem
   2. Liberar mensagem atrasada
   3. Ver estado
   4. Sair
   Opção:
   ```
4. Opção 1: pede texto da mensagem, chama `mcsend` (o prompt de atraso aparecerá automaticamente).
5. Opção 2: lista mensagens atrasadas e pede índice para liberar.
6. Opção 3: imprime estado via display.
7. Opção 4: encerra.

**Compilar e executar:**
```
javac -encoding UTF-8 -d bin -sourcepath src src/CausalMulticast/*.java src/TestModulo4.java
java -cp bin TestModulo4
```

**Cenário de validação (manual):**
1. Iniciar o programa.
2. Escolher opção 1, digitar "teste".
3. Quando perguntar "Enviar para Processo 1?", responder "n".
4. Verificar que aparece `[ATRASO]` e a mensagem consta em "Mensagens atrasadas: 1".
5. Escolher opção 2, liberar índice 0.
6. Verificar que `[LIBERADO]` aparece e a lista de atrasadas volta a 0.

### 7.3 Verificações visuais
- O `DisplayManager` formata a saída no layout especificado (seção 4.1).
- O buffer, a matriz e o relógio são exibidos corretamente após cada evento.
- Mensagens atrasadas aparecem com índices numéricos.

---

## 8. Lembrete Final

Ao confirmar que o módulo compilou e os critérios de aceite foram atendidos, gere o **Recibo de Entrega** no formato padrão (vide instruções do agente Operário).

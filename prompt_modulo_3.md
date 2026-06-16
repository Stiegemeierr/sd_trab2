# Prompt de Implementação — Módulo 3: Ordenação Causal e Estabilização

---

## 1. Objetivo

Implementar as duas classes que compõem o coração do middleware: `MessageBuffer` (buffer de mensagens pendentes com lógica de entrega causal e detecção de estabilidade) e `CausalMulticast` (classe principal que orquestra tudo — relógio vetorial, matriz de estabilidade, envio, recepção, entrega e garbage collection).

Neste módulo, o `CausalMulticast` ainda **não** implementa controle interativo de atraso nem display formatado no terminal — esses virão nos Módulos 4 e 5. Aqui, o `mcsend` envia para **todos** os membros automaticamente (sem prompt de sim/não), e a saída de debug é feita com `System.out.println` simples.

---

## 2. Contexto Necessário

### Projeto
Middleware Java para comunicação multicast com ordenamento causal sobre UDP unicast. Grupo estático definido por `group.cfg`. Entrega causal via relógios vetoriais. Estabilização via matriz de relógios vetoriais (vetor de vetores) para garbage collection de mensagens do buffer.

### Stack
- **Java 17 LTS** — sem bibliotecas externas.
- **Build**: `javac -encoding UTF-8`.
- **Encoding**: UTF-8 em todos os `.java` e na conversão `String ↔ byte[]` dos datagramas.

### Estrutura de Diretórios (apenas o relevante)

```
sd_trab2/
├── group.cfg
├── src/
│   └── CausalMulticast/
│       ├── ICausalMulticast.java       # [Módulo 1 — já existe]
│       ├── Message.java               # [Módulo 1 — já existe]
│       ├── VectorClock.java           # [Módulo 1 — já existe]
│       ├── GroupConfig.java           # [Módulo 1 — já existe]
│       ├── MessageHandler.java        # [Módulo 2 — já existe]
│       ├── UDPSender.java            # [Módulo 2 — já existe]
│       ├── UDPReceiver.java          # [Módulo 2 — já existe]
│       ├── MessageBuffer.java        # [ESTE MÓDULO]
│       └── CausalMulticast.java      # [ESTE MÓDULO]
└── bin/
```

### Convenções
- Pacote: `CausalMulticast`.
- Classes: PascalCase. Métodos/variáveis: camelCase. Constantes: UPPER_SNAKE_CASE.
- Javadoc em toda classe e método público.
- `try-catch` local com log em `System.err`. Nunca engolir exceção silenciosamente.
- **Thread safety**: todos os acessos ao estado compartilhado (buffer, vectorClock, stabilityMatrix) devem usar `synchronized` no mesmo monitor.

---

## 3. Classes Existentes — Contratos Exatos (NÃO MODIFICAR)

As classes abaixo já existem e **não devem ser alteradas**. Estão aqui para referência de assinaturas.

### `ICausalMulticast` (Módulo 1)
```java
package CausalMulticast;
public interface ICausalMulticast {
    void deliver(String msg);
}
```

### `Message` (Módulo 1)
```java
package CausalMulticast;
public class Message {
    public Message(int senderIndex, int[] timestamp, String payload)
    public int getSenderIndex()
    public int[] getTimestamp()    // retorna cópia defensiva
    public String getPayload()
    public String serialize()      // → "senderIndex|vc0,vc1,...|payload"
    public static Message deserialize(String raw)  // throws IllegalArgumentException
    public String toString()       // → "[P1 | VC=[0,2,1] | \"texto\"]"
}
```

### `VectorClock` (Módulo 1)
```java
package CausalMulticast;
public class VectorClock {
    public VectorClock(int size)      // inicializa com zeros
    public void increment(int index)
    public int get(int index)
    public void set(int index, int value)
    public int[] copy()               // cópia defensiva
    public void merge(int[] other)    // max element-wise; throws IllegalArgumentException se tamanhos diferem
    public boolean canDeliver(int[] msgTimestamp, int senderIndex)
    public int getSize()
    public String toString()          // → "[v0, v1, ..., vN-1]"
}
```
**Regra `canDeliver`:**
- `msgTimestamp[senderIndex] == clock[senderIndex] + 1`
- `∀k ≠ senderIndex : msgTimestamp[k] ≤ clock[k]`

### `GroupConfig` (Módulo 1)
```java
package CausalMulticast;
public class GroupConfig {
    public static class Member {
        public String getIp()
        public int getPort()
        public String toString()  // → "ip:porta"
    }
    public static GroupConfig load(String filePath) throws IOException
    public int size()
    public Member getMember(int index)
    public int indexOf(String ip, int port)  // → -1 se não encontrado
    public List<Member> getMembers()         // lista imutável
}
```

### `MessageHandler` (Módulo 2)
```java
package CausalMulticast;
@FunctionalInterface
public interface MessageHandler {
    void onReceive(byte[] data, int length);
}
```

### `UDPSender` (Módulo 2)
```java
package CausalMulticast;
public class UDPSender {
    public static final int MAX_DATAGRAM_SIZE = 65507;
    public UDPSender(DatagramSocket socket)
    public void send(byte[] data, InetAddress address, int port) throws IOException
}
```

### `UDPReceiver` (Módulo 2)
```java
package CausalMulticast;
public class UDPReceiver implements Runnable {
    public UDPReceiver(DatagramSocket socket, MessageHandler handler)
    public void stop()  // seta running = false; NÃO fecha o socket
    public void run()   // loop de recepção
}
```

---

## 4. Contratos deste Módulo

### 4.1 `MessageBuffer.java`

Buffer de mensagens pendentes com lógica de consulta para entrega causal e detecção de estabilidade.

**Campos:**

| Campo | Tipo | Visibilidade | Descrição |
|---|---|---|---|
| `pending` | `ArrayList<Message>` | `private` | Mensagens recebidas aguardando entrega ou estabilização |

**Construtor:**
```java
public MessageBuffer()
```
- Inicializa `pending` como lista vazia.

**Métodos públicos:**

| Método | Assinatura | Descrição |
|---|---|---|
| `add` | `void add(Message msg)` | Adiciona mensagem ao buffer. |
| `getDeliverableMessages` | `List<Message> getDeliverableMessages(VectorClock vc)` | Retorna lista de mensagens que podem ser entregues causalmente (vide regra). |
| `remove` | `void remove(Message msg)` | Remove uma mensagem específica do buffer. |
| `removeAll` | `void removeAll(List<Message> msgs)` | Remove uma lista de mensagens do buffer. |
| `getStableMessages` | `List<Message> getStableMessages(int[][] stabilityMatrix)` | Retorna mensagens estáveis (podem ser descartadas). |
| `getAll` | `List<Message> getAll()` | Retorna cópia da lista de mensagens pendentes. |
| `size` | `int size()` | Retorna quantidade de mensagens no buffer. |

**Lógica de `getDeliverableMessages(VectorClock vc)`:**

Itera sobre `pending` e retorna todas as mensagens `m` para as quais `vc.canDeliver(m.getTimestamp(), m.getSenderIndex())` retorna `true`. Retorna uma **nova lista** (não modificar `pending` aqui).

**Lógica de `getStableMessages(int[][] stabilityMatrix)`:**

Uma mensagem `m` de sender `j` com timestamp `VT` é **estável** se:

```
∀k (0..N-1) : stabilityMatrix[k][j] ≥ VT[j]
```

Onde `N = stabilityMatrix.length` e `VT[j] = m.getTimestamp()[m.getSenderIndex()]`.

Ou seja, todos os processos já receberam pelo menos a mensagem com esse número de sequência de `j`. Retorna nova lista.

> [!IMPORTANT]
> `MessageBuffer` é uma estrutura de dados **sem sincronização própria**. A thread safety é responsabilidade do chamador (`CausalMulticast`), que usará `synchronized`.

---

### 4.2 `CausalMulticast.java`

Classe principal do middleware. Orquestra todos os componentes.

**Campos:**

| Campo | Tipo | Visibilidade | Descrição |
|---|---|---|---|
| `client` | `ICausalMulticast` | `private final` | Callback do construtor (para entregas de mensagens recebidas) |
| `groupConfig` | `GroupConfig` | `private final` | Configuração do grupo |
| `selfIndex` | `int` | `private final` | Índice deste processo no grupo |
| `vectorClock` | `VectorClock` | `private final` | Relógio vetorial local |
| `stabilityMatrix` | `int[][]` | `private final` | Matriz N×N de estabilidade |
| `messageBuffer` | `MessageBuffer` | `private final` | Buffer de mensagens pendentes |
| `udpSender` | `UDPSender` | `private final` | Componente de envio UDP |
| `udpReceiver` | `UDPReceiver` | `private final` | Componente de recepção UDP |
| `socket` | `DatagramSocket` | `private final` | Socket UDP compartilhado |
| `receiverThread` | `Thread` | `private final` | Thread do receiver |
| `lock` | `Object` | `private final` | Monitor para sincronização (`new Object()`) |
| `CONFIG_PATH` | `String` | `private static final` | `"group.cfg"` |

**Construtor:**
```java
public CausalMulticast(String ip, Integer port, ICausalMulticast client)
```

Passos do construtor:
1. Armazena `client`.
2. Carrega `GroupConfig.load(CONFIG_PATH)`.
3. Determina `selfIndex = groupConfig.indexOf(ip, port)`. Se retornar `-1`, lançar `RuntimeException("Processo ip:port não encontrado no group.cfg")`.
4. Inicializa `vectorClock = new VectorClock(groupConfig.size())`.
5. Inicializa `stabilityMatrix = new int[N][N]` (tudo zero), onde `N = groupConfig.size()`.
6. Inicializa `messageBuffer = new MessageBuffer()`.
7. Cria `socket = new DatagramSocket(port, InetAddress.getByName(ip))`.
8. Cria `udpSender = new UDPSender(socket)`.
9. Cria `udpReceiver = new UDPReceiver(socket, this::handleReceive)` — o method reference `this::handleReceive` implementa `MessageHandler`.
10. Cria thread: `receiverThread = new Thread(udpReceiver, "UDPReceiver")`.
11. `receiverThread.setDaemon(true)`.
12. `receiverThread.start()`.
13. Imprime: `System.out.println("[CausalMulticast] Processo " + selfIndex + " (" + ip + ":" + port + ") iniciado.")`.

**Métodos públicos:**

### `mcsend(String msg, ICausalMulticast cliente)`

```java
public void mcsend(String msg, ICausalMulticast cliente)
```

Passos (todos dentro de `synchronized(lock)`):
1. `vectorClock.increment(selfIndex)`.
2. `Message m = new Message(selfIndex, vectorClock.copy(), msg)`.
3. `String serialized = m.serialize()`.
4. `byte[] data = serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8)`.
5. Para cada membro `j` do grupo onde `j != selfIndex`:
   - Obter `Member member = groupConfig.getMember(j)`.
   - `InetAddress addr = InetAddress.getByName(member.getIp())`.
   - `udpSender.send(data, addr, member.getPort())`.
   - (Neste módulo, envia para todos sem prompt interativo — o Módulo 4 adicionará o controle de atraso.)
6. Atualizar `stabilityMatrix[selfIndex] = vectorClock.copy()` (o remetente sabe seu próprio estado).
7. Entrega local: `cliente.deliver(msg)` — usar o parâmetro `cliente` do `mcsend`, não o `this.client`.
8. Executar garbage collection de estabilidade (vide seção abaixo).
9. Imprimir estado: `printState("ENVIO")`.

Tratamento de erros: capturar `IOException` no envio de cada membro individualmente; logar em `System.err` e continuar com os demais membros.

### `handleReceive(byte[] data, int length)`

Método **privado** que implementa `MessageHandler`. Chamado pela thread do `UDPReceiver`.

```java
private void handleReceive(byte[] data, int length)
```

Passos (todos dentro de `synchronized(lock)`):
1. Converter bytes para String: `String raw = new String(data, 0, length, StandardCharsets.UTF_8)`.
2. `Message m = Message.deserialize(raw)` — envolver em try-catch; se falhar, logar e retornar.
3. `messageBuffer.add(m)`.
4. Atualizar `stabilityMatrix[m.getSenderIndex()]`: fazer merge element-wise com `m.getTimestamp()`:
   ```
   for each i (0..N-1):
       stabilityMatrix[m.getSenderIndex()][i] = Math.max(stabilityMatrix[m.getSenderIndex()][i], m.getTimestamp()[i])
   ```
5. Executar **loop de entrega causal** (ponto fixo):
   ```
   boolean delivered = true;
   while (delivered) {
       delivered = false;
       List<Message> deliverable = messageBuffer.getDeliverableMessages(vectorClock);
       for (Message dm : deliverable) {
           // Atualizar vectorClock: set posição do sender para o valor do timestamp
           vectorClock.set(dm.getSenderIndex(), dm.getTimestamp()[dm.getSenderIndex()]);
           // Atualizar linha self da matriz de estabilidade
           stabilityMatrix[selfIndex] = vectorClock.copy();
           // Entregar ao cliente
           client.deliver(dm.getPayload());
           // Remover do buffer
           messageBuffer.remove(dm);
           delivered = true;
       }
   }
   ```
6. Executar garbage collection de estabilidade.
7. Imprimir estado: `printState("RECEPÇÃO")`.

### Garbage Collection de Estabilidade

Método privado reutilizado em `mcsend` e `handleReceive`:

```java
private void garbageCollect()
```

Passos (chamado já dentro de `synchronized(lock)`):
1. `List<Message> stable = messageBuffer.getStableMessages(stabilityMatrix)`.
2. Se `stable` não estiver vazia:
   - Para cada mensagem estável, logar: `System.out.println("[GC] Mensagem estável descartada: " + msg)`.
   - `messageBuffer.removeAll(stable)`.

### `printState(String event)`

Método privado para debug (temporário — será substituído pelo `DisplayManager` no Módulo 4):

```java
private void printState(String event)
```

Imprimir:
```
========================================
[EVENTO: event] Processo selfIndex (ip:port)
── Relógio Vetorial: vectorClock.toString()
── Matriz de Estabilidade:
   P0: [stabilityMatrix[0] formatado]
   P1: [stabilityMatrix[1] formatado]
   ...
── Buffer (pendentes): messageBuffer.size()
   [listar cada mensagem no buffer com toString()]
========================================
```

### Métodos de acesso para o Módulo 4 (getters protegidos/públicos)

O Módulo 4 precisará acessar o estado interno para o `DisplayManager` e para a lógica de delay. Expor os seguintes getters públicos:

| Método | Retorno | Descrição |
|---|---|---|
| `getVectorClock()` | `VectorClock` | Referência ao relógio vetorial |
| `getStabilityMatrix()` | `int[][]` | Referência à matriz de estabilidade |
| `getMessageBuffer()` | `MessageBuffer` | Referência ao buffer |
| `getGroupConfig()` | `GroupConfig` | Referência à config do grupo |
| `getSelfIndex()` | `int` | Índice do processo local |
| `getLock()` | `Object` | Monitor de sincronização (para que o Módulo 4 sincronize no mesmo lock) |
| `getUdpSender()` | `UDPSender` | Referência ao sender (para liberar mensagens atrasadas) |

> [!IMPORTANT]
> Esses getters existem para permitir composição no Módulo 4. A sincronização continua sendo feita via `synchronized(lock)` em ambos os lados.

---

## 5. Restrições

### Bibliotecas permitidas
Apenas a biblioteca padrão do Java 17:
- `java.net.*` (DatagramSocket, InetAddress)
- `java.nio.charset.StandardCharsets`
- `java.util.*` (ArrayList, List, Arrays)
- `java.io.IOException`
- **Nenhuma** biblioteca de terceiros.

### Padrão de tratamento de erros
- `IOException` no construtor (socket, config): propagar como `RuntimeException` encapsulada.
- `IOException` em `mcsend` (envio individual): capturar, logar em `System.err`, continuar com demais membros.
- `IllegalArgumentException` em `handleReceive` (deserialize): capturar, logar em `System.err`, retornar sem processar.
- Nunca `e.printStackTrace()` sozinho.

### O que NÃO implementar neste módulo
- **Controle interativo de atraso** (prompt "Enviar para Processo j? [S/n]") — vem no Módulo 4.
- **Fila de mensagens atrasadas** (`delayedQueue` / `DelayedEntry`) — vem no Módulo 4.
- **`DisplayManager`** — vem no Módulo 4. Usar `printState()` simples por enquanto.
- **`ClientApp`** — vem no Módulo 5.

### O que NÃO modificar
- Nenhum arquivo dos Módulos 1 e 2. Se algo parecer errado, pare e pergunte.

---

## 6. Critério de Aceite

O módulo está pronto quando:

### 6.1 Compilação limpa
Todos os arquivos (Módulos 1 + 2 + 3) compilam sem erros:
```
javac -encoding UTF-8 -d bin -sourcepath src src/CausalMulticast/*.java
```

### 6.2 Teste com 3 processos

Criar `src/TestModulo3.java` (default package) que simula 3 processos **no mesmo JVM** (cada um em uma thread), usando portas diferentes no localhost:

**Setup:**
1. Criar `group.cfg` com 3 membros: `127.0.0.1:6001`, `127.0.0.1:6002`, `127.0.0.1:6003`.
2. Cada "processo" é um objeto que implementa `ICausalMulticast` e armazena as mensagens entregues em uma `List<String>`.
3. Criar 3 instâncias de `CausalMulticast` (uma por porta).
4. Aguardar 500ms para todas as instâncias iniciarem.

**Cenário 1 — Entrega simples:**
1. Processo 0 envia "msg1".
2. Aguardar 1 segundo.
3. Verificar: todos os 3 processos receberam "msg1" via `deliver()`.

**Cenário 2 — Ordem causal preservada:**
1. Processo 0 envia "A".
2. Aguardar 500ms.
3. Processo 1 envia "B" (que causalmente depende de "A", pois 1 já recebeu "A").
4. Aguardar 1 segundo.
5. Verificar: no Processo 2, "A" foi entregue **antes** de "B".

**Saída:**
- Imprimir `"TESTE MÓDULO 3 - Cenário 1: OK/FALHOU"`
- Imprimir `"TESTE MÓDULO 3 - Cenário 2: OK/FALHOU"`

**Compilar e executar:**
```
javac -encoding UTF-8 -d bin -sourcepath src src/CausalMulticast/*.java src/TestModulo3.java
java -cp bin TestModulo3
```

### 6.3 Verificações de estado
No output de `printState`, confirmar visualmente que:
- O relógio vetorial incrementa corretamente a cada envio.
- A matriz de estabilidade atualiza ao receber mensagens.
- O buffer esvazia quando todas as condições de entrega causal são satisfeitas.
- Mensagens estáveis são reportadas pelo garbage collector (quando todos os processos tiverem trocado mensagens suficientes).

---

## 7. Lembrete Final

Ao confirmar que o módulo compilou, os testes passaram e os critérios de aceite foram atendidos, gere o **Recibo de Entrega** no formato padrão (vide instruções do agente Operário).

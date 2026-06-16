# Prompt de Implementação — Módulo 2: Transporte UDP

---

## 1. Objetivo

Implementar a camada de transporte UDP do middleware `CausalMulticast`: uma classe para envio de datagramas unicast (`UDPSender`) e uma classe para recepção contínua em thread separada (`UDPReceiver`), além da interface funcional de callback (`MessageHandler`). Essas classes encapsulam todo o acesso a `java.net.DatagramSocket`/`DatagramPacket`, isolando o resto do middleware dos detalhes de rede.

---

## 2. Contexto Necessário

### Projeto
Middleware Java para comunicação multicast com ordenamento causal, usando **UDP unicast** como transporte. O grupo de participantes é estático. Mensagens são serializadas como strings delimitadas por `|`.

### Stack
- **Java 17 LTS** — sem bibliotecas externas.
- **Build**: `javac -encoding UTF-8` (compilação direta, sem Maven/Gradle).
- **Encoding**: UTF-8 em todos os arquivos `.java` e na codificação de datagramas.

### Estrutura de Diretórios (apenas o relevante)

```
sd_trab2/
├── src/
│   └── CausalMulticast/
│       ├── ICausalMulticast.java       # [Módulo 1 — já existe]
│       ├── Message.java               # [Módulo 1 — já existe]
│       ├── VectorClock.java           # [Módulo 1 — já existe]
│       ├── GroupConfig.java           # [Módulo 1 — já existe]
│       ├── UDPSender.java            # [ESTE MÓDULO]
│       ├── UDPReceiver.java          # [ESTE MÓDULO]
│       └── MessageHandler.java       # [ESTE MÓDULO]
└── bin/
```

### Convenções
- Pacote: `CausalMulticast`.
- Classes: PascalCase. Métodos/variáveis: camelCase. Constantes: UPPER_SNAKE_CASE.
- Javadoc (`/** */`) em toda classe e método público.
- Tratamento de erros: `try-catch` local com log em `System.err`. Nunca engolir exceção silenciosamente.

### Formato de serialização das mensagens (referência — você NÃO implementa isso, já existe)

As mensagens chegam prontas como `String` serializada no formato:
```
senderIndex|vc0,vc1,...,vcN-1|payload
```
A classe `Message` (Módulo 1) já possui `serialize()` e `deserialize()`. O transporte trabalha com **bytes brutos** (`byte[]`) — a conversão `String ↔ byte[]` será feita pelas camadas superiores (Módulo 3). O `UDPSender` e o `UDPReceiver` lidam apenas com `byte[]`.

---

## 3. Contratos

### 3.1 `MessageHandler.java`

Interface funcional de callback usada pelo `UDPReceiver` para notificar a camada superior quando um datagrama chega.

```java
package CausalMulticast;

/**
 * Interface funcional de callback para recepção de datagramas UDP.
 * Implementada pela classe CausalMulticast (Módulo 3) para processar
 * mensagens recebidas.
 */
@FunctionalInterface
public interface MessageHandler {

    /**
     * Invocado pelo UDPReceiver quando um datagrama é recebido.
     *
     * @param data   bytes do datagrama recebido (já recortado no tamanho real)
     * @param length número de bytes válidos no array
     */
    void onReceive(byte[] data, int length);
}
```

Este é o arquivo completo.

---

### 3.2 `UDPSender.java`

Responsável por enviar datagramas UDP unicast.

**Campos:**

| Campo | Tipo | Visibilidade | Descrição |
|---|---|---|---|
| `socket` | `DatagramSocket` | `private final` | Socket UDP compartilhado (recebido via construtor) |
| `MAX_DATAGRAM_SIZE` | `int` | `public static final` | `65507` — tamanho máximo do payload UDP |

**Construtor:**
```java
public UDPSender(DatagramSocket socket)
```
- Recebe o socket já criado pela camada superior (o `CausalMulticast` criará um único `DatagramSocket` que será compartilhado entre `UDPSender` e `UDPReceiver`).
- Não criar socket novo aqui.

**Métodos públicos:**

| Método | Assinatura | Descrição |
|---|---|---|
| `send` | `void send(byte[] data, InetAddress address, int port) throws IOException` | Envia um datagrama UDP unicast para o endereço e porta especificados. |

**Comportamento de `send`:**
1. Validar que `data.length` não excede `MAX_DATAGRAM_SIZE`. Se exceder, lançar `IllegalArgumentException` com mensagem descritiva.
2. Criar `DatagramPacket(data, data.length, address, port)`.
3. Chamar `socket.send(packet)`.
4. Não capturar `IOException` — propagar para o chamador decidir como tratar.

---

### 3.3 `UDPReceiver.java`

Thread receptora que escuta datagramas UDP continuamente e repassa via callback.

**Campos:**

| Campo | Tipo | Visibilidade | Descrição |
|---|---|---|---|
| `socket` | `DatagramSocket` | `private final` | Socket UDP compartilhado |
| `handler` | `MessageHandler` | `private final` | Callback para processar datagramas recebidos |
| `running` | `volatile boolean` | `private` | Flag de controle do loop de recepção |
| `MAX_DATAGRAM_SIZE` | `int` | `private static final` | `65507` |

**Construtor:**
```java
public UDPReceiver(DatagramSocket socket, MessageHandler handler)
```
- Armazena referências ao socket e ao handler.
- Inicializa `running = true`.

**Interface implementada:** `Runnable`

**Métodos públicos:**

| Método | Assinatura | Descrição |
|---|---|---|
| `run` | `void run()` | Loop principal de recepção (executado em thread separada). |
| `stop` | `void stop()` | Seta `running = false` para encerrar o loop graciosamente. |

**Comportamento de `run()`:**

```
enquanto running == true:
    1. Criar buffer: byte[MAX_DATAGRAM_SIZE]
    2. Criar DatagramPacket com o buffer
    3. Chamar socket.receive(packet)  — bloqueia até receber
    4. Extrair os bytes recebidos: Arrays.copyOf(packet.getData(), packet.getLength())
    5. Chamar handler.onReceive(receivedBytes, receivedBytes.length)
```

**Tratamento de erros no loop:**
- Se `socket.receive()` lançar `IOException` **e** `running` for `true`: logar em `System.err` com `"[UDPReceiver] Erro ao receber datagrama: " + e.getMessage()` e **continuar** o loop (não crashar por um erro transiente).
- Se `running` for `false` (ou o socket estiver fechado): sair do loop silenciosamente (é o encerramento normal).

**Nota sobre threading:**
- Esta classe NÃO cria a thread por conta própria. Ela implementa `Runnable`.
- A camada superior (Módulo 3) fará:
  ```java
  UDPReceiver receiver = new UDPReceiver(socket, handler);
  Thread receiverThread = new Thread(receiver, "UDPReceiver");
  receiverThread.setDaemon(true);
  receiverThread.start();
  ```
- A thread deve ser **daemon** para não bloquear o encerramento da JVM.

---

## 4. Restrições

### Bibliotecas permitidas
Apenas a biblioteca padrão do Java 17:
- `java.net.DatagramSocket`
- `java.net.DatagramPacket`
- `java.net.InetAddress`
- `java.io.IOException`
- `java.util.Arrays`
- **Nenhuma** biblioteca de terceiros.

### Padrão de tratamento de erros
- `UDPSender.send()`: propagar `IOException` (não capturar).
- `UDPReceiver.run()`: capturar `IOException` no loop, logar e continuar se `running == true`.
- Validações de parâmetros: `IllegalArgumentException` com mensagem descritiva.

### O que NÃO implementar neste módulo
- Serialização/desserialização de mensagens (já existe no Módulo 1).
- Conversão `String ↔ byte[]` (responsabilidade do Módulo 3).
- Qualquer lógica de relógios vetoriais, buffer ou entrega causal.
- Criação do `DatagramSocket` (será feita no Módulo 3).
- Criação da `Thread` (será feita no Módulo 3).

---

## 5. Critério de Aceite

O módulo está pronto quando:

1. **Compilação limpa** de todo o projeto (Módulos 1 + 2) sem erros nem warnings:
   ```
   javac -encoding UTF-8 -d bin -sourcepath src src/CausalMulticast/ICausalMulticast.java src/CausalMulticast/Message.java src/CausalMulticast/VectorClock.java src/CausalMulticast/GroupConfig.java src/CausalMulticast/MessageHandler.java src/CausalMulticast/UDPSender.java src/CausalMulticast/UDPReceiver.java
   ```

2. **Teste funcional** — criar um script de teste `src/TestModulo2.java` (fora do pacote `CausalMulticast`, no default package) que:
   - Cria um `DatagramSocket` na porta 9999.
   - Instancia `UDPReceiver` com um `MessageHandler` que imprime os bytes recebidos como String UTF-8.
   - Inicia o receiver em uma thread daemon.
   - Instancia `UDPSender` com o mesmo socket.
   - Envia uma mensagem de teste (`"0|1,0,0|Hello UDP"`) para `localhost:9999` via `UDPSender`.
   - Aguarda 500ms para o receiver processar.
   - Verifica que o handler recebeu a mensagem.
   - Chama `receiver.stop()` e fecha o socket.
   - Imprime `"TESTE MÓDULO 2: OK"` se tudo funcionar, ou `"TESTE MÓDULO 2: FALHOU"` caso contrário.

   Compilar e executar com:
   ```
   javac -encoding UTF-8 -d bin -sourcepath src src/CausalMulticast/*.java src/TestModulo2.java
   java -cp bin TestModulo2
   ```

3. **Validação de tamanho**: `UDPSender.send()` com array maior que 65507 bytes deve lançar `IllegalArgumentException`.

4. **Stop gracioso**: após `receiver.stop()`, o loop de recepção deve encerrar sem exceções visíveis ao usuário.

---

## 6. Lembrete Final

Ao confirmar que o módulo compilou, os testes passaram e os critérios de aceite foram atendidos, gere o **Recibo de Entrega** no formato padrão (vide instruções do agente Operário).

# Prompt de Implementação — Módulo 5: Cliente de Teste, Build e Documentação

---

## 1. Objetivo

Finalizar o middleware com três entregas:

1. **`ClientApp.java`** — aplicação cliente de teste que demonstra todas as funcionalidades do middleware (envio, atraso, liberação, display). Esta é a aplicação que o professor vai **substituir** pelo código dele na avaliação, então ela serve para demonstração e teste próprio.
2. **`Makefile`** — automatiza compilação, execução, geração de Javadoc e limpeza.
3. **Javadoc** — geração da documentação a partir dos comentários Javadoc já existentes nos módulos anteriores.
4. **`group.cfg`** — restaurar para 3 membros localhost (5001, 5002, 5003) como configuração padrão de demonstração.

---

## 2. Contexto Necessário

### Projeto
Middleware Java (`CausalMulticast`) para comunicação multicast com ordenamento causal sobre UDP unicast. Grupo estático. O middleware já está 100% funcional — este módulo apenas cria a aplicação cliente, o sistema de build e a documentação.

### Stack
- **Java 17 LTS** — sem bibliotecas externas.
- **Build**: `javac -encoding UTF-8` + `Makefile`.
- **Documentação**: Javadoc.

### Estrutura de Diretórios Completa (estado final)

```
sd_trab2/
├── def.md                              # Enunciado original
├── 00_especificacao_mestre.md          # Especificação Mestre
├── Makefile                            # [ESTE MÓDULO]
├── group.cfg                           # [RESTAURAR NESTE MÓDULO]
├── src/
│   ├── CausalMulticast/
│   │   ├── ICausalMulticast.java       # [já existe]
│   │   ├── CausalMulticast.java        # [já existe]
│   │   ├── Message.java               # [já existe]
│   │   ├── VectorClock.java           # [já existe]
│   │   ├── MessageBuffer.java         # [já existe]
│   │   ├── UDPSender.java            # [já existe]
│   │   ├── UDPReceiver.java          # [já existe]
│   │   ├── MessageHandler.java        # [já existe]
│   │   ├── GroupConfig.java           # [já existe]
│   │   └── DisplayManager.java        # [já existe]
│   ├── client/
│   │   └── ClientApp.java             # [ESTE MÓDULO]
│   ├── TestModulo1.java
│   ├── TestModulo2.java
│   ├── TestModulo3.java
│   └── TestModulo4.java
├── bin/                                # Classes compiladas
└── doc/                                # Javadoc gerado
```

### Convenções
- Pacote do middleware: `CausalMulticast`.
- O `ClientApp` fica no pacote `client` **ou** no default package. Recomendo **default package** para facilitar a execução simples (`java -cp bin ClientApp ip porta`).
- Javadoc em toda classe e método público.
- Flag `-encoding UTF-8` obrigatória no `javac`.

---

## 3. Classes Existentes — API do Middleware (NÃO MODIFICAR)

### `CausalMulticast`
```java
package CausalMulticast;

// Construtor
public CausalMulticast(String ip, Integer port, ICausalMulticast client)

// Enviar mensagem multicast (interativo — pede confirmação para cada destinatário)
public void mcsend(String msg, ICausalMulticast cliente)

// Liberar mensagem atrasada pelo índice na delayed queue
public void releaseDelayed(int index)

// Getters
public List<CausalMulticast.DelayedEntry> getDelayedQueue()
public int getDelayedQueueSize()
public VectorClock getVectorClock()
public int[][] getStabilityMatrix()
public MessageBuffer getMessageBuffer()
public GroupConfig getGroupConfig()
public int getSelfIndex()
public Object getLock()
public UDPSender getUdpSender()
```

### `CausalMulticast.DelayedEntry`
```java
public static class DelayedEntry {
    public byte[] getDatagram()
    public InetAddress getDestAddr()
    public int getDestPort()
    public int getDestIndex()
    public String getDescription()
}
```

### `ICausalMulticast`
```java
package CausalMulticast;
public interface ICausalMulticast {
    void deliver(String msg);
}
```

### `DisplayManager`
```java
package CausalMulticast;
public class DisplayManager {
    public DisplayManager(int selfIndex, String ip, int port)
    public void printState(String event, VectorClock vc, int[][] stabilityMatrix, MessageBuffer buffer, List<CausalMulticast.DelayedEntry> delayedQueue)
}
```

### `GroupConfig`
```java
public static GroupConfig load(String filePath) throws IOException
public int size()
public Member getMember(int index)
// Member: getIp(), getPort(), toString()
```

---

## 4. Contratos deste Módulo

### 4.1 `ClientApp.java` (CRIAR)

Aplicação cliente interativa que roda no terminal. **Default package** (sem declaração de `package`), no diretório `src/client/`.

> [!IMPORTANT]
> O professor vai substituir este arquivo pelo código de teste dele. Portanto:
> - A API do middleware (`CausalMulticast`, `ICausalMulticast`) deve ser a única interface usada.
> - O `ClientApp` deve ser um bom exemplo de como usar o middleware.

**Execução:**
```
java -cp bin ClientApp <ip> <porta>
```

Exemplo: `java -cp bin ClientApp 127.0.0.1 5001`

**Comportamento:**

1. Recebe `ip` e `porta` como argumentos da linha de comando.
2. Implementa `ICausalMulticast` (o próprio `ClientApp` implementa a interface).
3. Cria instância: `CausalMulticast cm = new CausalMulticast(ip, port, this)`.
4. Exibe menu em loop:

```
╔══════════════════════════════════════╗
║  Processo <selfIndex> - <ip>:<porta> ║
╠══════════════════════════════════════╣
║  1. Enviar mensagem                  ║
║  2. Liberar mensagem atrasada        ║
║  3. Ver estado                       ║
║  4. Sair                             ║
╚══════════════════════════════════════╝
Opção:
```

5. **Opção 1 — Enviar mensagem:**
   - Prompt: `"Mensagem: "`
   - Lê a mensagem do Scanner.
   - Chama `cm.mcsend(texto, this)`.
   - O prompt interativo de atraso (S/n para cada destinatário) aparecerá automaticamente dentro do `mcsend`.

6. **Opção 2 — Liberar mensagem atrasada:**
   - Se `cm.getDelayedQueueSize() == 0`: imprimir `"Nenhuma mensagem atrasada."` e voltar ao menu.
   - Senão: listar as mensagens atrasadas com seus índices (acessar via `synchronized(cm.getLock())`).
   - Prompt: `"Índice para liberar (-1 para cancelar): "`
   - Se índice válido: `cm.releaseDelayed(indice)`.
   - Se `-1`: voltar ao menu.

7. **Opção 3 — Ver estado:**
   - Dentro de `synchronized(cm.getLock())`, criar uma instância temporária de `DisplayManager` e chamar `printState("CONSULTA", ...)`.
   - Ou mais simples: expor um método no middleware. **Decisão**: usar o `DisplayManager` com os getters expostos.

8. **Opção 4 — Sair:**
   - Imprimir `"Encerrando processo..."`.
   - `System.exit(0)`.

**Método `deliver` (callback):**
```java
@Override
public void deliver(String msg) {
    System.out.println(">> [ENTREGUE] " + msg);
}
```

Simples — imprime a mensagem entregue. O professor pode substituir por lógica mais complexa.

**Tratamento de erros:**
- Se argumentos insuficientes: imprimir `"Uso: java ClientApp <ip> <porta>"` e sair com código 1.
- Se porta inválida: imprimir erro e sair.
- Exceções do middleware: capturar no `main`, imprimir e sair.

---

### 4.2 `group.cfg` (RESTAURAR)

Restaurar o conteúdo para a configuração padrão de demonstração com 3 processos:

```
# Configuração do grupo CausalMulticast
# Formato: ip:porta (uma por linha, 0-indexed)
127.0.0.1:5001
127.0.0.1:5002
127.0.0.1:5003
```

---

### 4.3 `Makefile` (CRIAR)

O Makefile deve funcionar tanto no **Linux/macOS** quanto no **Windows** (via `make` do MinGW/GnuWin32 ou WSL). Usar variáveis para flexibilidade.

**Variáveis:**

| Variável | Valor | Descrição |
|---|---|---|
| `JAVAC` | `javac` | Compilador |
| `JAVA` | `java` | Runtime |
| `JAVADOC` | `javadoc` | Gerador de documentação |
| `SRC_DIR` | `src` | Diretório fonte |
| `BIN_DIR` | `bin` | Diretório de saída |
| `DOC_DIR` | `doc` | Diretório de Javadoc |
| `ENCODING` | `UTF-8` | Encoding |

**Targets:**

| Target | Comando | Descrição |
|---|---|---|
| `all` | Depende de `compile` | Target padrão |
| `compile` | `javac -encoding UTF-8 -d bin -sourcepath src src/CausalMulticast/*.java src/client/ClientApp.java` | Compila tudo |
| `run` | `java -cp bin ClientApp $(IP) $(PORT)` | Executa o cliente. Uso: `make run IP=127.0.0.1 PORT=5001` |
| `run1` | `java -cp bin ClientApp 127.0.0.1 5001` | Atalho para processo 0 |
| `run2` | `java -cp bin ClientApp 127.0.0.1 5002` | Atalho para processo 1 |
| `run3` | `java -cp bin ClientApp 127.0.0.1 5003` | Atalho para processo 2 |
| `javadoc` | `javadoc -encoding UTF-8 -d doc -sourcepath src -subpackages CausalMulticast` | Gera Javadoc |
| `clean` | Remove `bin/` e `doc/` | Limpa artefatos |
| `test1` | Compila e roda `TestModulo1` | Roda teste módulo 1 |
| `test2` | Compila e roda `TestModulo2` | Roda teste módulo 2 |
| `test3` | Compila e roda `TestModulo3` | Roda teste módulo 3 |

> [!NOTE]
> Os targets `run1`, `run2`, `run3` existem para facilitar abrir 3 terminais e rodar um processo em cada um, que é o cenário padrão de demonstração/avaliação.

---

## 5. Restrições

### Bibliotecas permitidas
Apenas a biblioteca padrão do Java 17:
- `java.util.Scanner`
- `CausalMulticast.*` (o middleware)
- **Nenhuma** biblioteca de terceiros.

### O que NÃO modificar
- **Nenhum** arquivo dentro de `src/CausalMulticast/`. Todos os 10 arquivos do middleware estão finalizados.
- Não alterar os arquivos `TestModuloN.java`.

### O que NÃO implementar
- Nenhuma funcionalidade nova no middleware.
- Não criar GUI.

---

## 6. Critério de Aceite

### 6.1 Compilação limpa
```
make clean
make compile
```
Sem erros nem warnings.

### 6.2 Javadoc gerado
```
make javadoc
```
- Diretório `doc/` criado com `index.html` funcional.
- Todas as classes do pacote `CausalMulticast` aparecem documentadas.

### 6.3 Teste com 3 processos em terminais separados

Abrir **3 terminais** no diretório `sd_trab2/`. Executar:

- Terminal 1: `make run1`
- Terminal 2: `make run2`
- Terminal 3: `make run3`

**Cenário de demonstração:**

1. **Terminal 1** (Processo 0): Enviar mensagem "Hello" → confirmar envio para todos (S, S).
2. Verificar que **Terminal 2** e **Terminal 3** exibem `>> [ENTREGUE] Hello`.
3. **Terminal 2** (Processo 1): Enviar "World" → atrasar para Processo 2 (S para P0, N para P2).
4. Verificar que **Terminal 1** recebe "World" mas **Terminal 3** não.
5. **Terminal 2**: Liberar a mensagem atrasada (opção 2, índice 0).
6. Verificar que **Terminal 3** agora recebe "World".
7. Verificar em todos os terminais que:
   - Relógios vetoriais incrementam corretamente.
   - Matriz de estabilidade reflete o conhecimento de cada processo.
   - Buffer esvazia após entregas causais.

### 6.4 Verificação da API
Confirmar que o `ClientApp` usa **apenas** a API pública do middleware (`CausalMulticast`, `ICausalMulticast`, `CausalMulticast.DelayedEntry`), sem acessar classes internas diretamente (exceto via getters já expostos).

---

## 7. Lembrete Final

Ao confirmar que o módulo compilou, o Javadoc foi gerado e o cenário de 3 processos funcionou, gere o **Recibo de Entrega** no formato padrão (vide instruções do agente Operário).

**Este é o último módulo.** O Recibo de Entrega fecha o projeto.

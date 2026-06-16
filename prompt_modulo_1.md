# Prompt de Implementação — Módulo 1: Núcleo — Estruturas de Dados

---

## 1. Objetivo

Implementar as 4 classes fundamentais do pacote `CausalMulticast` que serão usadas por todos os módulos subsequentes: a interface de callback (`ICausalMulticast`), a representação de mensagem (`Message`), o relógio vetorial (`VectorClock`) e o leitor de configuração de grupo (`GroupConfig`). Nenhuma dessas classes depende de qualquer outra parte do projeto — são puramente estruturais.

---

## 2. Contexto Necessário

### Projeto
Middleware Java para comunicação multicast com ordenamento causal, usando UDP unicast como transporte. O grupo de participantes é estático (definido por arquivo de configuração). O middleware garante entrega causal via relógios vetoriais e implementa estabilização via matriz de relógios para descarte de mensagens do buffer.

### Stack
- **Java 17 LTS** — sem bibliotecas externas.
- **Build**: `javac` (compilação direta). Não usar Maven/Gradle.
- **Encoding**: UTF-8 em todos os arquivos `.java`.

### Estrutura de Diretórios (apenas o relevante para este módulo)

```
sd_trab2/
├── group.cfg                           # Arquivo de configuração do grupo
├── src/
│   └── CausalMulticast/
│       ├── ICausalMulticast.java       # [ESTE MÓDULO]
│       ├── Message.java               # [ESTE MÓDULO]
│       ├── VectorClock.java           # [ESTE MÓDULO]
│       └── GroupConfig.java           # [ESTE MÓDULO]
└── bin/                                # Destino dos .class compilados
```

### Convenções
- Pacote: `CausalMulticast` (PascalCase — obrigatório, o professor importa via `import CausalMulticast.*;`).
- Classes: PascalCase.
- Métodos/variáveis: camelCase.
- Constantes: UPPER_SNAKE_CASE.
- Javadoc (`/** */`) em **toda** classe pública e **todo** método público.
- Tratamento de erros: `try-catch` local com log em `System.err`. Nunca engolir exceção silenciosamente.

---

## 3. Contratos

### 3.1 `ICausalMulticast.java`

Interface que o cliente do middleware deve implementar para receber mensagens.

```java
package CausalMulticast;

/**
 * Interface de callback para recebimento de mensagens do middleware.
 * Clientes do pacote CausalMulticast devem implementar esta interface.
 */
public interface ICausalMulticast {

    /**
     * Callback invocado pelo middleware quando uma mensagem está pronta
     * para entrega (respeitando a ordem causal).
     *
     * @param msg conteúdo textual da mensagem entregue
     */
    void deliver(String msg);
}
```

Este é o arquivo completo — não adicionar nada além disso.

---

### 3.2 `Message.java`

Representa uma mensagem com metadados para ordenação causal.

**Campos (todos `private final`):**

| Campo | Tipo | Descrição |
|---|---|---|
| `senderIndex` | `int` | Índice do remetente na lista de membros do grupo (0-indexed) |
| `timestamp` | `int[]` | Cópia do vetor de relógio no momento do envio |
| `payload` | `String` | Conteúdo textual da mensagem |

**Construtor:**
```java
public Message(int senderIndex, int[] timestamp, String payload)
```
- Deve armazenar uma **cópia defensiva** do array `timestamp` (não manter referência ao original).

**Métodos públicos:**

| Método | Retorno | Descrição |
|---|---|---|
| `getSenderIndex()` | `int` | Retorna o índice do remetente |
| `getTimestamp()` | `int[]` | Retorna **cópia** do vetor de relógio (cópia defensiva) |
| `getPayload()` | `String` | Retorna o conteúdo da mensagem |
| `serialize()` | `String` | Serializa no formato de protocolo (vide abaixo) |
| `deserialize(String raw)` | `static Message` | Desserializa a partir do formato de protocolo |
| `toString()` | `String` | Representação legível para debug/display |

**Formato de serialização (usado em datagramas UDP):**

```
senderIndex|vc0,vc1,...,vcN-1|payload
```

- Separador principal: `|` (pipe).
- **Regra de parsing**: separar apenas nos **dois primeiros** `|`. Tudo após o segundo `|` é payload (o payload pode conter `|`).
- `senderIndex`: inteiro.
- `vc0,vc1,...`: inteiros separados por vírgula.
- `payload`: string UTF-8.

**Exemplo** (3 processos, sender=1, VC=[0,2,1], payload="Olá mundo"):
```
1|0,2,1|Olá mundo
```

**`toString()`** deve retornar algo como:
```
[P1 | VC=[0,2,1] | "Olá mundo"]
```

**Tratamento de erros em `deserialize`:**
- Se o formato for inválido (menos de 2 pipes, índice não-numérico, VC malformado), lançar `IllegalArgumentException` com mensagem descritiva.

---

### 3.3 `VectorClock.java`

Relógio vetorial de tamanho N (número de processos no grupo).

**Campos:**

| Campo | Tipo | Visibilidade | Descrição |
|---|---|---|---|
| `clock` | `int[]` | `private` | Vetor de tamanho N, inicializado com zeros |
| `size` | `int` | `private final` | Tamanho do vetor (N) |

**Construtor:**
```java
public VectorClock(int size)
```
- Inicializa `clock` com `new int[size]` (todos zeros).

**Métodos públicos:**

| Método | Assinatura | Descrição |
|---|---|---|
| `increment` | `void increment(int index)` | Incrementa `clock[index]` em 1. Lançar `IndexOutOfBoundsException` se índice inválido. |
| `get` | `int get(int index)` | Retorna `clock[index]`. Lançar `IndexOutOfBoundsException` se índice inválido. |
| `set` | `void set(int index, int value)` | Define `clock[index] = value`. |
| `copy` | `int[] copy()` | Retorna cópia do array interno (`Arrays.copyOf`). |
| `merge` | `void merge(int[] other)` | `clock[i] = Math.max(clock[i], other[i])` para todo `i`. Lançar `IllegalArgumentException` se `other.length != size`. |
| `canDeliver` | `boolean canDeliver(int[] msgTimestamp, int senderIndex)` | Retorna `true` se a mensagem pode ser entregue causalmente (vide regra abaixo). |
| `getSize` | `int getSize()` | Retorna `size`. |
| `toString` | `String toString()` | Formato: `[v0, v1, ..., vN-1]` |

**Regra de entrega causal (`canDeliver`):**

Uma mensagem com timestamp `VT` do sender `j` pode ser entregue se e somente se:

1. `VT[j] == clock[j] + 1`  — é a **próxima** mensagem esperada de `j`.
2. `∀k ≠ j : VT[k] ≤ clock[k]`  — já recebemos tudo que `j` havia visto antes de enviar.

Se qualquer condição falhar, retornar `false`.

---

### 3.4 `GroupConfig.java`

Lê o arquivo `group.cfg` e expõe a lista de membros do grupo.

**Classe interna `Member`:**

```java
public static class Member {
    private final String ip;
    private final int port;

    // Construtor, getters, toString
}
```

**Campos de `GroupConfig`:**

| Campo | Tipo | Descrição |
|---|---|---|
| `members` | `List<Member>` (imutável após carregamento) | Lista ordenada de membros |

**Construtor:** privado (factory method `load`).

**Métodos públicos:**

| Método | Assinatura | Descrição |
|---|---|---|
| `load` | `static GroupConfig load(String filePath)` | Lê o arquivo, retorna instância. Lançar `IOException` se arquivo não existir ou formato inválido. |
| `size` | `int size()` | Retorna número de membros. |
| `getMember` | `Member getMember(int index)` | Retorna membro no índice dado. |
| `indexOf` | `int indexOf(String ip, int port)` | Retorna o índice do membro com o ip:porta dado, ou `-1` se não encontrado. |
| `getMembers` | `List<Member> getMembers()` | Retorna lista imutável de todos os membros. |

**Formato do `group.cfg`:**

```
# Comentário (linhas começando com #)
# Linhas em branco são ignoradas
# Formato: ip:porta
127.0.0.1:5001
127.0.0.1:5002
127.0.0.1:5003
```

**Regras de parsing:**
- Ignorar linhas em branco e linhas começando com `#` (após trim).
- Cada linha válida: `ip:porta` — separar no **último** `:` (para compatibilidade futura com IPv6, embora não seja requisito agora).
- Porta deve ser inteiro válido entre 1 e 65535. Se não for, lançar `IOException` com mensagem descritiva.
- Mínimo 2 membros no grupo (se menos, lançar `IOException`).
- O índice do processo é determinado pela ordem das linhas (0-indexed): primeira linha = processo 0, segunda = processo 1, etc.

---

## 4. Restrições

### Bibliotecas permitidas
Apenas as da biblioteca padrão do Java 17:
- `java.util.*` (Arrays, List, ArrayList, Collections)
- `java.io.*` (BufferedReader, FileReader, IOException)
- `java.nio.file.*` (se preferir para leitura de arquivo)
- **Nenhuma** biblioteca de terceiros.

### Padrão de tratamento de erros
- Validações de parâmetros: lançar exceções unchecked (`IllegalArgumentException`, `IndexOutOfBoundsException`) com mensagem clara.
- Erros de I/O (`GroupConfig.load`): propagar `IOException`.
- Nunca usar `e.printStackTrace()` sozinho — sempre incluir mensagem contextual em `System.err` antes, ou propagar a exceção.

### Estilo
- Javadoc em toda classe e método público.
- Cópias defensivas em `Message` (construtor e `getTimestamp()`) e em `VectorClock.copy()`.
- Campos imutáveis onde possível (`private final`).
- Sem getters/setters automáticos — apenas os listados nos contratos.

---

## 5. Critério de Aceite

O módulo está pronto quando:

1. **Compilação limpa**: todos os 4 arquivos compilam sem erros nem warnings com:
   ```
   javac -d bin -sourcepath src src/CausalMulticast/ICausalMulticast.java src/CausalMulticast/Message.java src/CausalMulticast/VectorClock.java src/CausalMulticast/GroupConfig.java
   ```

2. **`Message` serializa/desserializa corretamente**:
   - `new Message(1, new int[]{0,2,1}, "Olá mundo").serialize()` → `"1|0,2,1|Olá mundo"`
   - `Message.deserialize("1|0,2,1|Olá mundo")` → Message com senderIndex=1, timestamp=[0,2,1], payload="Olá mundo"
   - Payload com pipe: `Message.deserialize("0|1,0|msg|com|pipes")` → payload = `"msg|com|pipes"`

3. **`VectorClock.canDeliver` funciona**:
   - VC local = [2, 0, 1], mensagem de sender 1 com VT=[2, 1, 1] → `true` (próxima de 1, e ∀k≠1: VT[k]≤VC[k])
   - VC local = [2, 0, 1], mensagem de sender 1 com VT=[2, 2, 1] → `false` (VT[1]=2, esperava VC[1]+1=1)
   - VC local = [2, 0, 1], mensagem de sender 1 com VT=[3, 1, 1] → `false` (VT[0]=3 > VC[0]=2)

4. **`GroupConfig.load` lê o arquivo corretamente**:
   - Com o `group.cfg` de exemplo (3 membros), `size()` retorna 3, `getMember(0)` retorna ip=127.0.0.1/port=5001, `indexOf("127.0.0.1", 5002)` retorna 1.
   - Linhas de comentário e linhas em branco são ignoradas.
   - Arquivo com menos de 2 membros lança `IOException`.

5. **Arquivo `group.cfg` de exemplo** criado na raiz do projeto com 3 membros localhost (portas 5001, 5002, 5003).

---

## 6. Lembrete Final

Ao confirmar que o módulo compilou e os critérios de aceite foram atendidos, gere o **Recibo de Entrega** no formato padrão (vide instruções do agente Operário).

# README CausalMulticast Middleware
---

## Arquivos do Projeto

### Middleware (`src/CausalMulticast/`)

| Arquivo | Módulo | Função |
|---|---|---|
| `ICausalMulticast.java` | 1 | Interface de callback `deliver(String msg)` |
| `Message.java` | 1 | Serialização/desserialização (`senderIndex\|vc0,vc1,...\|payload`) |
| `GroupConfig.java` | 1 | Leitura do `group.cfg` estático |
| `MessageHandler.java` | 2 | Interface funcional `onReceive(byte[], int)` |
| `UDPSender.java` | 2 | Envio de datagramas unicast |
| `UDPReceiver.java` | 2 | Thread daemon de recepção contínua |
| `MessageBuffer.java` | 3 | Buffer com consultas de entrega causal e estabilidade |
| `CausalMulticast.java` | 3+4 | Orquestrador: VC, matriz N×N, entrega, GC, delay interativo |
| `DisplayManager.java` | 4 | Formatação de estado no terminal |

### Aplicação e Build

| Arquivo | Módulo | Função |
|---|---|---|
| `src/client/ClientApp.java` | 5 | Cliente interativo (menu terminal) |
| `Makefile` | 5 | Build, execução, Javadoc, testes |
| `group.cfg` | 1 | Config de grupo (3 membros localhost) |

---

## Como Usar

### Compilar
```bash
make compile
```

### Executar (3 terminais)
```bash
# Terminal 1:
make run1

# Terminal 2:
make run2

# Terminal 3:
make run3
```
---

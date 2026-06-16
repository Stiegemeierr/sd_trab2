# Especificação do Trabalho II - ELC1018 Sistemas Distribuídos

## 1. Objetivo Geral

* O trabalho consiste em programar um middleware Java, e a atividade deve ser desenvolvida em dupla.
* A principal função do middleware é oferecer comunicação multicast com ordenamento causal de mensagens.
* O sistema deve possuir um mecanismo de estabilização para realizar o descarte de mensagens do buffer.
* Esse controle de estabilização deve ser baseado em um vetor de vetor de relógios lógicos.

## 2. Arquitetura e Comunicação

* O middleware deve ser construído como um único pacote Java chamado `CausalMulticast`.
* O uso de RMI (Remote Method Invocation) não é permitido neste trabalho.
* A comunicação multicast deve ser implementada na base por meio do envio de mensagens unicast não confiáveis utilizando sockets UDP.
* A especificação original exigia um Serviço de Descoberta via IP multicast que deveria permanecer sempre ativo para atualização dinâmica dos membros. **(Nota: Esta regra foi flexibilizada pelo comentário do professor, vide seção 6)**.

## 3. API do Middleware

A API que o pacote `CausalMulticast` deve expor consiste nos seguintes componentes:

* **Construtor:** `public CausalMulticast(String ip, Integer port, ICausalMulticast client)`.
* **Envio de mensagens:** O método `public void mcsend(String msg, ICausalMulticast cliente)` permite que os usuários enviem mensagens multicast obedecendo ao ordenamento causal. O parâmetro `cliente` (ou `this`) repassa a referência do objeto do usuário para possibilitar callbacks.
* **Recebimento de mensagens:** Os usuários do pacote devem importar o middleware com `import CausalMulticast.*;` e implementar a interface `ICausalMulticast`. Essa interface obriga a implementação do método `public void deliver(String msg);`, que funciona como callback para a entrega das mensagens.



## 4. Testes, Controle e Interface

* Não é necessário implementar uma Interface Gráfica de Usuário (GUI); a aplicação pode rodar no terminal.
* O conteúdo atual do buffer de mensagens e o estado dos relógios lógicos devem ser permanentemente demonstrados na tela do terminal.
* O envio de cada mensagem unicast deve possuir controle manual via teclado para possibilitar a correção do trabalho.
* Antes de cada envio, o sistema deve questionar o usuário se a mensagem deve ser enviada para todos os destinos ou não.
* Se a escolha for "não", o sistema deve permitir o atraso de uma mensagem específica para um destinatário escolhido pelo avaliador, permitindo o seu envio/entrega apenas posteriormente (ex: em um grupo de três processos, escolher um deles para ter a mensagem retida).

## 5. Algoritmos e Base Teórica

* Os algoritmos implementados devem utilizar vetores de relógios lógicos para a ordenação causal e a estabilização.
* É sugerida a utilização dos algoritmos do artigo *"Fundamentals of Distributed Computing: A Practical Tour of Vector Clock Systems"*.

## 6. Alterações e Flexibilizações (Comentário do Professor)

De acordo com o aviso recente do professor, as seguintes simplificações foram aplicadas sobre as regras originais:

* **Grupos Estáticos:** Não é necessário implementar a descoberta e a manutenção de grupos dinâmicos. O grupo de participantes pode ser estático desde a inicialização.
* **Sem Entrada/Saída:** O sistema não precisa tratar cenários onde processos saem e depois reentram no grupo. O foco deve ser garantir a ordem causal apenas com os membros já estabelecidos no grupo.
* **Simulação de Atraso Simplificada:** Para demonstrar o algoritmo e o ordenamento, a exigência se resume a conseguir bloquear manualmente um envio (atrasar a mensagem para um membro) e desbloqueá-lo no futuro.

## 7. Avaliação e Entrega

* O middleware desenvolvido será rigorosamente testado utilizando um código cliente criado pelo próprio professor, portanto, a assinatura da API deve ser respeitada.
* A entrega final deve incluir o código-fonte completo e a documentação gerada em formato Oxigen ou JavaDoc.
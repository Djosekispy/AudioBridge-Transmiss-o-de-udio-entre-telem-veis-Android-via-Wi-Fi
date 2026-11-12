# AudioBridge 📻⚡

**AudioBridge** é uma aplicação Android nativa desenvolvida em **Kotlin** e **Jetpack Compose** que permite transmitir o áudio de um telemóvel para outro através da rede Wi-Fi local ou Hotspot, em tempo real e com baixíssima latência (< 100 ms).

Com a **AudioBridge**, qualquer telemóvel Android sobresselente pode ser transformado numa **coluna de som sem fios**, num **amplificador de áudio** ou num **sistema Walkie-Talkie / Megafone**.

---

## 🌟 Principais Funcionalidades

- **Dois Modos numa Única App**:
  - **Modo Emissor (Sender)**: Captura o áudio interno do sistema (músicas, jogos, vídeos) ou o microfone do dispositivo e envia pela rede.
  - **Modo Receptor (Receiver)**: Recebe os pacotes de áudio via rede UDP e reproduz em tempo real nas colunas ou auscultadores do dispositivo.
- **Selecção de Fonte de Áudio**:
  - **Áudio do Sistema**: Transmite qualquer som a tocar no dispositivo via Android `AudioPlaybackCaptureConfiguration`.
  - **Microfone (Voz)**: Transforma o telemóvel num microfone / megafone sem fios em tempo real.
- **Descoberta Automática de Canais (mDNS / NSD)**:
  - **Modo Público**: O receptor anuncia a sua presença na rede Wi-Fi local via `NsdManager` com um nome de canal personalizável (ex: *"Coluna da Sala"*).
  - **Modo Privado**: O receptor fica oculto na rede e aceita apenas ligações através de IP direto.
  - **Aba de Canais**: Permite aos emissores visualizar e ligar a qualquer receptor público na rede com 1 toque.
- **Gerador de Tom de Teste (440Hz)**:
  - Sinal sonoro contínuo integrado para verificar instantaneamente a ligação de rede e as colunas, mesmo sem reproduzir música externa.
- **Desempenho & Baixa Latência**:
  - Algoritmo de **Jitter Buffer** para reordenação de pacotes e supressão de falhas com silêncio.
  - Reprodução nativa via `AudioTrack` em modo `PERFORMANCE_MODE_LOW_LATENCY`.
  - Serviços de Foreground persistentes com `WakeLock` e `WifiLock` para continuar a funcionar com o ecrã desligado.
- **Interface Responsiva e Minimalista**:
  - Construída com **Material 3** e adaptável a telemóveis, dobráveis e tablets (layout automático de 2 colunas em ecrãs largos via `BoxWithConstraints`).

---

## 🛠️ Arquitectura Técnica

- **Linguagem**: Kotlin
- **Interface**: Jetpack Compose (Material 3)
- **SDK Mínimo**: API 29 (Android 10)
- **SDK Alvo**: API 34+
- **Formato de Áudio**: PCM 16-bit, 44100 Hz, Estéreo
- **Protocolo de Rede**: UDP DatagramSocket
- **Estrutura dos Pacotes UDP**:
  - **Cabeçalho (8 bytes)**: 4 bytes (Número de Sequência) + 4 bytes (Timestamp em ms)
  - **Payload PCM**: 3528 bytes por bloco (~20 ms de áudio)
  - **Tamanho Total do Pacote**: 3536 bytes

---

## 📱 Requisitos

1. Dois dispositivos com **Android 10 (API 29)** ou superior.
2. Ambos os dispositivos ligados à **mesma rede Wi-Fi** ou um ligado ao **Hotspot Wi-Fi** criado pelo outro.
3. Permissão de gravação de áudio (`RECORD_AUDIO`) e captura de ecrã (`MediaProjection`) no Emissor.

---

## 🚀 Como Compilar e Executar

1. Clone o repositório:
   ```bash
   git clone https://github.com/Djosekispy/AudioBridge-Transmiss-o-de-udio-entre-telem-veis-Android-via-Wi-Fi.git
   ```
2. Abra o projecto no **Android Studio (Ladybug ou mais recente)**.
3. Ligue os dois dispositivos Android via USB ou Wi-Fi ADB.
4. Execute o build no Gradle:
   ```bash
   ./gradlew assembleDebug
   ```
5. Instale a aplicação nos dois dispositivos.

---

## 📖 Instruções de Utilização

### Conectar por Descoberta Automática (Recomendado)
1. **No Telemóvel B (Receptor)**:
   - Abra a app na aba **RECEPTOR**.
   - Mantenha a opção **Modo Público (Anunciar IP)** activa e introduza o nome do canal (ex: *"Coluna Quarto"*).
   - Prima **OUVIR**.
2. **No Telemóvel A (Emissor)**:
   - Abra a app na aba **CANAIS**.
   - Prima **LIGAR** ao lado do canal do Telemóvel B.
   - Prima **EMITIR** na aba Emissor para iniciar a transmissão em tempo real.

### Conectar por IP Directo (Modo Privado)
1. No **Receptor**, anote o endereço IP mostrado no ecrã (ex: `192.168.1.45`).
2. No **Emissor**, cole ou introduza o IP no campo **IP do Receptor** e prima **EMITIR**.

---

## ⚡ Dicas para Minimizar a Latência

- **Utilize Wi-Fi de 5 GHz ou Hotspot Directo**: A banda de 5 GHz reduz drasticamente o *jitter*. Criar um Hotspot directo no telemóvel emissor reduz a latência para menos de 40 ms.
- **Desative o Bluetooth**: Em alguns dispositivos, a antenna partilhada Wi-Fi/Bluetooth pode causar pequenos atrasos no tráfego UDP.

---

## 🔒 Protecção de Conteúdos (DRM)

Algumas aplicações (como Netflix, aplicações bancárias ou chamadas telefónicas) bloqueiam nativamente a captura de áudio pelo sistema operativo Android por razões de segurança. Nesses casos, a transmissão emitirá silêncio por limitação do próprio sistema Android.

---

## 📄 Licença

Este projecto está licenciado sob a licença MIT - veja o ficheiro [LICENSE](LICENSE) para mais detalhes.

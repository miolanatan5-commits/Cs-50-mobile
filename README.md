# CS50 Mobile

Ambiente de desenvolvimento para fazer o CS50x no celular, pensado para TalkBack:
terminal Linux (PRoot), editor de texto acessível e botão de compilar e rodar.

## Abas
- **Terminal**: shell bash real dentro de um Ubuntu arm64 (PRoot). Saída em lista legível,
  cartão "Última saída" anunciado automaticamente (dá para desligar), Ctrl+C, Ctrl+D, Limpar, Reiniciar.
  Botão **Instalar ferramentas CS50** roda o comando `cs50-setup` (clang, make, gdb, python3, sqlite3,
  libcs50, check50, submit50, style50, help50). Leva vários minutos e cerca de 1 GB.
- **Arquivos**: pasta `workspace` (no terminal: `/root/workspace`). Criar arquivo/pasta, abrir, apagar
  (ação personalizada do TalkBack ou diálogo de confirmação).
- **Editor** (abre ao tocar num arquivo): ler linha, linha anterior/próxima, ir para linha,
  símbolos por extenso, botão Tab, recuo automático, salvamento automático a cada 2 s.
- **Compilar e rodar**: `.c` → `make nome && ./nome`; `.py` → `python3 arquivo`. Erros do clang e
  tracebacks do Python viram uma lista; tocar num item leva à linha.

- **GitHub**: gera uma chave SSH (porta 443), configura o git, copia a chave pública, abre a página
  de chaves do GitHub e testa a conexão. Também dispara `check50` e `submit50` numa pasta do workspace.
  Antes do primeiro envio, entre uma vez em https://submit.cs50.io com o GitHub.

## Idiomas
Inglês (padrão, `res/values`) e português (`res/values-pt`). Os scripts `cs50-setup` e `cs50-github`
recebem `pt` ou `en` e falam o idioma do aparelho.

## Release
`app/proguard-rules.pro` cobre Application/Activity/Service, ViewModels, enums e a reflexão do PID.
Para assinar, coloque `release.keystore` e `release.properties` na raiz; sem eles usa a chave de debug.

## Primeiro uso
1. Abra o app e toque em "Baixar e instalar" (baixa o Ubuntu Base uma vez, ~400 MB).
2. No Terminal, toque em "Instalar ferramentas CS50".
3. Crie `hello.c` em Arquivos e use "Compilar e rodar".

## Estrutura
```
app/src/main/java/com/natam/cs50mobile/
  core/runtime   instalação do rootfs (download retomável, extração tar.gz)
  core/process   PRoot: montagem de comandos e processo gerenciado
  core/terminal  sessão, buffer de linhas, detecção de prompt
  core/files     arquivos do workspace
  core/editor    texto por linha, símbolos, recuo automático
  core/build     compilar/rodar e parser de erros
  core/cs50      instalador (assets/cs50-setup.sh)
  service        serviço em primeiro plano que mantém o processo vivo
  ui             telas Compose
app/src/main/jniLibs/arm64-v8a  libproot.so e libproot-loader.so
```
Veja THIRD_PARTY_NOTICES.md para o PRoot.

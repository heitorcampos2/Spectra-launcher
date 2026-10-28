# Spectra Launcher 3 — por Heitor Campos

Desenvolvedor: **Heitor Campos** ([@heitorcampos2](https://github.com/heitorcampos2))

<p align="center">
  <a href="https://github.com/heitorcampos2/Spectra-launcher/releases/latest/download/SpectraLauncher.apk">
    <img alt="Baixar APK" src="https://img.shields.io/badge/⬇%20Baixar%20APK-Spectra%20Launcher-8B5CF6?style=for-the-badge&logo=android&logoColor=white" height="56">
  </a>
  <br>
  <a href="https://github.com/heitorcampos2/Spectra-launcher/releases/latest"><img alt="Versão" src="https://img.shields.io/github/v/release/heitorcampos2/Spectra-launcher?label=vers%C3%A3o&color=8B5CF6"></a>
  <a href="https://github.com/heitorcampos2/Spectra-launcher/actions/workflows/build.yml"><img alt="Build" src="https://github.com/heitorcampos2/Spectra-launcher/actions/workflows/build.yml/badge.svg"></a>
</p>

> **Instalar:** baixe o `SpectraLauncher.apk` pelo botão acima (no navegador da TV, no celular ou no PC) e instale.
> Depois disso, atualize pelo próprio app: **MENU ☰ › Atualizações › Atualizar agora**.
> Todas as versões ficam em [Releases](https://github.com/heitorcampos2/Spectra-launcher/releases).

## Novidades da 3.1
- **Controle remoto pelo celular**: na TV, MENU ☰ › *Parear celular (QR code)*. Defina um PIN e escaneie o QR code com o app **Spectra Controle** no celular (mesma rede Wi-Fi). Tem direcional com deslizar, OK (segure para opções), voltar, início, menu, mídia, volume (inclusive pelos botões de volume do celular), teclado para digitar na TV e lista de apps para abrir.
  - Sem o app no celular? A câmera comum lê o QR e abre o controle no navegador.
  - Para controlar também outros apps (YouTube, Netflix…), ative **"Spectra — Botão Home e controle"** em Acessibilidade na TV.
- **Atualizações pelo GitHub**: Configurações › Atualizações (ou MENU ☰ › Atualizações) verifica, baixa e instala a versão mais recente quando você quiser. Cada mudança no projeto gera uma versão nova automaticamente.

## Novidades da v3
- **Sistema de canais**: MENU › Editar canais. "Ver a seguir" (continuar assistindo de todos os apps) e cada canal de cada app (Sintonia, SmartTube, Crunchyroll, Globoplay…) vira uma fileira própria. OK mostra/oculta e segurar OK configura.
- **Configuração lateral por seção** (⚙ nos cabeçalhos à esquerda ou segurar OK num cartão): nome, ícone, visível, grade, linha deslizante, exibir nome, tamanho P/M/G/GG, estilo, ordenar, mover a seção, gerenciar aplicativos (mostrar/ocultar, **tamanho individual** e **posição** de cada cartão) e definir como padrão.
- **Cabeçalhos à esquerda**: ícones das seções com ⚙ configurar e ⇅ mover (↑/↓).
- Informações do item focado logo abaixo da fileira (app, título, duração, "T5 E25 · Continuar de 0:13").
- **Cores**: grade com 32 cores, luminosidade, opacidade e cor dinâmica; **fundo** em cor sólida ou degradê; tom dos painéis (Índigo, AMOLED, Grafite, Vinho, Azul-noite, Floresta, Vidro).
- **Fonte**: família, negrito, itálico e tamanho do texto.
- **Modo ocioso**: depois de X minutos mostra só o wallpaper e um relógio grande.


## Novidades da v2
- Seções editáveis (criar, renomear, ícone, nome visível ou não, estilo/tamanho por seção, ordenar, excluir) — Painel MENU › Editar seções
- Seção **TV** automática (Sintonia, YouTube, IPTV, SmartTube, Crunchyroll, Netflix, Prime Video, Spotify, Play Store…)
- Mover apps: segure OK › Mover › ◀ ▶ › OK. Adicionar/remover apps de qualquer seção
- Seção de **Entradas** (HDMI, AV, Cast) com nomes editáveis e modo de exemplo para apresentação
- Barra de status: busca, Wi-Fi, notificações com contador e painel, engrenagem, bateria, relógio e avatar com nome
- Painel lateral pela tecla **MENU (☰)**
- Configurações com **prévia ao vivo em 3D**; opacidade das fileiras, foco fixo, ocultar fileiras acima, margens, espaçamentos
- Wallpaper GIF e **vídeo**, troca automática, "Compartilhar → Usar como wallpaper do Spectra"
- Backup/restauração de todas as configurações

Launcher para **Android TV 11** (funciona do Android 8 ao 14), feito em Kotlin + Jetpack Compose e inspirado no Projectivy.

## Principais recursos
- **Seções por app na tela inicial**: cada app compatível (SmartTube, Crunchyroll, Sintonia, YouTube, Netflix, Prime Video, Globoplay, Plex…) ganha um bloco com **Continuar assistindo**, **Recomendados** e **Canais**. Tudo é lido do TvProvider do Android TV e se atualiza sozinho.
- Você pode ligar ou desligar cada seção por app, reordenar os apps e esconder o que não quiser.
- **Wallpapers**:
  - categorias prontas (Anime, Dark/AMOLED, Espaço, Natureza, Cyberpunk, Games, Fantasia, Animais…);
  - **busca no Google Imagens** só com imagens grandes e um botão **"Selecionar (alta qualidade)"**, que baixa a imagem original;
  - modo navegador como alternativa;
  - imagens de arquivo ou pendrive e histórico dos wallpapers salvos.
- **Fundo em movimento**: parallax que acompanha o foco, movimento suave contínuo, partículas, escurecimento ajustável, desfoque leve e cor de destaque tirada do wallpaper.
- **Título personalizável**: texto, emoji e ícones exclusivos desenhados em vetor (gato, pug, leão, tigre e dragão).
- **Cartões**: banner 16:9, ícone redondo ou pôster, em 3 tamanhos, com arredondamento ajustável.
- **Desempenho**: modos Máximo desempenho, Equilibrado e Máxima qualidade (4K).
- **Launcher padrão**: pergunta ao abrir se deve virar a tela inicial. No Google TV usa a substituição do botão Home por acessibilidade, como o Projectivy.
- Ao segurar OK ou apertar MENU num app: favoritar, reordenar, ocultar, ver informações ou desinstalar.

## Como compilar
1. No Android Studio: **File › Open** e escolha a pasta `SpectraLauncher`.
2. Espere o Gradle sincronizar (baixa AGP 8.1.0, Kotlin 2.0.20 e Compose).
3. **Build › Build APK(s)**, ou o Run (▶) com a TV conectada por ADB.
4. A versão release (`assembleRelease`) já sai assinada com a chave de debug, pronta para instalar.

## Instalar e liberar permissões pela TV ou por ADB
```
adb connect IP_DA_TV
adb install -r app-release.apk
adb shell pm grant com.hazagou.spectra com.android.providers.tv.permission.READ_EPG_DATA
adb shell cmd notification allow_listener com.hazagou.spectra/com.hazagou.spectra.service.SpectraNotificationService
# Google TV (substituir botão Home):
adb shell settings put secure enabled_accessibility_services com.hazagou.spectra/com.hazagou.spectra.service.HomeOverrideService
adb shell settings put secure accessibility_enabled 1
```
> Na build de debug o pacote é `com.hazagou.spectra.debug`.

## Observações
- As seções só aparecem para apps que publicam canais ou "Continuar assistindo" no sistema. Abra o app uma vez e assista algo.
- A busca do Google lê a página de resultados. Ela continua funcionando sem chave, mas, se o Google mudar o layout, use o **Modo navegador**. A chave da Custom Search API é opcional (a Google vai encerrar essa API em 2027).

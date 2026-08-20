# AutomBot Security

App Android nativo de antifurto/segurança de aparelho. Fica rodando em segundo
plano observando tentativas de desbloqueio da tela; ao atingir o número
configurado de erros seguidos, tira uma foto pela câmera frontal, toca um
alarme e envia um e-mail para o dono do aparelho com a foto e a localização
aproximada de quem tentou acessar.

## Como funciona (base atual)

1. **`SecurityDeviceAdminReceiver`** — usa a API oficial de Device Admin do
   Android (`onPasswordFailed()` / `onPasswordSucceeded()`) para saber quando
   alguém errou o PIN/senha/padrão da tela de bloqueio. Não precisa de
   Accessibility Service.
2. **`PrefsManager`** — guarda as configurações: quantos erros disparam o
   alerta (padrão: 2), e-mail de destino, e credenciais SMTP do remetente.
3. Ao atingir o limite, o `SecurityDeviceAdminReceiver` sobe o
   **`SecurityMonitorService`** (foreground service), que:
   - toca o alarme (`AlarmHelper`)
   - tira a foto pela câmera frontal (`CameraCaptureHelper`, via CameraX)
   - pega a localização atual (`LocationHelper`)
   - manda tudo por e-mail (`EmailSender`, SMTP direto do aparelho)
4. **`MainActivity`** — liga/desliga a proteção, pede as permissões
   necessárias e ativa o app como administrador do dispositivo. Também tem o
   botão "tocar alarme" para achar o aparelho perdido dentro de casa.
5. **`SettingsActivity`** — configura o limite de tentativas e os dados de
   e-mail/SMTP.
6. **`BootReceiver`** — religa o monitoramento depois que o aparelho reinicia.

## Antes de compilar

- Abra a pasta no Android Studio (ele gera o `gradlew`/wrapper automaticamente
  na primeira sincronização — não incluí o wrapper binário aqui).
- Ícones: coloquei um ícone adaptativo vetorial simples (escudo verde) só
  para o projeto compilar; troque por uma identidade visual definitiva depois.
- Para enviar e-mail via Gmail, o usuário final precisa gerar uma **senha de
  app** em `myaccount.google.com/apppasswords` (a senha normal da conta não
  funciona com SMTP direto) — já deixei um aviso disso na tela de
  configurações.

## Pontos de atenção / decisões tomadas

- Contador de tentativas zera automaticamente quando a senha certa é digitada
  (`onPasswordSucceeded`), e também é resetado ~2s depois de reagir a uma
  intrusão — para não reenviar e-mail a cada tentativa subsequente. Isso é
  fácil de ajustar depois se você preferir outro comportamento (ex.: parar de
  reagir só depois de X minutos).
- `ACCESS_BACKGROUND_LOCATION` já está no manifesto; em Android 10+ o sistema
  vai pedir essa permissão separadamente ("permitir o tempo todo") — a base
  ainda não trata esse fluxo específico na UI, só pede a localização
  "enquanto em uso". Ajustar quando for testar em aparelho real.
- Envio de e-mail é feito direto do aparelho via SMTP (biblioteca
  `com.sun.mail:android-mail`). Funciona para uma base/MVP; se no futuro vocês
  quiserem integrar com o **AutomBot Core** (VPS) para centralizar isso, dá
  pra trocar o `EmailSender` por uma chamada HTTP para o backend.

## Próximos passos (não incluídos nesta base, conforme combinado)

- Fluxo dedicado de permissão de localização em segundo plano (Android 10+)
- Criptografar a senha SMTP salva localmente (hoje é `SharedPreferences`
  simples)
- Tela de histórico das tentativas/fotos capturadas
- Geofencing / alerta ao sair de uma área segura
- Publicação na Play Store (ícone final, política de privacidade — obrigatória
  por causa das permissões de câmera/localização em segundo plano)

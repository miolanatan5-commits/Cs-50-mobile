#!/bin/bash
# Liga o Linux ao GitHub por chave SSH (usada pelo check50 e pelo submit50).
# Uso: cs50-github setup "Nome" "email" [pt|en]   |   cs50-github test [pt|en]

CMD="$1"
if [ "$CMD" = "setup" ]; then NAME="$2"; EMAIL="$3"; L="${4:-en}"; else L="${2:-en}"; fi

say() { if [ "$L" = "pt" ]; then echo "$1"; else echo "$2"; fi; }

KEY="$HOME/.ssh/id_ed25519"

case "$CMD" in
setup)
    if ! command -v ssh-keygen > /dev/null 2>&1; then
        say "Instalando o cliente SSH..." "Installing the SSH client..."
        export DEBIAN_FRONTEND=noninteractive
        apt-get update && apt-get install -y --no-install-recommends openssh-client || {
            say "ERRO: não consegui instalar o cliente SSH." "ERROR: could not install the SSH client."
            exit 1
        }
    fi
    mkdir -p "$HOME/.ssh" && chmod 700 "$HOME/.ssh"
    if [ ! -f "$KEY" ]; then
        ssh-keygen -q -t ed25519 -N "" -f "$KEY" -C "$EMAIL" || exit 1
        say "Chave SSH criada." "SSH key created."
    else
        say "Você já tem uma chave SSH. Ela foi mantida." "You already have an SSH key. It was kept."
    fi
    # O GitHub aceita SSH pela porta 443, que passa em redes que bloqueiam a porta 22.
    if ! grep -q "cs50-mobile" "$HOME/.ssh/config" 2> /dev/null; then
        cat >> "$HOME/.ssh/config" << 'CFG'

# cs50-mobile
Host github.com
    Hostname ssh.github.com
    Port 443
    User git
    IdentityFile ~/.ssh/id_ed25519
    StrictHostKeyChecking accept-new
CFG
        chmod 600 "$HOME/.ssh/config"
    fi
    [ -n "$NAME" ] && git config --global user.name "$NAME"
    [ -n "$EMAIL" ] && git config --global user.email "$EMAIL"
    echo
    say "Sua chave pública (já pode tocar em Copiar chave pública no app):" \
        "Your public key (you can now tap Copy public key in the app):"
    cat "$KEY.pub"
    echo
    say "Próximo passo: abra a página de chaves do GitHub, cole a chave e salve. Depois toque em Testar conexão." \
        "Next step: open the GitHub keys page, paste the key and save. Then tap Test connection."
    ;;
test)
    if [ ! -f "$KEY" ]; then
        say "Ainda não existe chave. Toque em Gerar chave e configurar." \
            "There is no key yet. Tap Generate key and set up."
        exit 1
    fi
    OUT=$(ssh -T git@github.com 2>&1)
    echo "$OUT"
    if echo "$OUT" | grep -q "successfully authenticated"; then
        say "Conectado ao GitHub. Agora o check50 e o submit50 devem funcionar." \
            "Connected to GitHub. check50 and submit50 should work now."
    else
        say "Não conectou. Confira se colou a chave inteira na página de chaves do GitHub." \
            "Not connected. Check that you pasted the whole key on GitHub's keys page."
    fi
    ;;
*)
    echo "cs50-github setup \"Name\" \"email\" | cs50-github test"
    ;;
esac

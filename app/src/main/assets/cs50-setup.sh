#!/bin/bash
# Installs the CS50x tools in this Linux (Instala as ferramentas do CS50x). Safe to re-run. Usage: cs50-setup [pt|en]

L="${1:-en}"
say() { if [ "$L" = "pt" ]; then echo "$1"; else echo "$2"; fi; }

export DEBIAN_FRONTEND=noninteractive
export PIP_BREAK_SYSTEM_PACKAGES=1
FAILED=""

step() {
    echo
    echo "== $1"
}

note_fail() {
    say "FALHOU: $1" "FAILED: $1"
    FAILED="$FAILED [$1]"
}

step "$(say "Passo 1 de 5: atualizando a lista de pacotes" "Step 1 of 5: updating the package list")"
if ! apt-get update; then
    say "ERRO: não consegui atualizar a lista de pacotes. Verifique a internet e rode cs50-setup de novo." "ERROR: could not update the package list. Check your internet and run cs50-setup again."
    exit 1
fi

step "$(say "Passo 2 de 5: instalando compilador e ferramentas. Isso leva vários minutos." "Step 2 of 5: installing the compiler and tools. This takes several minutes.")"
if ! apt-get install -y --no-install-recommends \
    ca-certificates git make clang gdb python3 python3-pip python3-venv \
    sqlite3 astyle curl libc6-dev libcrypt-dev openssh-client; then
    say "ERRO: a instalação dos pacotes falhou. Rode cs50-setup de novo." "ERROR: package installation failed. Run cs50-setup again."
    exit 1
fi

step "$(say "Passo 3 de 5: verificando os sanitizers do clang" "Step 3 of 5: checking clang sanitizers")"
V=$(clang --version | sed -n 's/.*version \([0-9][0-9]*\)\..*/\1/p' | head -n 1)
if [ -n "$V" ]; then
    apt-get install -y --no-install-recommends "libclang-rt-$V-dev" > /dev/null 2>&1
fi
mkdir -p /usr/local/share
rm -f /usr/local/share/cs50-sanitize
if printf 'int main(void) { return 0; }\n' \
    | clang -x c -fsanitize=signed-integer-overflow -fsanitize=undefined - -o /tmp/cs50-san-test 2> /dev/null \
    && /tmp/cs50-san-test; then
    touch /usr/local/share/cs50-sanitize
    say "Sanitizers ativos: o programa avisa estouro de inteiros e comportamento indefinido." "Sanitizers on: the program reports integer overflow and undefined behavior."
else
    say "Sanitizers indisponíveis neste aparelho. O curso funciona sem eles." "Sanitizers unavailable on this device. The course works without them."
fi
rm -f /tmp/cs50-san-test

step "$(say "Passo 4 de 5: instalando a biblioteca cs50 para C" "Step 4 of 5: installing the cs50 library for C")"
rm -rf /tmp/libcs50
if git clone --depth 1 https://github.com/cs50/libcs50 /tmp/libcs50; then
    (cd /tmp/libcs50 && make && make install)
fi
if [ -e /usr/local/lib/libcs50.so ]; then
    ldconfig 2> /dev/null
    say "Biblioteca cs50 instalada." "cs50 library installed."
else
    note_fail "cs50 library (C)"
fi
rm -rf /tmp/libcs50

step "$(say "Passo 5 de 5: instalando as ferramentas Python do CS50" "Step 5 of 5: installing the CS50 Python tools")"
for pkg in cs50 check50 submit50 style50 help50; do
    echo "-- $pkg"
    pip3 install --break-system-packages "$pkg" || note_fail "$pkg"
done

echo
if [ -z "$FAILED" ]; then
    touch /usr/local/share/cs50-setup.done
    say "Pronto: as ferramentas do CS50 estão instaladas." "Done: the CS50 tools are installed."
    clang --version | head -n 1
    python3 --version
else
    say "Terminou com falhas em: $FAILED" "Finished with failures in: $FAILED"
    say "Rode cs50-setup de novo para tentar outra vez." "Run cs50-setup again to retry."
fi

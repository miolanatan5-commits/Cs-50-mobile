# Componentes de terceiros

Os dois arquivos em `app/src/main/jniLibs/arm64-v8a/` vêm do pacote pré-compilado
`packages/proot-android-aarch64.tar.gz` do repositório
https://github.com/green-green-avk/build-proot-android (scripts de build sob licença MIT):

| Arquivo no projeto      | Origem no pacote           | Licença |
|-------------------------|----------------------------|---------|
| `libproot.so`           | `root/bin/proot`           | GPL-2.0-or-later (PRoot, fork de green-green-avk: https://github.com/green-green-avk/proot) |
| `libproot-loader.so`    | `root/libexec/proot/loader`| GPL-2.0-or-later (parte do PRoot) |

O `libproot.so` já leva a biblioteca talloc ligada estaticamente (LGPL-3.0-or-later,
https://talloc.samba.org). O código-fonte correspondente está nos repositórios acima.
Ao distribuir o APK, mantenha este aviso e ofereça o código-fonte do PRoot.

# Matchday Play — versão de teste

Base: OpenTV (GPL-3.0-or-later), com alterações nesta branch.

## O que foi feito
- Aplicação com identificador `com.matchday.play.debug` na compilação de testes.
- Login Xtream só com utilizador e palavra-passe.
- Endpoint de teste embutido: `https://us7r.xyz:443` (não contém credenciais).
- Validação das credenciais antes de guardar a fonte.
- Menus de gestão de fornecedores e web manager ocultos da navegação.
- Nome, cores e ícones provisórios Matchday Play.
- Atualização automática da OpenTV original desativada.

## O que não está feito
- Imagens aprovadas e ecrã de login final ainda precisam de acabamento visual.
- Mudança remota de servidor ainda não existe nesta versão inicial.
- Não há garantia de que o fornecedor suporte a API Xtream nem de reprodução.
- Um endpoint embutido no APK é recuperável por análise técnica.
- Falta validar instalação, comando TV, catálogo e reprodução num dispositivo real.

## APK
O workflow `.github/workflows/build.yml` compila a versão debug.
No GitHub: **Actions → Build → run → Artifacts → opentv-debug-apk**.

Não publiques credenciais nos ficheiros do projeto ou nas issues.

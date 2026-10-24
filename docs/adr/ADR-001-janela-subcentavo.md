# ADR-001: Prevalência da Margem Mínima na Janela de Inconsistência Subcentavo

## Status
Aceito

## Contexto
Na aritmética de precificação com duas casas decimais, o piso de margem é calculado com arredondamento para cima (`CEILING`), garantindo que a margem mínima nominal da categoria nunca seja violada por frações de centavo. Em contrapartida, o teto de margem é arredondado para baixo (`FLOOR`), impedindo que a margem máxima permitida seja ultrapassada.

Em produtos de custo unitário reduzido e categorias cujas margens mínima e máxima são muito próximas (janelas estreitas), pode ocorrer uma inconsistência matemática onde:
$$\text{piso\_margem} > \text{teto\_margem}$$

*Exemplo Prático:* Custo de aquisição $c = \text{R\$} 4{,}20$, margem mínima de $9{,}36\%$ e margem máxima de $9{,}37\%$:
* $\text{Piso} = \lceil 4{,}20 / (1 - 0{,}0936) \rceil = \lceil 4{,}63371\dots \rceil = \text{R\$} 4{,}64$
* $\text{Teto} = \lfloor 4{,}20 / (1 - 0{,}0937) \rfloor = \lfloor 4{,}63422\dots \rfloor = \text{R\$} 4{,}63$

Nenhum número no domínio monetário discreto de centavos satisfaz simultaneamente ambas as restrições.

## Decisão
Estabeleceu-se que **a proteção contra prejuízo e erosão de margem tem precedência absoluta sobre o teto de preço**. Quando $\text{piso\_margem} > \text{teto\_margem}$, o intervalo de precificação colapsa unicamente em:
$$\text{alvo} = \text{piso\_margem}$$

Neste caso, o sinalizador `variationLimitHit` somente será ativado se a variação máxima permitida sobre o preço atual for incapaz de alcançar o alvo do piso.

## Consequências
### Positivas
* Garante rentabilidade mínima positiva em todas as vendas, prevenindo sangramento de caixa em alta escala.
* Elimina exceções não tratadas ou travamento de ciclos automatizados de precificação.
* Provado matematicamente com 100% de sucesso através de testes baseados em propriedades com 300.000 iterações estocásticas.

### Negativas
* A margem máxima da categoria pode ser momentaneamente superada em frações de centavo para itens de baixo valor, o que é plenamente aceitável pelo negócio frente ao risco de prejuízo.
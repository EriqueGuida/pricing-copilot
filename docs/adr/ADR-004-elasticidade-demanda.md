# ADR-004: Modelo Econométrico Log-Log, Maximização de Lucro e Recorte Defensivo

## Status
Aceito

## Contexto
Precificar produtos exclusivamente através de regras lineares de markup ou seguindo cegamente a concorrência gera dois grandes riscos: erosão agressiva de margem (guerra de preços irracional) ou perda de volume por preços excessivamente altos. Era necessário um modelo que estimasse a sensibilidade da demanda dos consumidores.

## Decisão
Adotou-se o modelo econométrico de **Regressão Múltipla Log-Log**:
$$\ln(q) = \alpha + \eta \ln(p) + \beta \ln(p_{\text{comp}}) + \sum \gamma_k D_k + \epsilon$$

Onde $\eta$ é a elasticidade-preço direta da demanda e $p^*$ é o preço que maximiza o lucro esperado:
$$p^* = c \cdot \left( \frac{\eta}{1 + \eta} \right), \quad \text{para } \eta < -1$$

Adicionalmente, integraram-se salvaguardas de corte:
1. **Limiar de Confiança:** Se $\eta \ge -1$ (demanda inelástica ou divergente), $R^2 < 0{,}30$ ou observações $< 30$, aciona-se a estratégia `BASELINE_FALLBACK` (mediana dos concorrentes $\times 0{,}99$ ou preço atual) com indicador `confidence: LOW`.
2. **Clipping Mandatório:** O valor gerado pela IA é obrigatoriamente recortado no intervalo seguro $[min\_price, max\_price]$ calculado previamente pelo Java.

## Consequências
### Positivas
* Formulação microeconômica matematicamente sólida que equilibra volume e margem.
* Proteção contra alucinação ou divergência assintótica de preços em itens inelásticos.
* Rastreabilidade analítica: todo output informa a elasticidade usada, o preço irrestrito e qual fator recortou o valor final (`MIN_PRICE`, `MAX_PRICE` ou `NONE`).

### Negativas
* O modelo pressupõe elasticidade constante localmente no horizonte de curto prazo, demandando recalibrações periódicas conforme a dinâmica de mercado mude.
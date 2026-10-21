"""
Gerador determinístico de séries temporais de vendas e preços concorrentes.
Adota choques exógenos na cadeia de suprimentos para evitar viés de endogeneidade.
"""

from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path
from typing import Dict, List, Tuple
import numpy as np
import pandas as pd


@dataclass(frozen=True)
class CategoryConfig:
    category_id: int
    name: str
    vertical: str
    true_elasticity: float       # eta < 0
    cross_elasticity: float      # beta > 0
    base_demand_alpha: float     # intercepto alpha
    base_cost: float             # custo base médio
    base_margin: float           # margem de markup padrão


VERTICAL_CONFIGS: Dict[str, CategoryConfig] = {
    "CONSUMER_ELECTRONICS": CategoryConfig(
        category_id=1,
        name="Eletrônicos de Consumo",
        vertical="CONSUMER_ELECTRONICS",
        true_elasticity=-1.80,
        cross_elasticity=0.45,
        base_demand_alpha=12.2,      # Ajustado de 8.5 -> volume diário ~30-50 unidades
        base_cost=400.0,
        base_margin=0.25,
    ),
    "COMPUTER_GAMING": CategoryConfig(
        category_id=2,
        name="Acessórios e Gaming",
        vertical="COMPUTER_GAMING",
        true_elasticity=-2.30,
        cross_elasticity=0.60,
        base_demand_alpha=13.0,      # Ajustado de 9.2 -> volume diário ~40-60 unidades
        base_cost=150.0,
        base_margin=0.30,
    ),
    "SMART_HOME": CategoryConfig(
        category_id=3,
        name="Casa Inteligente",
        vertical="SMART_HOME",
        true_elasticity=-1.50,
        cross_elasticity=0.35,
        base_demand_alpha=9.5,       # Ajustado de 7.8 -> volume diário ~25-45 unidades
        base_cost=90.0,
        base_margin=0.20,
    ),
}


class SyntheticDataGenerator:
    def __init__(self, seed: int = 42):
        self.seed = seed
        self.rng = np.random.default_rng(seed)

    def generate_dataset(
        self,
        start_date: date = date(2023, 1, 1),
        days: int = 365,
        products_per_vertical: int = 4,
    ) -> Tuple[pd.DataFrame, pd.DataFrame]:
        """
        Gera dois DataFrames relacionais e reproduzíveis:
        1. sales_history: transações diárias de venda.
        2. competitor_prices: coletas de concorrentes no mesmo período.
        """
        # Reinicia o gerador com a semente fixa para garantir reprodutibilidade
        rng = np.random.default_rng(self.seed)

        sales_records: List[dict] = []
        comp_records: List[dict] = []

        # Coeficientes reais de sazonalidade semanal (gamma_k): Seg=0, Dom=6
        day_of_week_effects = {
            0: -0.05,  # Segunda
            1: -0.02,  # Terça
            2: 0.00,   # Quarta
            3: 0.03,   # Quinta
            4: 0.12,   # Sexta (+ dem)
            5: 0.20,   # Sábado
            6: 0.15,   # Domingo
        }

        competitors = ["Amazon", "Kabum", "Mercado Livre", "Magazine Luiza"]
        product_global_id = 1

        for vertical_name, config in VERTICAL_CONFIGS.items():
            for p_idx in range(1, products_per_vertical + 1):
                product_id = product_global_id
                sku = f"SKU-{config.vertical[:3]}-{p_idx:03d}"
                product_global_id += 1

                # Dispersão de custo intrínseca por produto
                product_cost_base = config.base_cost * rng.uniform(0.85, 1.20)

                for day_offset in range(days):
                    current_date = start_date + timedelta(days=day_offset)
                    dow = current_date.weekday()
                    seasonal_gamma = day_of_week_effects[dow]

                    # 1. Choque exógeno de custo (variação cambial / fornecedor)
                    # Variável instrumental sintética que quebra o ciclo de endogeneidade
                    cost_shock = rng.normal(loc=0.0, scale=0.04)
                    daily_cost = round(product_cost_base * (1.0 + cost_shock), 2)

                    # 2. Preços dos Concorrentes
                    comp_shocks = rng.normal(loc=0.0, scale=0.03, size=len(competitors))
                    comp_daily_prices = []
                    for comp_name, shock in zip(competitors, comp_shocks):
                        # Preço concorrente ancorado no custo com margem setorial e ruído
                        raw_comp_price = daily_cost / (1.0 - config.base_margin) * (1.0 + shock)
                        p_comp_val = round(max(raw_comp_price, daily_cost * 1.05), 2)
                        comp_daily_prices.append(p_comp_val)

                        comp_records.append({
                            "product_id": product_id,
                            "sku": sku,
                            "competitor": comp_name,
                            "collected_at": current_date.isoformat(),
                            "price": p_comp_val,
                        })

                    p_comp_median = float(np.median(comp_daily_prices))

                    # 3. Política de preço da própria loja (com experimentos exógenos)
                    # Adiciona variação intencional de teste A/B para identificação de elasticidade
                    pricing_experiment = rng.uniform(-0.06, 0.06)
                    store_price = round(
                        (daily_cost / (1.0 - config.base_margin)) * (1.0 + pricing_experiment),
                        2
                    )

                    # 4. Processo Gerador da Demanda (Equação Log-Log Verdadeira):
                    # ln(q) = alpha + eta * ln(p) + beta * ln(p_comp) + gamma_k + epsilon
                    epsilon = rng.normal(loc=0.0, scale=0.08)
                    ln_q = (
                        config.base_demand_alpha
                        + config.true_elasticity * np.log(store_price)
                        + config.cross_elasticity * np.log(p_comp_median)
                        + seasonal_gamma
                        + epsilon
                    )

                    # Quantidade observada (discreta com valor mínimo 1)
                    quantity = max(1, int(np.round(np.exp(ln_q))))

                    sales_records.append({
                        "product_id": product_id,
                        "sku": sku,
                        "category_id": config.category_id,
                        "vertical": config.vertical,
                        "sold_on": current_date.isoformat(),
                        "quantity": quantity,
                        "unit_price": store_price,
                        "cost_price": daily_cost,
                        "competitor_median_price": p_comp_median,
                        "day_of_week": dow,
                    })

        df_sales = pd.DataFrame(sales_records)
        df_competitors = pd.DataFrame(comp_records)

        return df_sales, df_competitors


def export_synthetic_data(output_dir: str = "data") -> Tuple[Path, Path]:
    out_path = Path(output_dir)
    out_path.mkdir(parents=True, exist_ok=True)

    generator = SyntheticDataGenerator(seed=42)
    df_sales, df_comp = generator.generate_dataset()

    sales_file = out_path / "sales_history.csv"
    comp_file = out_path / "competitor_prices.csv"

    df_sales.to_csv(sales_file, index=False)
    df_comp.to_csv(comp_file, index=False)

    return sales_file, comp_file


if __name__ == "__main__":
    sales_p, comp_p = export_synthetic_data()
    print(f"Dados gerados com sucesso:\n- {sales_p}\n- {comp_p}")
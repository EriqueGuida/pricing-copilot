"""
Testes de integridade e reprodutibilidade estrita para o gerador de dados sintéticos.
"""

import pandas as pd
from data_generator import SyntheticDataGenerator


def test_data_generator_strict_reproducibility():
    """
    Garante que duas execuções sucessivas com a mesma semente (seed=42)
    produzem DataFrames exatamente idênticos em dimensões, valores e tipos.
    """
    gen1 = SyntheticDataGenerator(seed=42)
    sales_df_1, comp_df_1 = gen1.generate_dataset(days=30, products_per_vertical=2)

    gen2 = SyntheticDataGenerator(seed=42)
    sales_df_2, comp_df_2 = gen2.generate_dataset(days=30, products_per_vertical=2)

    # Asserção estrita em todas as linhas e colunas
    pd.testing.assert_frame_equal(sales_df_1, sales_df_2, check_exact=True)
    pd.testing.assert_frame_equal(comp_df_1, comp_df_2, check_exact=True)


def test_generated_data_business_invariants():
    """Valida as restrições de integridade financeira e cardinalidade dos dados."""
    gen = SyntheticDataGenerator(seed=42)
    sales_df, comp_df = gen.generate_dataset(days=10, products_per_vertical=2)

    # Preços e custos devem ser estritamente positivos
    assert (sales_df["unit_price"] > 0).all()
    assert (sales_df["cost_price"] > 0).all()
    assert (sales_df["quantity"] >= 1).all()
    assert (comp_df["price"] > 0).all()

    # Cobertura das três verticais obrigatórias
    expected_verticals = {"CONSUMER_ELECTRONICS", "COMPUTER_GAMING", "SMART_HOME"}
    assert set(sales_df["vertical"].unique()) == expected_verticals
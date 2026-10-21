"""
Pipeline de calibração econométrica via Regressão Múltipla Log-Log.
Estima elasticidade direta, cruzada e sazonalidade diária por vertical.
"""

from datetime import datetime, timezone
import json
from pathlib import Path
from typing import Dict, Any
import numpy as np
import pandas as pd
import statsmodels.api as sm

from data_generator import export_synthetic_data


def train_econometric_models(sales_file: Path) -> Dict[str, Any]:
    df = pd.read_csv(sales_file)

    # Variáveis transformadas em escala logarítmica
    df["ln_q"] = np.log(df["quantity"])
    df["ln_p"] = np.log(df["unit_price"])
    df["ln_p_comp"] = np.log(df["competitor_median_price"])

    # Dummies de dia da semana (referência: Quarta-feira dow=2 para evitar dummy variable trap)
    dow_dummies = pd.get_dummies(df["day_of_week"], prefix="dow", drop_first=False, dtype=float)
    reference_dow_col = "dow_2"
    selected_dow_cols = [col for col in dow_dummies.columns if col != reference_dow_col]

    df = pd.concat([df, dow_dummies[selected_dow_cols]], axis=1)

    model_artifacts: Dict[str, Any] = {
        "model_version": "v1.0.0",
        "calibrated_at": datetime.now(timezone.utc).isoformat(),
        "categories": {},
    }

    verticals = df["vertical"].unique()

    for vertical in verticals:
        subset = df[df["vertical"] == vertical].copy()

        feature_cols = ["ln_p", "ln_p_comp"] + selected_dow_cols
        X = sm.add_constant(subset[feature_cols])
        y = subset["ln_q"]

        # Ajuste OLS Robusto
        ols_model = sm.OLS(y, X).fit(cov_type="HC3")  # Erros-padrão robustos à heterocedasticidade

        eta_estimated = float(ols_model.params["ln_p"])
        beta_estimated = float(ols_model.params["ln_p_comp"])
        alpha_estimated = float(ols_model.params["const"])
        r2 = float(ols_model.rsquared)
        p_val_eta = float(ols_model.pvalues["ln_p"])
        conf_int_eta = [float(x) for x in ols_model.conf_int().loc["ln_p"].values]

        model_artifacts["categories"][vertical] = {
            "eta": round(eta_estimated, 4),
            "beta": round(beta_estimated, 4),
            "alpha": round(alpha_estimated, 4),
            "r2": round(r2, 4),
            "p_value_eta": p_val_eta,
            "conf_int_eta_95": conf_int_eta,
            "observations": int(ols_model.nobs),
            "status": "CALIBRATED" if (eta_estimated < -1.0 and r2 >= 0.30) else "DEGRADED",
        }

        print(
            f"[{vertical}] -> eta: {eta_estimated:.4f} (p={p_val_eta:.2e}), "
            f"beta: {beta_estimated:.4f}, R²: {r2:.4f}"
        )

    return model_artifacts


def run():
    print("1. Gerando dados sintéticos reproduzíveis...")
    sales_file, _ = export_synthetic_data("data")

    print("2. Ajustando regressões econométricas log-log...")
    artifact = train_econometric_models(sales_file)

    output_file = Path("model_artifact.json")
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(artifact, f, indent=2, ensure_ascii=False)

    print(f"3. Artefato salvo com sucesso em: {output_file.resolve()}")


if __name__ == "__main__":
    run()
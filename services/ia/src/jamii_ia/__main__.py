"""Lancement : `uv run python -m jamii_ia`."""

import uvicorn

from jamii_ia.config import reglages
from jamii_ia.main import creer_app

if __name__ == "__main__":
    # 0.0.0.0 : obligatoire dans un conteneur ou chez un hébergeur, qui route vers ce port.
    uvicorn.run(creer_app(), host="0.0.0.0", port=reglages().port)  # noqa: S104

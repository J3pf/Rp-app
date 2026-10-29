#!/usr/bin/env python3
"""Elige el Xcode instalado más nuevo que cumpla la versión mínima exigida.

Imprime `ruta|versión` y sale con 0 si encuentra uno; si ninguno cumple, sale con 1
sin imprimir nada. Existe porque el pool de runners de GitHub rota su conjunto de
Xcode entre corridas, así que fijar una *minor* exacta produce fallos por la imagen
y no por el proyecto. El mínimo lo pasa el workflow en XCODE_MIN_VERSION.
"""

import os
import re
import subprocess
import sys

APLICACIONES = "/Applications"


def version_minima() -> tuple[int, ...]:
    crudo = os.environ.get("XCODE_MIN_VERSION", "")
    if not re.fullmatch(r"\d+(\.\d+)*", crudo or ""):
        print(f"XCODE_MIN_VERSION inválido: {crudo!r}", file=sys.stderr)
        raise SystemExit(2)
    return tuple(int(parte) for parte in crudo.split("."))


def candidatos() -> list[tuple[tuple[int, ...], str, str]]:
    encontrados = []
    for nombre in sorted(os.listdir(APLICACIONES)):
        if not re.fullmatch(r"Xcode[_-]?\d*(\.\d+)*\.app", nombre):
            continue
        ruta = os.path.join(APLICACIONES, nombre)
        binario = os.path.join(ruta, "Contents", "Developer", "usr", "bin", "xcodebuild")
        if not os.path.isdir(ruta) or not os.path.exists(binario):
            continue
        try:
            salida = subprocess.run(
                [binario, "-version"], capture_output=True, text=True, timeout=120
            ).stdout
        except (OSError, subprocess.SubprocessError):
            continue
        coincidencia = re.search(r"Xcode (\S+)", salida)
        if coincidencia is None:
            continue
        version = coincidencia.group(1)
        numeros = tuple(int(x) for x in re.findall(r"\d+", version)[:3])
        encontrados.append((numeros, ruta, version))
    return encontrados


def main() -> int:
    minimo = version_minima()
    validos = [c for c in candidatos() if c[0] >= minimo]
    if not validos:
        print(
            f"ningún Xcode instalado alcanza la versión mínima {'.'.join(map(str, minimo))}",
            file=sys.stderr,
        )
        return 1
    validos.sort()
    _, ruta, version = validos[-1]
    print(f"{ruta}|{version}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

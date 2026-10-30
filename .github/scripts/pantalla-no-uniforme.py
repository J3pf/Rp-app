#!/usr/bin/env python3
"""Falla si la captura de pantalla es uniforme: una pantalla negra y una blanca dan desviación 0.

Lanzar y sobrevivir no es dibujar. Una app con la capa Metal adjunta y cero frames sigue viva y
muestra negro, así que la única prueba de que se dibuja algo es mirar los píxeles.

Se decodifica el PNG a mano con `zlib` (biblioteca estándar) en vez de convertir a BMP: `sips`
produce BMP con compresión BI_BITFIELDS, que es más frágil de leer que el PNG que ya está ahí.

Uso: pantalla-no-uniforme.py <captura.png> [umbral]
Sale 0 si hay contenido, 1 si es uniforme, 2 si la imagen no se puede leer.
"""

import statistics
import struct
import sys
import zlib

CANALES_POR_TIPO = {0: 1, 2: 3, 4: 2, 6: 4}


def leer_png(ruta):
    datos = open(ruta, "rb").read()
    if datos[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("el archivo no es un PNG")
    posicion, idat = 8, b""
    ancho = alto = profundidad = tipo = entrelazado = 0
    while posicion < len(datos):
        largo, trozo = struct.unpack_from(">I4s", datos, posicion)
        cuerpo = datos[posicion + 8 : posicion + 8 + largo]
        if trozo == b"IHDR":
            ancho, alto, profundidad, tipo, _, _, entrelazado = struct.unpack_from(">IIBBBBB", cuerpo, 0)
        elif trozo == b"IDAT":
            idat += cuerpo
        posicion += 12 + largo
    if profundidad != 8 or tipo not in CANALES_POR_TIPO or entrelazado != 0:
        raise ValueError(
            f"PNG no soportado: profundidad={profundidad} tipoColor={tipo} entrelazado={entrelazado}"
        )
    return ancho, alto, CANALES_POR_TIPO[tipo], zlib.decompress(idat)


def desfiltrar(ancho, alto, canales, crudo):
    """Aplica los cinco filtros de PNG; sin esto los bytes no son colores."""
    paso = ancho * canales
    filas = []
    previa = bytearray(paso)
    i = 0
    for _ in range(alto):
        filtro = crudo[i]
        i += 1
        fila = bytearray(crudo[i : i + paso])
        i += paso
        if filtro == 1:
            for x in range(canales, paso):
                fila[x] = (fila[x] + fila[x - canales]) & 0xFF
        elif filtro == 2:
            for x in range(paso):
                fila[x] = (fila[x] + previa[x]) & 0xFF
        elif filtro == 3:
            for x in range(paso):
                izquierda = fila[x - canales] if x >= canales else 0
                fila[x] = (fila[x] + ((izquierda + previa[x]) >> 1)) & 0xFF
        elif filtro == 4:
            for x in range(paso):
                a = fila[x - canales] if x >= canales else 0
                b = previa[x]
                c = previa[x - canales] if x >= canales else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                predictor = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                fila[x] = (fila[x] + predictor) & 0xFF
        elif filtro != 0:
            raise ValueError(f"filtro PNG desconocido: {filtro}")
        filas.append(bytes(fila))
        previa = fila
    return filas


def main():
    if len(sys.argv) < 2:
        print("uso: pantalla-no-uniforme.py <captura.png> [umbral]", file=sys.stderr)
        return 2
    ruta = sys.argv[1]
    umbral = float(sys.argv[2]) if len(sys.argv) > 2 else 1.0
    try:
        ancho, alto, canales, crudo = leer_png(ruta)
        filas = desfiltrar(ancho, alto, canales, crudo)
    except (OSError, ValueError, zlib.error) as error:
        print(f"no se pudo leer la captura: {error}", file=sys.stderr)
        return 2

    luminancias = []
    for y in range(0, alto, 6):
        fila = filas[y]
        for x in range(0, ancho, 6):
            j = x * canales
            if canales >= 3:
                r, g, b = fila[j], fila[j + 1], fila[j + 2]
            else:
                r = g = b = fila[j]
            luminancias.append(0.2126 * r + 0.7152 * g + 0.0722 * b)

    media = statistics.fmean(luminancias)
    desviacion = statistics.pstdev(luminancias)
    print(f"captura: {ancho}x{alto}, {canales} canales")
    print(f"píxeles muestreados: {len(luminancias)}")
    print(f"luminancia media: {media:.2f}   desviación típica: {desviacion:.2f}")
    print(f"mínimo: {min(luminancias):.2f}   máximo: {max(luminancias):.2f}")

    if desviacion < umbral:
        # El umbral solo descarta la ausencia total de dibujo; no juzga el diseño ni el contraste.
        print("::error::La pantalla capturada es uniforme: no se está dibujando ningún frame.")
        return 1
    print("la pantalla tiene contenido: se está dibujando algo")
    return 0


if __name__ == "__main__":
    sys.exit(main())

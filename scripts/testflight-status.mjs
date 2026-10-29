#!/usr/bin/env node
// Consulta el estado de los últimos builds de TestFlight en App Store Connect y escribe una tabla en el
// resumen del job. Los códigos de salida son la parte importante: esto tiene que servir de alarma.
//
//   0  el build más nuevo está VALID
//   1  el build más nuevo está INVALID o FAILED  (Apple rechaza en silencio; no se puede dejar pasar)
//   2  el build sigue PROCESSING
//   3  no se encontró la app o no hay builds
//   4  error de la API (401/403 se imprimen tal cual, sin adivinar alcances)
//
// Sin dependencias: el JWT ES256 se firma con node:crypto. Nunca se imprime la clave ni el token.

import crypto from "node:crypto";
import fs from "node:fs/promises";

const API = "https://api.appstoreconnect.apple.com";
const BUNDLE_ID = "dev.j3pf.rp.iosApp";
const VALID = new Set(["VALID"]);
const REJECTED = new Set(["INVALID", "FAILED"]);

/** Firma un JWT ES256 con la clave .p8 de App Store Connect. `exp` nunca supera los 20 minutos. */
export function mintJwt({ keyId, issuerId, privateKey, nowMs = Date.now() }) {
    const iat = Math.floor(nowMs / 1000);
    const exp = iat + 15 * 60;
    const header = { alg: "ES256", kid: keyId, typ: "JWT" };
    const payload = { iss: issuerId, iat, exp, aud: "appstoreconnect-v1" };
    const b64 = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
    const signingInput = `${b64(header)}.${b64(payload)}`;
    // ieee-p1363 da la firma cruda r||s que exige JOSE; sin esto saldría DER y Apple devolvería 401.
    const signature = crypto.sign("sha256", Buffer.from(signingInput), {
        key: privateKey,
        dsaEncoding: "ieee-p1363",
    });
    return `${signingInput}.${signature.toString("base64url")}`;
}

async function get(path, token) {
    const response = await fetch(`${API}${path}`, {
        headers: { Authorization: `Bearer ${token}` },
    });
    const body = await response.text();
    if (!response.ok) {
        const detalle = body.slice(0, 600);
        throw new Error(`HTTP ${response.status} en ${path}: ${detalle}`);
    }
    return JSON.parse(body);
}

function tabla(filas) {
    const cabecera = ["Versión", "Build", "processingState", "Subido"];
    const lineas = [
        `| ${cabecera.join(" | ")} |`,
        `| ${cabecera.map(() => "---").join(" | ")} |`,
        ...filas.map((f) => `| ${f.version} | ${f.build} | ${f.estado} | ${f.subido} |`),
    ];
    return lineas.join("\n");
}

async function resumen(texto) {
    const destino = process.env.GITHUB_STEP_SUMMARY;
    if (destino) await fs.appendFile(destino, `${texto}\n`);
    console.log(texto);
}

async function main() {
    const keyId = process.env.APP_STORE_CONNECT_KEY_ID;
    const issuerId = process.env.APP_STORE_CONNECT_ISSUER_ID;
    const privateKey = process.env.APP_STORE_CONNECT_KEY_P8;
    const faltan = [
        ["APP_STORE_CONNECT_KEY_ID", keyId],
        ["APP_STORE_CONNECT_ISSUER_ID", issuerId],
        ["APP_STORE_CONNECT_KEY_P8", privateKey],
    ]
        .filter(([, valor]) => !valor)
        .map(([nombre]) => nombre);
    if (faltan.length > 0) {
        // Los nombres sí, los valores nunca.
        console.error(`faltan secretos: ${faltan.join(", ")}`);
        return 4;
    }

    const token = mintJwt({ keyId, issuerId, privateKey });

    const apps = await get(`/v1/apps?filter[bundleId]=${BUNDLE_ID}`, token);
    const app = apps?.data?.[0];
    if (!app) {
        await resumen(`No hay ninguna app con bundle id \`${BUNDLE_ID}\` en App Store Connect.`);
        return 3;
    }

    // En `/v1/builds`, `attributes.version` es el número de build. La versión de marketing vive en la
    // relación `preReleaseVersion`, de ahí el `include`.
    const builds = await get(
        `/v1/builds?filter[app]=${app.id}&sort=-uploadedDate&limit=5&include=preReleaseVersion`,
        token,
    );
    const versiones = new Map(
        (builds?.included ?? [])
            .filter((item) => item.type === "preReleaseVersions")
            .map((item) => [item.id, item.attributes?.version ?? "?"]),
    );
    const filas = (builds?.data ?? []).map((build) => {
        const preReleaseId = build.relationships?.preReleaseVersion?.data?.id;
        return {
            version: versiones.get(preReleaseId) ?? "?",
            build: build.attributes?.version ?? "?",
            estado: build.attributes?.processingState ?? "?",
            subido: (build.attributes?.uploadedDate ?? "?").replace("T", " ").replace(/\..*$/, " UTC"),
        };
    });

    if (filas.length === 0) {
        await resumen(`La app \`${BUNDLE_ID}\` existe pero no tiene ningún build subido todavía.`);
        return 3;
    }

    await resumen(tabla(filas));
    const nuevo = filas[0];
    if (VALID.has(nuevo.estado)) {
        await resumen(`El build más nuevo (${nuevo.version} / ${nuevo.build}) está **VALID**: listo en TestFlight.`);
        return 0;
    }
    if (REJECTED.has(nuevo.estado)) {
        await resumen(
            `El build más nuevo (${nuevo.version} / ${nuevo.build}) está **${nuevo.estado}**: Apple lo rechazó y hay que leer el motivo en App Store Connect.`,
        );
        return 1;
    }
    await resumen(`El build más nuevo (${nuevo.version} / ${nuevo.build}) sigue **${nuevo.estado}**: todavía procesando.`);
    return 2;
}

if (import.meta.url === `file://${process.argv[1]}`) {
    main()
        .then((codigo) => {
            process.exitCode = codigo;
        })
        .catch((error) => {
            console.error(error instanceof Error ? error.message : String(error));
            process.exitCode = 4;
        });
}

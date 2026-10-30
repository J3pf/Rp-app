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

/**
 * Igual que `get`, pero devuelve el rechazo en vez de lanzarlo: cuando la API se niega a servir una
 * relación, lo útil es imprimir el estado y el cuerpo exactos, no adivinar alcances ni reintentar.
 */
async function getTolerante(path, token) {
    const response = await fetch(`${API}${path}`, {
        headers: { Authorization: `Bearer ${token}` },
    });
    const body = await response.text();
    if (!response.ok) {
        return { error: `HTTP ${response.status} en ${path}\n${body.slice(0, 600)}` };
    }
    return { data: JSON.parse(body) };
}

/**
 * Resuelve qué builds contiene realmente cada grupo de beta. Un build VALID no está en TestFlight si
 * ningún grupo lo incluye: TestFlight le sirve al tester el más nuevo de su grupo, no el más nuevo de
 * la cuenta. Por eso esto es evidencia, no inferencia.
 */
async function seccionGrupos(appId, numerosDeBuild, token) {
    const grupos = await get(`/v1/betaGroups?filter[app]=${appId}&limit=50`, token);
    const lista = grupos?.data ?? [];
    if (lista.length === 0) {
        return { texto: "La app no tiene ningún grupo de beta.", pertenencia: new Map() };
    }

    const filas = [];
    const pertenencia = new Map();
    for (const grupo of lista) {
        const nombre = grupo.attributes?.name ?? "?";
        const interno = grupo.attributes?.isInternalGroup === true ? "sí" : "no";
        const respuesta = await getTolerante(
            `/v1/betaGroups/${grupo.id}/builds?limit=200&fields[builds]=version,processingState`,
            token,
        );
        if (respuesta.error) {
            // Se imprime el rechazo tal cual y se corta aquí: sin esto, cualquier conclusión sería adivinada.
            return {
                texto: `No se pudo leer los builds del grupo "${nombre}":\n\n\`\`\`\n${respuesta.error}\n\`\`\``,
                pertenencia: new Map(),
            };
        }
        const numeros = (respuesta.data?.data ?? [])
            .map((build) => build.attributes?.version ?? "?")
            .sort((a, b) => Number(b) - Number(a));
        for (const numero of numeros) {
            if (!pertenencia.has(numero)) pertenencia.set(numero, []);
            pertenencia.get(numero).push(nombre);
        }
        filas.push({ nombre, interno, builds: numeros.length === 0 ? "(ninguno)" : numeros.join(", ") });
    }

    const cabecera = ["Grupo", "Interno", "Builds que contiene"];
    const tablaGrupos = [
        `| ${cabecera.join(" | ")} |`,
        `| ${cabecera.map(() => "---").join(" | ")} |`,
        ...filas.map((f) => `| ${f.nombre} | ${f.interno} | ${f.builds} |`),
    ].join("\n");

    const veredicto = numerosDeBuild.map((numero) => {
        const en = pertenencia.get(numero) ?? [];
        return en.length === 0
            ? `- Build **${numero}**: **no está en ningún grupo**. TestFlight nunca lo va a ofrecer.`
            : `- Build **${numero}**: está en ${en.map((n) => `"${n}"`).join(", ")}.`;
    });

    return {
        texto: [tablaGrupos, "", "Pertenencia a grupos:", ...veredicto].join("\n"),
        pertenencia,
    };
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

    // Evidencia extra sobre la distribución. Si esto falla, el código de salida no cambia: la alarma
    // sigue siendo el estado del build.
    try {
        const grupos = await seccionGrupos(
            app.id,
            filas.slice(0, 3).map((fila) => fila.build),
            token,
        );
        await resumen(grupos.texto);
    } catch (error) {
        await resumen(
            `No se pudieron leer los grupos de beta: ${error instanceof Error ? error.message : String(error)}`,
        );
    }

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

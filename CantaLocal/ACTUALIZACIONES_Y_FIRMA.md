# CantaLocal — Política de actualización Android

## Problema identificado (2026-11-16)

Las compilaciones 0.4–0.7 fueron publicadas con `assembleDebug` en GitHub Actions,
sin un almacén de claves persistente. Los ejecutores efímeros generan certificados
de depuración diferentes. Por eso instalar una versión sobre otra con el mismo
`applicationId` provocó `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

**No es posible convertir la versión 0.7 en una actualización de la 0.6 usando
únicamente los certificados públicos de ambas APK.** Hace falta la clave
privada original de la 0.6, que no se conservó. Tampoco puede fabricarse una
prueba de rotación de claves sin la clave privada anterior.

## Política futura

- Un identificador permanente: `cl.cantalocal.autonomo`.
- Cada actualización incrementa el `versionCode`.
- Las versiones destinadas al usuario solo pueden proceder de
  `assembleRelease` con un certificado de publicación persistente.
- La clave privada se gestiona **fuera del repositorio público**, mediante
  secretos cifrados de GitHub Actions. Nunca se publica ni se incluye en el ZIP.
- CI rechaza construir o publicar si falta la clave, la contraseña o el
  certificado fijado en `CANTALOCAL_SIGNER_SHA256`.
- CI ejecuta `apksigner verify --print-certs` y compara la huella SHA-256.
- Los modelos GGUF permanecen en el almacenamiento de la aplicación; una
  actualización legítima (igual ID y certificado) conserva esos archivos.

## Secretos requeridos para habilitar publicación

`CANTALOCAL_KEYSTORE_BASE64`: almacén de claves privado, codificado base64.

`CANTALOCAL_STORE_PASSWORD`: contraseña del almacén.

`CANTALOCAL_KEY_ALIAS`: nombre de la clave.

`CANTALOCAL_KEY_PASSWORD`: contraseña de la clave.

`CANTALOCAL_SIGNER_SHA256`: huella pública SHA-256 exacta del certificado.

Las credenciales todavía no están configuradas como secretos en GitHub Actions.
Sin embargo, **ya se creó una firma privada permanente fuera de GitHub** y se guardó
exclusivamente en la Biblioteca privada de este usuario, carpeta `/CantaLocal/Privado/`.
Las versiones de distribución se firman localmente con la clave `cantalocal-release.jks`
y `secret.env` recuperados de esa ubicación, sin publicarlos en el repositorio.
El sistema de CI evita publicar una compilación debug con otra firma.

**Huella del certificado permanente:**
`f62a0ad426f1e3a0d97f9f7f5b3a8ebaebc60370d680f28c320bde8112e36a1a`

**APK entregada como base:** `CantaLocal_0.8.1_Actualizable.apk`, versiónCode 11,
package `cl.cantalocal.autonomo`. El APK 0.8.0/versionCode10 de prueba también
se firmó con esa misma huella SHA-256. Ambos se verificaron mediante `apksigner`.

**Para la próxima versión**: recuperar los dos archivos privados de Biblioteca,
incrementar `versionCode` (mínimo 12), compilar y firmar con esa misma clave.
Verificar la huella antes de publicar. Nunca generar otra clave.
No copiar credenciales a workflows públicos ni al código fuente.

## Restricción de migración desde 0.6

Android no permite actualizar una APK firmada con una clave distinta ni acceder
a sus datos protegidos desde una aplicación con otra firma. Mantener instalada
la versión 0.6 evita borrar los modelos existentes. Una eventual primera
instalación con la nueva firma requerirá una estrategia de migración de datos
autorizada y comprobada; no se debe prometer una conversión automática.

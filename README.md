# Gaming Pipeline

Proyecto Java para ingerir tiendas y ofertas de CheapShark en PostgreSQL. La
conexión se configura mediante variables de entorno para evitar guardar
credenciales en el repositorio.

## Requisitos

- Java 17 o superior
- Maven
- PostgreSQL iniciado y accesible desde este equipo
- Base de datos creada y tablas cargadas desde `schema.sql`

## Configurar la conexión en PowerShell

En una ventana de PowerShell, establece los datos de conexión. `PGHOST` y
`PGPORT` son opcionales si usas `localhost` y el puerto `5432`.

```powershell
$env:PGHOST = "localhost"
$env:PGPORT = "5432"
$env:PGDATABASE = "gaming_deals_db"
$env:PGUSER = "postgres"
$securePassword = Read-Host "Contraseña de PostgreSQL" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}
```

La contraseña se introduce al ejecutarlo y no se guarda en el repositorio.
Estas variables solo estarán disponibles en esa ventana de PowerShell.

## Probar la conexión

Desde la raíz del repositorio, ejecuta:

```powershell
mvn compile exec:java "-Dexec.mainClass=com.gamingpipeline.db.DatabaseConnectionCheck"
```

Si la conexión falla, comprueba que el servicio PostgreSQL está iniciado, que
el host y puerto son correctos, y que el usuario tiene acceso a la base de datos.

## Ejecutar la ingesta de CheapShark

Una vez establecidas las variables de conexión en PowerShell:

```powershell
mvn compile exec:java "-Dexec.mainClass=com.gamingpipeline.etl.CheapSharkIngestion"
```

La ingesta consulta el endpoint de tiendas y la primera página de ofertas
(60 resultados), inserta o actualiza `dim_tienda`, inserta juegos nuevos en
`dim_juego` y registra los hechos en `fact_oferta` dentro de una transacción.
Ofertas con el mismo juego, tienda, precio rebajado y descuento no se vuelven a
insertar.

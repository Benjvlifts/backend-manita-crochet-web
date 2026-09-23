# Pedidos360 — Backend

Backend del sistema **Pedidos360**, compuesto por dos microservicios en **Spring Boot 3 / Java 17**,
cada uno protegido como **OAuth2 Resource Server** que valida JWT emitidos por **Microsoft Entra ID**
(tenant normal — `login.microsoftonline.com`, **no** Entra External ID / CIAM).

```
backend/
├── lanas-service/     (puerto 8080)  Catálogo de lanas
└── pedidos-service/   (puerto 8081)  Pedidos de clientes
```

Ambos microservicios comparten el **mismo tenant** y el **mismo App Registration** de Entra ID
(un solo registro, con su scope expuesto en "Exponer una API").

## Autenticación y autorización

- El token se valida por **firma** (JWKS de Entra ID), **issuer** y **audience** (`SecurityConfig.java`
  en cada servicio).
- Los roles vienen del claim `roles` del token (App Roles de Entra ID) y se mapean a
  autoridades `ROLE_Admin` / `ROLE_User`.
- Reglas de autorización:
  | Endpoint | Regla |
  |---|---|
  | `GET /api/public/health` | público |
  | `GET /api/lanas` | cualquier usuario autenticado |
  | `POST /api/lanas` | solo `ROLE_Admin` |
  | `GET /api/pedidos` | cualquier usuario autenticado |
  | `POST /api/pedidos` | cualquier usuario autenticado |
  | `PATCH /api/pedidos/{id}/estado` | solo `ROLE_Admin` |
- Sin token → `401` (`unauthorized`). Con token pero sin el rol requerido → `403` (`forbidden`).
  Ambos códigos se devuelven como JSON (`{"error": "...", "message": "..."}`).

## Variables de entorno

| Variable | Descripción | Default (dev) |
|---|---|---|
| `AZURE_TENANT_ID` | Directory (tenant) ID del tenant de Entra ID | `YOUR_AZURE_TENANT_ID` |
| `AZURE_CLIENT_ID` | Application (client) ID del App Registration | `YOUR_AZURE_CLIENT_ID` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos para el frontend (coma-separados) | `http://localhost:4200` |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | Conexión a MySQL (RDS) en producción | H2 en memoria |

Por defecto cada servicio corre con **H2 en memoria** (sin configurar nada) y datos de ejemplo
cargados por `DataLoader`. Para usar MySQL/RDS, descomenta las líneas correspondientes en
`application.properties` y define las variables `DB_*`.

## Cómo ejecutar

Cada microservicio es un proyecto Maven independiente:

```bash
cd lanas-service
AZURE_TENANT_ID=xxxx AZURE_CLIENT_ID=yyyy mvn spring-boot:run
```

```bash
cd pedidos-service
AZURE_TENANT_ID=xxxx AZURE_CLIENT_ID=yyyy mvn spring-boot:run
```

Correr las pruebas (no dependen de red ni del tenant real; simulan el JWT ya validado):

```bash
mvn test
```

## Configuración necesaria en Azure Entra ID (antes de probar con tokens reales)

1. Crear/usar un tenant de **"Id. de Microsoft Entra"** (no "Entra External ID").
2. Registrar **una sola aplicación** para todo el sistema → anotar **Application (client) ID** y
   **Directory (tenant) ID**.
3. En **Exponer una API**, definir el Application ID URI y agregar un scope (ej. `access_as_user`).
4. En **Roles de aplicación (App roles)**, crear los roles `Admin` y `User`, y asignarlos a los
   usuarios de prueba desde **Empresas → tu app → Usuarios y grupos**.
5. Cargar `AZURE_TENANT_ID` y `AZURE_CLIENT_ID` como variables de entorno al desplegar (EC2) o al
   correr localmente.

## Despliegue (referencia para EP2)

Pensado para correr en instancias **EC2** detrás de **AWS API Gateway**, que actúa como fachada
única hacia ambos microservicios (`/api/lanas/*` → lanas-service, `/api/pedidos/*` → pedidos-service)
y valida el JWT antes de reenviar la petición.

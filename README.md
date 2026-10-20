# Pedidos360 — Backend

Backend del sistema **Pedidos360**, compuesto por tres microservicios en **Spring Boot 3 / Java 17**,
cada uno protegido como **OAuth2 Resource Server** que valida JWT emitidos por **Microsoft Entra ID**
(tenant normal — `login.microsoftonline.com`, **no** Entra External ID / CIAM).

```
backend/
├── lanas-service/            (puerto 8080)  Catálogo de lanas
├── pedidos-service/          (puerto 8081)  Pedidos de clientes + productor/consumidores RabbitMQ
├── rabbitmq-admin-service/   (puerto 8082)  API REST para administrar colas, exchanges y bindings
└── docker-compose.yml                       RabbitMQ para desarrollo local
```

Los tres microservicios comparten el **mismo tenant** y el **mismo App Registration** de Entra ID
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
  | `/api/admin/**` (rabbitmq-admin-service) | solo `ROLE_Admin` |
- Sin token → `401` (`unauthorized`). Con token pero sin el rol requerido → `403` (`forbidden`).
  Ambos códigos se devuelven como JSON (`{"error": "...", "message": "..."}`).

## Variables de entorno

| Variable | Descripción | Default (dev) |
|---|---|---|
| `AZURE_TENANT_ID` | Directory (tenant) ID del tenant de Entra ID | `YOUR_AZURE_TENANT_ID` |
| `AZURE_CLIENT_ID` | Application (client) ID del App Registration | `YOUR_AZURE_CLIENT_ID` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos para el frontend (coma-separados) | `http://localhost:4200` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | Dirección del broker (`pedidos-service` y `rabbitmq-admin-service`) | `localhost` / `5672` |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | Credenciales del broker | `guest` / `guest` |
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

```bash
cd rabbitmq-admin-service
AZURE_TENANT_ID=xxxx AZURE_CLIENT_ID=yyyy mvn spring-boot:run
```

> `pedidos-service` y `rabbitmq-admin-service` necesitan un RabbitMQ activo para mensajería
> (ver la sección *Mensajería con RabbitMQ*). Sin broker los servicios igual arrancan: crear un pedido
> sigue funcionando y el error de publicación queda registrado en el log.

Correr las pruebas (no dependen de red ni del tenant real; simulan el JWT ya validado):

```bash
mvn test
```

## Mensajería con RabbitMQ (EP3)

Levantar el broker en local (consola web en <http://localhost:15672>, usuario/clave `guest`):

```bash
docker compose up -d
```

### Topología de `pedidos-service`

Todos los nombres están centralizados en `application.properties` (`rabbitmq.*`) y los beans
`Queue` / `Exchange` / `Binding` se declaran únicamente en `config/RabbitMQConfig.java`.

| Caso de uso | Exchange (topic) | Routing key | Cola | Cola de mensajes muertos |
|---|---|---|---|---|
| Notificaciones (pedido creado / estado actualizado) | `pedidos.exchange` | `pedidos.routing.key` | `pedidos.queue` | `pedidos.dlq` (key `pedidos.dlq.routing.key`) |
| Tickets (pedido creado) | `pedidos.exchange` | `pedidos.ticket.routing.key` | `pedidos.ticket.queue` | `pedidos.ticket.dlq` (key `pedidos.ticket.dlq.routing.key`) |

Los mensajes rechazados de cualquier cola principal pasan por el Dead Letter Exchange `pedidos.dlx` (direct).

```
messaging/
├── event/         PedidoEvent (JSON)
├── producer/      PedidoProducer  — publica al crear un pedido y al cambiar su estado
├── notificacion/  NotificacionPedidoConsumer   (@RabbitListener sobre pedidos.queue)
├── ticket/        TicketPedidoConsumer         (@RabbitListener sobre pedidos.ticket.queue)
├── dlq/           DeadLetterConsumer           (registra las DLQ en log; apagado por defecto)
└── support/       AckHandler + excepciones + validador del evento
```

### Confirmación de mensajes (ACK) y errores

Los consumidores usan **ACK manual**. `AckHandler` decide para cada mensaje:

| Resultado | Acción |
|---|---|
| Procesado | `basicAck` |
| Error no recuperable (evento inválido) | `basicNack` sin requeue, sin reintentos → DLQ |
| Error recuperable | hasta 3 intentos con espera creciente (`rabbitmq.retry.*`); si fallan todos → `basicNack` → DLQ |

Cada decisión queda registrada en el log. Si RabbitMQ está caído al crear un pedido, el pedido se guarda igual.

### Cómo demostrar la DLQ

1. **Flujo normal:** crear un pedido desde el frontend → en el log de `pedidos-service` aparecen la notificación y el ticket.
2. **Error recuperable:** crear un pedido con un cliente que empiece con `FALLA-` (ej. `FALLA-Ana`) → 3 intentos en el log y luego el mensaje queda en `pedidos.dlq` (consola de RabbitMQ → *Queues*). Se desactiva dejando vacío `rabbitmq.demo.failure-prefix`.
3. **Error no recuperable:** crear un pedido con cantidad `0` → va directo a `pedidos.dlq`, sin reintentos.

### API de `rabbitmq-admin-service` (requiere rol `Admin`)

| Método | Ruta | Descripción | Éxito |
|---|---|---|---|
| `POST` | `/api/admin/queues` | Crear cola | 201 |
| `GET` | `/api/admin/queues/{nombre}` | Mensajes y consumidores de una cola | 200 |
| `DELETE` | `/api/admin/queues/{nombre}` | Eliminar cola | 204 |
| `POST` | `/api/admin/exchanges` | Crear exchange (`direct`, `topic`, `fanout`, `headers`) | 201 |
| `DELETE` | `/api/admin/exchanges/{nombre}` | Eliminar exchange | 204 |
| `POST` | `/api/admin/bindings` | Crear binding | 201 |
| `DELETE` | `/api/admin/bindings?exchange=&destino=&tipoDestino=&routingKey=` | Eliminar binding | 204 |

Errores en JSON: `400` entrada inválida (con detalle por campo), `404` no existe, `409` ya existe, `401`/`403` seguridad, `503` broker caído.

```bash
# Crear una cola con dead-letter (reemplaza $TOKEN por un access token de un usuario con rol Admin)
curl -X POST http://localhost:8082/api/admin/queues \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"demo.queue","deadLetterExchange":"pedidos.dlx","deadLetterRoutingKey":"pedidos.dlq.routing.key"}'

# Crear un exchange y enlazarlo a la cola
curl -X POST http://localhost:8082/api/admin/exchanges -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"nombre":"demo.exchange","tipo":"topic"}'
curl -X POST http://localhost:8082/api/admin/bindings -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"exchange":"demo.exchange","destino":"demo.queue","tipoDestino":"QUEUE","routingKey":"demo.key"}'
```

### Notas de operación

- Si `pedidos.queue` / `pedidos.ticket.queue` ya existían en el broker **sin** los argumentos de dead-letter, `pedidos-service` fallará al arrancar con `PRECONDITION_FAILED`. Elimínalas (consola de RabbitMQ o `DELETE /api/admin/queues/{nombre}`) y reinicia.
- El usuario `guest` de RabbitMQ solo acepta conexiones desde `localhost`. Si el broker corre en otra máquina (ej. una EC2 distinta), crea un usuario propio y pásalo en `RABBITMQ_USER` / `RABBITMQ_PASSWORD`.

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
# Pedidos360 — Backend

Backend del sistema **Pedidos360**, compuesto por cuatro microservicios en **Spring Boot 3 / Java 17**,
cada uno protegido como **OAuth2 Resource Server** que valida JWT emitidos por **Microsoft Entra ID**
(tenant normal — `login.microsoftonline.com`, **no** Entra External ID / CIAM).

```
backend/
├── lanas-service/            (puerto 8080)  Catálogo de lanas
├── pedidos-service/          (puerto 8081)  Pedidos de clientes + productor/consumidores RabbitMQ
├── rabbitmq-admin-service/   (puerto 8082)  API REST para administrar colas, exchanges y bindings
├── kafka-admin-service/      (puerto 8083)  API REST para administrar tópicos, configuraciones y consumer groups
└── docker/
    ├── rabbitmq-cluster/                    docker-compose: clúster RabbitMQ de 2 nodos
    ├── kafka/                               docker-compose: Zookeeper + 3 brokers Kafka
    └── kafka-ui/                            docker-compose: Kafka-UI
```

Los cuatro microservicios comparten el **mismo tenant** y el **mismo App Registration** de Entra ID
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
  | `/api/admin/**` (rabbitmq-admin-service y kafka-admin-service) | solo `ROLE_Admin` |
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
| `RABBITMQ_ADDRESSES` | Clúster RabbitMQ: varias direcciones (tiene prioridad sobre host/port) | `localhost:5672` |
| `KAFKA_BOOTSTRAP_SERVERS` | Brokers de Kafka (`lanas-service`, `pedidos-service`, `kafka-admin-service`) | `localhost:9092,localhost:9093,localhost:9094` |
| `KAFKA_REPLICATION_FACTOR` / `KAFKA_MIN_ISR` | Replicación de `pedidos.eventos` | `3` / `2` |
| `KAFKA_LANAS_REPLICATION_FACTOR` | Replicación de `lanas.eventos` | `2` |
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

```bash
cd kafka-admin-service
AZURE_TENANT_ID=xxxx AZURE_CLIENT_ID=yyyy mvn spring-boot:run
```

> Los servicios necesitan RabbitMQ y Kafka activos para la mensajería (ver las secciones *Mensajería con
> RabbitMQ* y *Streaming con Kafka*). Sin brokers los servicios igual arrancan: crear un pedido o una lana
> sigue funcionando y el error de publicación queda registrado en el log.

Correr las pruebas (no dependen de red ni del tenant real; simulan el JWT ya validado):

```bash
mvn test
```

## Mensajería con RabbitMQ (EP3)

Levantar el clúster de 2 nodos (consolas en <http://localhost:15672> y <http://localhost:15673>, usuario/clave `guest`):

```bash
cd docker/rabbitmq-cluster
docker compose up -d
docker exec pedidos360-rabbitmq1 rabbitmqctl cluster_status   # deben aparecer rabbit@rabbitmq1 y rabbit@rabbitmq2
```

Para que las colas `pedidos.*` se repliquen en ambos nodos (ejecutar una vez, con el clúster arriba):

```bash
docker exec pedidos360-rabbitmq1 rabbitmqctl set_policy ha-pedidos "^pedidos\." \
  '{"ha-mode":"all","ha-sync-mode":"automatic"}' --apply-to queues
```

Los servicios se conectan a ambos nodos con `RABBITMQ_ADDRESSES=localhost:5672,localhost:5673`.

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

### Retención de las DLQ

Una DLQ no debe crecer sin límite. `pedidos.dlq` y `pedidos.ticket.dlq` conservan los mensajes **7 días** y hasta **10.000 mensajes**;
pasado eso RabbitMQ descarta los más antiguos. Se configura con una *policy* del broker (no con argumentos en el código), así se puede
cambiar sin redeclarar las colas. Se ejecuta una vez con el clúster arriba (`deploy/desplegar-ec2.sh` ya lo hace):

```bash
docker exec pedidos360-rabbitmq1 rabbitmqctl set_policy --priority 10 dlq-pedidos '^pedidos\.(ticket\.)?dlq$' \
  '{"ha-mode":"all","ha-sync-mode":"automatic","message-ttl":604800000,"max-length":10000}' --apply-to queues
```

A cada cola le aplica **una sola** policy (la de mayor prioridad), por eso esta repite `ha-mode` y reemplaza a `ha-pedidos` solo para las DLQ.
Revisión operativa: un número creciente de mensajes *Ready* en `pedidos.dlq` indica un problema sistémico (consola de RabbitMQ → *Queues*).

### Notas de operación

- Si `pedidos.queue` / `pedidos.ticket.queue` ya existían en el broker **sin** los argumentos de dead-letter, `pedidos-service` fallará al arrancar con `PRECONDITION_FAILED`. Elimínalas (consola de RabbitMQ o `DELETE /api/admin/queues/{nombre}`) y reinicia.
- El usuario `guest` de RabbitMQ solo acepta conexiones desde `localhost`. Si el broker corre en otra máquina (ej. una EC2 distinta), crea un usuario propio y pásalo en `RABBITMQ_USER` / `RABBITMQ_PASSWORD`.

## Streaming con Kafka (EP5)

Kafka **complementa** a RabbitMQ sin reemplazarlo: RabbitMQ sigue entregando *tareas* (notificar, generar el ticket) y
Kafka guarda el *historial de eventos* para analítica y auditoría. Un mismo pedido genera ambas cosas y cada
publicador falla por separado: si cae uno, el otro y el pedido siguen funcionando.

### Levantar la infraestructura

```bash
cd docker/kafka && docker compose up -d        # Zookeeper + 3 brokers (puertos 9092, 9093, 9094)
cd ../kafka-ui && docker compose up -d         # Kafka-UI en http://localhost:8090
```

Con un solo broker (desarrollo liviano) levanta los servicios con
`KAFKA_REPLICATION_FACTOR=1 KAFKA_MIN_ISR=1 KAFKA_LANAS_REPLICATION_FACTOR=1`.
En EC2, levanta Kafka con `KAFKA_EXTERNAL_HOST=<IP-privada-o-DNS>` para que los microservicios puedan conectarse.

### Tópicos

Cada servicio crea al arrancar el tópico del que es dueño (los parámetros están en `application.properties`, `kafka.topic.*`).

| Tópico | Dueño (productor) | Consumidores (grupo) | Particiones | Réplicas | Retención / limpieza |
|---|---|---|---|---|---|
| `pedidos.eventos` | `pedidos-service` | `lanas-service` (`lanas-analitica`), `pedidos-service` (`pedidos-auditoria`) | 3 | 3 (min ISR 2) | 7 días, `cleanup.policy=delete` |
| `lanas.eventos` | `lanas-service` | `pedidos-service` (`pedidos-catalogo`) | 2 | 2 | `cleanup.policy=compact` (último evento por lana) |

La *key* de cada mensaje es el id del pedido / de la lana: así los eventos de una misma entidad conservan su orden.

### Consumer groups

- **`lanas-analitica`**: calcula en tiempo real las unidades vendidas por lana (`GET /api/analitica/ventas`). Sus 3 hilos se reparten las 3 particiones.
- **`pedidos-auditoria`**: bitácora de los últimos eventos de pedidos.
- **`pedidos-catalogo`**: copia local del catálogo de lanas.
- Grupos distintos sobre el mismo tópico reciben **todos** los eventos; hilos o instancias del **mismo** grupo se reparten las particiones.

### Estructura del código (`pedidos-service` y `lanas-service`)

```
kafka/
├── config/     KafkaConfig   — tópico propio + política de errores
├── dto/        eventos tipados (JSON)
├── producer/   KafkaTemplate con acks=all e idempotencia
├── consumer/   @KafkaListener: recibe, valida y delega
└── service/    lógica de dominio (sin código de Kafka)
```

El cliente (brokers, serializers, deserializers, offsets) está en `spring.kafka.*` de cada `application.properties`.

### Manejo de errores

| Situación | Qué pasa |
|---|---|
| Evento inválido (cantidad ≤ 0, campos faltantes) | `EventoInvalidoException`: no se reintenta; se registra en el log y se descarta |
| JSON corrupto | `ErrorHandlingDeserializer`: no se reintenta ni bloquea la partición |
| Error temporal | 3 intentos con 1 s de espera; luego se registra en el log (topic, partición, offset, payload) y se continúa |
| Kafka caído al publicar | el pedido/lana igual se guarda; queda el error en el log |

### Cómo demostrarlo

1. Crear un pedido: en el log de `pedidos-service` aparece `[kafka] Evento publicado`, y en `lanas-service` `[analitica]` y en `pedidos-service` `[auditoria]`. `GET /api/analitica/ventas` muestra las unidades por lana.
2. Crear una lana (rol Admin): `pedidos-service` registra `[catalogo]` con la lana nueva.
3. Pedido con cliente `FALLA-Ana`: la analítica reintenta 3 veces y descarta el mensaje (en el log); el resto sigue funcionando.
4. Pedido con cantidad `0`: se descarta sin reintentos.
5. En Kafka-UI se ven los tópicos, sus mensajes y el estado de los consumer groups.

### API de `kafka-admin-service` (requiere rol `Admin`)

| Método | Ruta | Descripción | Éxito |
|---|---|---|---|
| `POST` | `/api/admin/kafka/topics` | Crear tópico (nombre, particiones, replicas, configuraciones) | 201 |
| `GET` | `/api/admin/kafka/topics` | Listar tópicos | 200 |
| `GET` | `/api/admin/kafka/topics/{nombre}` | Particiones (líder, réplicas, ISR) y configuración | 200 |
| `PUT` | `/api/admin/kafka/topics/{nombre}/config` | Modificar configuraciones (`retention.ms`, `cleanup.policy`...) | 200 |
| `DELETE` | `/api/admin/kafka/topics/{nombre}/config/{clave}` | Restablecer una configuración a su valor por defecto | 204 |
| `PATCH` | `/api/admin/kafka/topics/{nombre}/partitions` | Aumentar particiones | 200 |
| `DELETE` | `/api/admin/kafka/topics/{nombre}` | Eliminar tópico | 204 |
| `GET` | `/api/admin/kafka/consumer-groups` | Listar consumer groups | 200 |
| `GET` | `/api/admin/kafka/consumer-groups/{id}` | Estado, miembros, offsets y lag por partición | 200 |

Errores en JSON: `400` entrada inválida (con detalle por campo), `404` no existe, `409` ya existe, `401`/`403` seguridad, `503` Kafka caído.

```bash
# Crear un tópico con retención de 1 día
curl -X POST http://localhost:8083/api/admin/kafka/topics -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"demo.eventos","particiones":3,"replicas":3,"configuraciones":{"retention.ms":"86400000"}}'

# Cambiar la retención a 7 días y ver el resultado
curl -X PUT http://localhost:8083/api/admin/kafka/topics/demo.eventos/config -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"configuraciones":{"retention.ms":"604800000"}}'
curl http://localhost:8083/api/admin/kafka/topics/demo.eventos -H "Authorization: Bearer $TOKEN"

# Lag de un consumer group
curl http://localhost:8083/api/admin/kafka/consumer-groups/lanas-analitica -H "Authorization: Bearer $TOKEN"
```

### Notas de operación

- Los brokers tienen `auto.create.topics.enable=false`: los tópicos los crean los servicios al arrancar o `kafka-admin-service`.
- Kafka solo permite **aumentar** el número de particiones, nunca reducirlo.
- Si cambias a mano la retención de `pedidos.eventos`, un reinicio de `pedidos-service` no la revierte (KafkaAdmin solo crea tópicos que no existen).

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
### Despliegue en AWS (EP5)

Todo el backend corre en **una EC2** (`t3.large`, Ubuntu 24.04, IP elástica) con **RDS MySQL** como base de datos y
**API Gateway (HTTP API)** como fachada con authorizer JWT de Entra ID:

| Pieza | Dónde corre | Puerto |
|---|---|---|
| `lanas-service` / `pedidos-service` / `rabbitmq-admin-service` / `kafka-admin-service` | systemd en la EC2 | 8080 / 8081 / 8082 / 8083 |
| RabbitMQ (clúster de 2 nodos), Kafka (Zookeeper + 3 brokers), Kafka-UI | Docker Compose en la EC2 | 5672-5673, 9092-9094, 8090 |
| MySQL (`manitadb`, `pedidosdb`) | Amazon RDS (solo accesible desde el security group de la EC2) | 3306 |

Rutas del API Gateway: `/api/lanas`, `/api/pedidos`, `/api/analitica/*` (→ lanas-service), `/api/admin/*` (→ rabbitmq-admin-service)
y `/api/admin/kafka/*` (→ kafka-admin-service; API Gateway elige la ruta más específica).

```bash
git clone https://github.com/Benjvlifts/backend-manita-crochet-web.git ~/ep5-backend
bash ~/ep5-backend/deploy/desplegar-ec2.sh <endpoint-RDS>   # pide el password de RDS; se puede repetir
```

El script amplía el disco, crea swap, levanta los 3 compose con la memoria de Java acotada, compila los 4 servicios y los
deja como servicios systemd. Las credenciales quedan en `/etc/pedidos360/` (permisos 600), fuera del repositorio.
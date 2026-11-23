#!/usr/bin/env bash
# =====================================================================================
# Despliegue de Pedidos360 (EP5) en una EC2 Ubuntu 24.04 — se ejecuta DENTRO de la EC2.
#
#   git clone https://github.com/Benjvlifts/backend-manita-crochet-web.git ~/ep5-backend
#   bash ~/ep5-backend/deploy/desplegar-ec2.sh <endpoint-RDS>
#
# Qué hace (se puede repetir sin problema, es idempotente):
#   1. Amplía el disco si el volumen creció, crea 2 GB de swap, instala JDK 17, Maven y Docker si faltan.
#   2. Detiene los servicios y el RabbitMQ de un solo nodo de entregas anteriores (EP3/EP4).
#   3. Levanta los 3 compose del proyecto: clúster RabbitMQ, Kafka (Zookeeper + 3 brokers) y Kafka-UI,
#      con la memoria de Java acotada para que quepan en una t3.large (8 GB).
#   4. Compila los 4 microservicios y los deja como servicios systemd (se reinician solos).
# Secretos: el password de RDS se pide por teclado y queda en /etc/pedidos360/ (permisos 600), nunca en el repo.
# =====================================================================================
set -euo pipefail

RDS_HOST="${1:-${RDS_HOST:-}}"
REPO_DIR="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
APP_USER="${APP_USER:-ubuntu}"
CONF_DIR=/etc/pedidos360
AZURE_TENANT_ID="${AZURE_TENANT_ID:-522bdf93-3bcf-46ce-83e7-ec4538db5496}"
AZURE_CLIENT_ID="${AZURE_CLIENT_ID:-3e1ac9d8-06dd-4ed2-a236-5d8397ad4944}"
# Orígenes permitidos por CORS en los microservicios (el frontend corre con ng serve en localhost:4200)
CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-http://localhost:4200}"

paso() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
fallo() { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" -ne 0 ] || fallo "No lo ejecutes con sudo: usa el usuario ${APP_USER} (el script pide sudo cuando lo necesita)."
[ -d "$REPO_DIR/kafka-admin-service" ] || fallo "No encuentro los microservicios en $REPO_DIR"
[ -n "$RDS_HOST" ] || fallo "Falta el endpoint de RDS. Uso: bash deploy/desplegar-ec2.sh <endpoint-RDS>"

if [ -z "${DB_PASSWORD:-}" ]; then
  read -rsp "Password de la RDS (usuario admin): " DB_PASSWORD; echo
fi
[ -n "$DB_PASSWORD" ] || fallo "El password de RDS no puede estar vacío"
case "$DB_PASSWORD" in *[[:space:]\"\'\\\$\`]*) fallo "El password no debe llevar espacios, comillas, \\ , \$ ni comillas invertidas";; esac

# ------------------------------------------------------------------ 1. Sistema
paso "1/6 Sistema: disco, swap y herramientas"
ROOT_DEV=$(lsblk -rno NAME,MOUNTPOINT | awk '$2=="/"{print "/dev/"$1; exit}')   # p. ej. /dev/nvme0n1p1
[ -b "$ROOT_DEV" ] || fallo "No pude detectar el disco raíz"
DISK_DEV="/dev/$(lsblk -no PKNAME "$ROOT_DEV")"
PART_NUM="${ROOT_DEV##*[!0-9]}"
sudo apt-get install -y -qq cloud-guest-utils >/dev/null
sudo growpart "$DISK_DEV" "$PART_NUM" >/dev/null 2>&1 || true   # "NOCHANGE" también es éxito
sudo resize2fs "$ROOT_DEV" >/dev/null 2>&1 || true
df -h / | tail -1
LIBRE_GB=$(df -BG --output=avail / | tail -1 | tr -dc '0-9')
MEM_MB=$(free -m | awk '/^Mem:/{print $2}')
[ "$LIBRE_GB" -ge 6 ] || fallo "Solo hay ${LIBRE_GB} GB libres. Amplía el volumen EBS a 20 GB (aws ec2 modify-volume) y reinicia la EC2."
[ "$MEM_MB" -ge 6500 ] || fallo "Esta EC2 tiene ${MEM_MB} MB de RAM y el proyecto necesita ~5 GB. Cámbiala a t3.large (8 GB)."

if ! swapon --show | grep -q /swapfile; then
  sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile >/dev/null && sudo swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

command -v javac >/dev/null || sudo apt-get install -y -qq openjdk-17-jdk-headless >/dev/null
command -v mvn   >/dev/null || sudo apt-get install -y -qq maven >/dev/null
docker compose version >/dev/null 2>&1 || sudo apt-get install -y -qq docker-compose-v2 >/dev/null
sudo usermod -aG docker "$APP_USER" || true
D="sudo docker"          # con sudo: así funciona aunque el grupo docker aún no se haya recargado
java -version 2>&1 | head -1; mvn -v | head -1; $D compose version

# ------------------------------------------------------------------ 2. Limpiar entregas anteriores
paso "2/6 Detener servicios y RabbitMQ de entregas anteriores"
mkdir -p "$HOME/respaldo-units"
for s in lanas-service pedidos-service rabbitmq-admin-service kafka-admin-service; do
  if [ -f "/etc/systemd/system/$s.service" ]; then
    cp -n "/etc/systemd/system/$s.service" "$HOME/respaldo-units/" 2>/dev/null || true
    sudo systemctl stop "$s" 2>/dev/null || true
  fi
done
# El contenedor de un solo nodo de EP3 usa los mismos puertos (5672/15672) que el nodo 1 del clúster.
$D rm -f pedidos360-rabbitmq >/dev/null 2>&1 || true

# ------------------------------------------------------------------ 3. Infraestructura (Docker)
paso "3/6 RabbitMQ (clúster de 2 nodos), Kafka (Zookeeper + 3 brokers) y Kafka-UI"
OVR="$HOME/ep5-overrides"; mkdir -p "$OVR"
# Los brokers anuncian "localhost": los servicios corren en esta misma EC2, así que no hace falta abrir 9092-9094.
# Memoria acotada (los valores por defecto de Kafka son 1 GB de heap por broker). No se toca el compose del repo.
cat > "$OVR/kafka.override.yml" <<'YML'
services:
  zookeeper:
    environment: { KAFKA_HEAP_OPTS: "-Xms128m -Xmx256m" }
  kafka1:
    environment: { KAFKA_HEAP_OPTS: "-Xms256m -Xmx384m" }
  kafka2:
    environment: { KAFKA_HEAP_OPTS: "-Xms256m -Xmx384m" }
  kafka3:
    environment: { KAFKA_HEAP_OPTS: "-Xms256m -Xmx384m" }
YML
cat > "$OVR/kafka-ui.override.yml" <<'YML'
services:
  kafka-ui:
    environment: { JAVA_OPTS: "-Xms128m -Xmx384m" }
YML

( cd "$REPO_DIR/docker/rabbitmq-cluster" && $D compose up -d )
( cd "$REPO_DIR/docker/kafka" && $D compose -f docker-compose.yml -f "$OVR/kafka.override.yml" up -d )
( cd "$REPO_DIR/docker/kafka-ui" && $D compose -f docker-compose.yml -f "$OVR/kafka-ui.override.yml" up -d )

echo -n "Esperando a los 3 brokers de Kafka"
for _ in $(seq 1 60); do
  n=$($D exec pedidos360-kafka1 kafka-broker-api-versions --bootstrap-server kafka1:29092 2>/dev/null | grep -c '(id: ' || true)
  [ "${n:-0}" -ge 3 ] && break; echo -n "."; sleep 5
done; echo
[ "${n:-0}" -ge 3 ] || fallo "Kafka no levantó los 3 brokers. Revisa: sudo docker logs pedidos360-kafka1"

echo -n "Esperando al clúster RabbitMQ"
for _ in $(seq 1 40); do
  $D exec pedidos360-rabbitmq1 rabbitmqctl cluster_status 2>/dev/null | grep -q rabbit@rabbitmq2 && break; echo -n "."; sleep 5
done; echo
# Alta disponibilidad de las colas "pedidos.*" en ambos nodos (igual que indica el README)
$D exec pedidos360-rabbitmq1 rabbitmqctl set_policy ha-pedidos '^pedidos\.' '{"ha-mode":"all","ha-sync-mode":"automatic"}' --apply-to queues >/dev/null
# Retención de las DLQ: 7 días y máximo 10.000 mensajes (así no crecen sin límite). A una cola solo le aplica UNA política
# (la de mayor prioridad), por eso esta repite el ha-mode y gana (prioridad 10) sobre ha-pedidos para pedidos.dlq y pedidos.ticket.dlq.
$D exec pedidos360-rabbitmq1 rabbitmqctl set_policy --priority 10 dlq-pedidos '^pedidos\.(ticket\.)?dlq$' '{"ha-mode":"all","ha-sync-mode":"automatic","message-ttl":604800000,"max-length":10000}' --apply-to queues >/dev/null

# ------------------------------------------------------------------ 4. Compilar
paso "4/6 Compilar los 4 microservicios (sin pruebas; ya pasan en tu PC)"
for s in kafka-admin-service lanas-service pedidos-service rabbitmq-admin-service; do
  echo "--- $s"
  ( cd "$REPO_DIR/$s" && mvn -q -B -DskipTests clean package )
done

# ------------------------------------------------------------------ 5. Configuración y systemd
paso "5/6 Configuración (/etc/pedidos360) y servicios systemd"
sudo mkdir -p "$CONF_DIR"
sudo tee "$CONF_DIR/common.env" >/dev/null <<ENV
AZURE_TENANT_ID=$AZURE_TENANT_ID
AZURE_CLIENT_ID=$AZURE_CLIENT_ID
CORS_ALLOWED_ORIGINS=$CORS_ALLOWED_ORIGINS
KAFKA_BOOTSTRAP_SERVERS=localhost:9092,localhost:9093,localhost:9094
RABBITMQ_ADDRESSES=localhost:5672,localhost:5673
DB_USER=admin
DB_PASSWORD=$DB_PASSWORD
ENV
echo "DB_URL=jdbc:mysql://$RDS_HOST:3306/manitadb?createDatabaseIfNotExist=true" | sudo tee "$CONF_DIR/lanas-service.env" >/dev/null
echo "DB_URL=jdbc:mysql://$RDS_HOST:3306/pedidosdb?createDatabaseIfNotExist=true" | sudo tee "$CONF_DIR/pedidos-service.env" >/dev/null
sudo touch "$CONF_DIR/rabbitmq-admin-service.env" "$CONF_DIR/kafka-admin-service.env"
sudo chmod 600 "$CONF_DIR"/*.env

echo -n "Conexión a RDS ($RDS_HOST:3306): "
timeout 5 bash -c "echo > /dev/tcp/$RDS_HOST/3306" 2>/dev/null && echo OK \
  || echo "SIN RESPUESTA (revisa que el SG de la RDS acepte 3306 desde el SG de esta EC2 y que la RDS esté 'available')"

# Tras reiniciar la EC2, Kafka tarda en estar listo: este script espera a su puerto antes de arrancar un servicio.
sudo tee /usr/local/bin/esperar-kafka.sh >/dev/null <<'WAIT'
#!/usr/bin/env bash
for _ in $(seq 1 60); do (echo > /dev/tcp/127.0.0.1/9092) 2>/dev/null && exit 0; sleep 3; done
exit 0
WAIT
sudo chmod +x /usr/local/bin/esperar-kafka.sh

crear_unidad() {   # $1 = servicio, $2 = heap máximo, $3 = ¿espera a Kafka? (si/no)
  local s=$1 heap=$2 espera=$3 jar pre=""
  jar=$(ls "$REPO_DIR/$s"/target/*.jar | head -1)
  [ "$espera" = si ] && pre="ExecStartPre=/usr/local/bin/esperar-kafka.sh"
  sudo tee "/etc/systemd/system/$s.service" >/dev/null <<UNIT
[Unit]
Description=Pedidos360 $s (EP5)
After=network-online.target docker.service
Wants=network-online.target

[Service]
User=$APP_USER
WorkingDirectory=$REPO_DIR/$s
EnvironmentFile=$CONF_DIR/common.env
EnvironmentFile=$CONF_DIR/$s.env
$pre
ExecStart=/usr/bin/java -Xms128m -Xmx$heap -XX:+UseSerialGC -jar $jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
TimeoutStartSec=300

[Install]
WantedBy=multi-user.target
UNIT
}
crear_unidad lanas-service          384m si
crear_unidad pedidos-service        384m si
crear_unidad kafka-admin-service    256m si
crear_unidad rabbitmq-admin-service 256m no

sudo systemctl daemon-reload
sudo systemctl enable --now kafka-admin-service lanas-service pedidos-service rabbitmq-admin-service

# ------------------------------------------------------------------ 6. Verificación
paso "6/6 Verificación (los servicios Java tardan ~1 minuto en arrancar)"
for par in lanas-service:8080 pedidos-service:8081 rabbitmq-admin-service:8082 kafka-admin-service:8083; do
  s=${par%%:*}; p=${par##*:}; ok=no
  for _ in $(seq 1 40); do
    curl -fs "http://localhost:$p/api/public/health" >/dev/null 2>&1 && { ok=si; break; }; sleep 4
  done
  printf '%-24s puerto %s  salud: %s  systemd: %s\n' "$s" "$p" "$ok" "$(systemctl is-active "$s")"
done
echo; $D ps --format 'table {{.Names}}\t{{.Status}}'
echo; $D exec pedidos360-kafka1 kafka-topics --bootstrap-server kafka1:29092 --list || true
echo; free -m | sed -n '1,3p'; df -h / | tail -1
echo
echo "Listo. Kafka-UI: http://<IP-elastica>:8090 (solo desde tu IP). Logs: journalctl -u pedidos-service -f"
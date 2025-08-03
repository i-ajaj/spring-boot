import pika
import os
import logging

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
)

logger = logging.getLogger(__name__)

logger.info("🐍 Python worker is starting...")

RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "rabbitmq")
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "guest")
RABBITMQ_PASS = os.getenv("RABBITMQ_PASS", "guest")


def callback(ch, method, properties, body):
    logger.info("📩 Received: %s", body.decode())


connection = None
for attempt in range(20):
    try:
        logger.info("🔁 Attempt %d to connect to RabbitMQ at %s", attempt + 1, RABBITMQ_HOST)
        credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
        parameters = pika.ConnectionParameters(host=RABBITMQ_HOST, credentials=credentials)
        connection = pika.BlockingConnection(parameters)
        logger.info("✅ Connected to RabbitMQ.")
        break
    except pika.exceptions.AMQPConnectionError:
        logger.warning("❌ RabbitMQ not ready, retrying...")

if connection is None:
    logger.error("❌ Could not connect to RabbitMQ after 20 attempts.")
    exit(1)

channel = connection.channel()
channel.queue_declare(queue='task-queue')

channel.basic_consume(
    queue='task-queue',
    on_message_callback=callback,
    auto_ack=True
)

logger.info("🐍 Waiting for messages...")
channel.start_consuming()

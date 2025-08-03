FROM python:3.10-slim
WORKDIR /app
COPY worker.py .
RUN pip install pika
CMD ["python3", "worker.py"]

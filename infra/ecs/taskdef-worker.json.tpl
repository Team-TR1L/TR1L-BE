{
  "family": "td-worker",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "taskRoleArn": "${TASK_ROLE_ARN}",
  "executionRoleArn": "${AWS_EXE_ROLE_ARN}",
  "containerDefinitions": [
    {
      "name": "worker",
      "image": "${WORKER_IMAGE_URI}",
      "cpu": 0,
      "portMappings": [],
      "essential": true,
      "environment": [
        { "name": "PG_SUB_DB", "value": "${PG_SUB_DB}" },
        { "name": "PG_SUB_USER", "value": "${PG_SUB_USER}" },
        { "name": "PG_MAIN_PASSWORD", "value": "${PG_MAIN_PASSWORD}" },
        { "name": "PG_SUB_HOST", "value": "${PG_SUB_HOST}" },
        { "name": "PG_SUB_PORT", "value": "5432" },
        { "name": "PG_MAIN_HOST", "value": "${PG_MAIN_HOST}" },
        { "name": "PG_MAIN_PORT", "value": "5432" },
        { "name": "PG_SUB_PASSWORD", "value": "${PG_SUB_PASSWORD}" },
        { "name": "PG_MAIN_DB", "value": "${PG_MAIN_DB}" },
        { "name": "PG_MAIN_USER", "value": "${PG_MAIN_USER}" },

        { "name": "S3_BUCKET", "value": "${S3_BUCKET}" },
        { "name": "AWS_ACCESS_KEY_ID", "value": "${AWS_ACCESS_KEY_ID}" },
        { "name": "AWS_SECRET_ACCESS_KEY", "value": "${AWS_SECRET_ACCESS_KEY}" },
        { "name": "S3_MAX_CONNECTIONS", "value": "200" },
        { "name": "S3_UPLOAD_CONCURRENCY", "value": "32" },
        { "name": "S3_UPLOAD_QUEUE_CAPACITY", "value": "5000" },

        { "name": "SPRING_PROFILES_ACTIVE", "value": "prod" },
        { "name": "BATCH_JOB1_STEP1_FETCH_SIZE", "value": "2000" },
        { "name": "BATCH_JOB1_STEP1_CHUNK_SIZE", "value": "2000" },
        { "name": "BATCH_JOB1_STEP3_FETCH_SIZE", "value": "6000" },
        { "name": "BATCH_JOB1_STEP3_CHUNK_SIZE", "value": "3000" },
        { "name": "BATCH_JOB2_STEP1_BATCH_SIZE", "value": "6000" },
        { "name": "BATCH_JOB2_STEP1_CHUNK_SIZE", "value": "6000" },
        { "name": "BATCH_JOB1_STEP3_LEASE_SECONDS", "value": "3000" },
        { "name": "SPRING_JPA_HIBERNATE_DDL_AUTO", "value": "update" },
        { "name": "SPRING_JPA_SHOW_SQL", "value": "false" },
        { "name": "SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL", "value": "false" },

        { "name": "SECRET_KEY", "value": "${SECRET_KEY}" },
        { "name": "ALGORITHM", "value": "${ALGORITHM}" },
        { "name": "TRANSFORMATION", "value": "${TRANSFORMATION}" },

        { "name": "CHANNEL_ORDER", "value": "${CHANNEL_ORDER}" },

        { "name": "OTEL_SERVICE_NAME", "value": "worker" },
        { "name": "OTEL_EXPORTER_OTLP_ENDPOINT", "value": "http://10.0.1.156:4318" },
        { "name": "OTEL_EXPORTER_OTLP_PROTOCOL", "value": "http/protobuf" },
        { "name": "OTEL_TRACES_EXPORTER", "value": "otlp" },
        { "name": "OTEL_METRICS_EXPORTER", "value": "none" },
        { "name": "OTEL_LOGS_EXPORTER", "value": "none" },
        { "name": "OTEL_RESOURCE_ATTRIBUTES", "value": "deployment.environment=prod,service.namespace=tr1l" },

        { "name": "PYROSCOPE_SERVER_ADDRESS", "value": "http://10.0.1.156:4040" },
        { "name": "PYROSCOPE_APPLICATION_NAME", "value": "worker" },
        { "name": "PYROSCOPE_PROFILING_INTERVAL", "value": "10s" }
      ],
      "secrets": [
        {
          "name": "MONGODB_URI",
          "valueFrom": "${MONGODB_URI}"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/td-worker",
          "awslogs-create-group": "true",
          "awslogs-region": "${AWS_REGION}",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}

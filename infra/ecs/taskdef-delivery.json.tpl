{
  "family": "td-delivery",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "taskRoleArn": "${TASK_ROLE_ARN}",
  "executionRoleArn": "${AWS_EXE_ROLE_ARN}",
  "containerDefinitions": [
    {
      "name": "delivery",
      "image": "${DELIVERY_IMAGE_URI}",
      "cpu": 0,
      "portMappings": [
      ],
      "essential": true,
      "environment": [
        { "name": "PG_SUB_DB", "value": "${PG_SUB_DB}" },
        { "name": "S3_BUCKET", "value": "${S3_BUCKET}" },
        { "name": "PG_SUB_USER", "value": "${PG_SUB_USER}" },
        { "name": "PG_SUB_HOST", "value": "${PG_SUB_HOST}" },
        { "name": "PG_SUB_PORT", "value": "5432" },
        { "name": "PG_SUB_PASSWORD", "value": "${PG_SUB_PASSWORD}" },
        { "name": "SPRING_JPA_HIBERNATE_DDL_AUTO", "value": "update" },
        { "name": "SPRING_JPA_SHOW_SQL", "value": "false" },
        { "name": "SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL", "value": "false" },

        { "name": "AWS_ACCESS_KEY_ID", "value": "${AWS_ACCESS_KEY_ID}" },
        { "name": "AWS_SECRET_ACCESS_KEY", "value": "${AWS_SECRET_ACCESS_KEY}" },

        { "name": "SPRING_PROFILES_ACTIVE", "value": "prod" },
        { "name": "KAFKA_CONSUMER_GROUP_ID", "value": "dispatch-server" },
        { "name": "SPRING_KAFKA_BOOTSTRAP_SERVERS", "value": "${SPRING_KAFKA_BOOTSTRAP_SERVERS}" },
        { "name": "KAFKA_TOPIC_DISPATCH_EVENTS", "value": "dispatch-events-v1" },
        { "name": "KAFKA_ACKS", "value": "all" },

        { "name": "KAFKA_AUTO_OFFSET_RESET", "value": "earliest" },
        { "name": "KAFKA_TOPIC_DELIVERY_RESULT_EVENTS", "value": "delivery-result-events-v1" },
        { "name": "KAFKA_GROUP_DELIVERY", "value": "delivery-group" },
        { "name": "KAFKA_GROUP_DELIVERY_RESULT_HANDLER", "value": "delivery-result-handler-group" },

        { "name": "SECRET_KEY", "value": "${SECRET_KEY}" },
        { "name": "ALGORITHM", "value": "${ALGORITHM}" },
        { "name": "TRANSFORMATION", "value": "${TRANSFORMATION}" },

        { "name": "OTEL_SERVICE_NAME", "value": "delivery" },
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
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/td-delivery",
          "awslogs-create-group": "true",
          "awslogs-region": "${AWS_REGION}",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}

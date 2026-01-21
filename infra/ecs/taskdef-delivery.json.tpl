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

        { "name": "SPRING_PROFILES_ACTIVE", "value": "prod" },
        { "name": "KAFKA_CONSUMER_GROUP_ID", "value": "dispatch-server" },
        { "name": "SPRING_KAFKA_BOOTSTRAP_SERVERS", "value": "${SPRING_KAFKA_BOOTSTRAP_SERVERS}" },
        { "name": "KAFKA_TOPIC_DISPATCH_EVENTS", "value": "dispatch-events-v1" },
        { "name": "KAFKA_ACKS", "value": "all" },

        { "name": "KAFKA_AUTO_OFFSET_RESET", "value": "earliest" },
        { "name": "KAFKA_TOPIC_DELIVERY_RESULT_EVENTS", "value": "delivery-result-events-v1" },
        { "name": "KAFKA_GROUP_DELIVERY", "value": "delivery-group" },
        { "name": "KAFKA_GROUP_DELIVERY_RESULT_HANDLER", "value": "delivery-result-handler-group" },
        { "name": "SECRET_KEY", "value": "${SECRET_KEY}" }
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

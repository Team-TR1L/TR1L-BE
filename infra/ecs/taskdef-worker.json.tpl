{
  "family": "td-worker",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "3072",
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
        { "name": "S3_BUCKET", "value": "${S3_BUCKET}" },
        { "name": "PG_SUB_USER", "value": "${PG_SUB_USER}" },
        { "name": "PG_MAIN_PASSWORD", "value": "${PG_MAIN_PASSWORD}" },
        { "name": "PG_SUB_HOST", "value": "${PG_SUB_HOST}" },
        { "name": "PG_SUB_PORT", "value": "5432" },
        { "name": "PG_MAIN_HOST", "value": "${PG_MAIN_HOST}" },
        { "name": "PG_MAIN_PORT", "value": "5432" },
        { "name": "PG_SUB_PASSWORD", "value": "${PG_SUB_PASSWORD}" },
        { "name": "PG_MAIN_DB", "value": "${PG_MAIN_DB}" },
        { "name": "PG_MAIN_USER", "value": "${PG_MAIN_USER}" },

        { "name": "SPRING_PROFILES_ACTIVE", "value": "prod" },
        { "name": "BATCH_JOB1_STEP1_FETCH_SIZE", "value": "1000" },
        { "name": "BATCH_JOB1_STEP1_CHUNK_SIZE", "value": "1000" },
        { "name": "SPRING_JPA_HIBERNATE_DDL_AUTO", "value": "update" },
        { "name": "SPRING_JPA_SHOW_SQL", "value": "false" },
        { "name": "SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL", "value": "false" },

        { "name": "ALGORITHM", "value": "${ALGORITHM}" },
        { "name": "TRANSFORMATION", "value": "${TRANSFORMATION}" },

        { "name": "CHANNEL_ORDER", "value": "${CHANNEL_ORDER}" }
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

# Deployment Guide

This guide covers deploying the notifications service to various environments.

## Table of Contents
1. [Docker Containerization](#docker-containerization)
2. [Kubernetes Deployment](#kubernetes-deployment)
3. [AWS Deployment](#aws-deployment)
4. [Environment Configuration](#environment-configuration)
5. [Production Checklist](#production-checklist)

## Docker Containerization

### Building Docker Image

The project includes a Dockerfile. To build the image:

```bash
# Build image with Maven
mvn clean package

# Build Docker image
docker build -t notifications-service:latest .

# Verify image was built
docker images | grep notifications-service
```

### Running Docker Container

```bash
# Run locally (requires PostgreSQL and Kafka on same network)
docker run -d \
  --name notifications-service \
  -p 8080:80 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/notifications_db \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e CLIENT_MEMBERS_URL=http://members-service:8081/api/members/ \
  --network notifications-network \
  notifications-service:latest

# View logs
docker logs -f notifications-service
```

### Docker Compose for Full Stack

See `docker-compose.yml` for complete setup with all dependencies.

```bash
# Start entire stack
docker-compose up -d

# View all services
docker-compose ps

# Stop services
docker-compose down

# Clean up (remove volumes)
docker-compose down -v
```

## Kubernetes Deployment

### Prerequisites
- Kubernetes cluster (1.18+)
- kubectl configured
- Docker image pushed to registry
- PostgreSQL and Kafka running

### Deployment Manifest

Create `k8s/deployment.yaml`:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: notifications

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: notifications-service
  namespace: notifications
spec:
  replicas: 3
  selector:
    matchLabels:
      app: notifications-service
  template:
    metadata:
      labels:
        app: notifications-service
    spec:
      containers:
      - name: notifications-service
        image: your-registry/notifications-service:1.0.0
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 80
          name: http
        env:
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            configMapKeyRef:
              name: notifications-config
              key: db.url
        - name: SPRING_DATASOURCE_USERNAME
          valueFrom:
            secretKeyRef:
              name: notifications-secrets
              key: db.username
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: notifications-secrets
              key: db.password
        - name: SPRING_KAFKA_BOOTSTRAP_SERVERS
          valueFrom:
            configMapKeyRef:
              name: notifications-config
              key: kafka.brokers
        - name: CLIENT_MEMBERS_URL
          valueFrom:
            configMapKeyRef:
              name: notifications-config
              key: members.service.url
        resources:
          requests:
            cpu: 250m
            memory: 512Mi
          limits:
            cpu: 500m
            memory: 1Gi
        livenessProbe:
          httpGet:
            path: /notifications-service/actuator/health
            port: 80
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /notifications-service/actuator/health/readiness
            port: 80
          initialDelaySeconds: 20
          periodSeconds: 5
          timeoutSeconds: 5
          failureThreshold: 3

---
apiVersion: v1
kind: Service
metadata:
  name: notifications-service
  namespace: notifications
spec:
  type: ClusterIP
  ports:
  - port: 80
    targetPort: 80
    protocol: TCP
    name: http
  selector:
    app: notifications-service

---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: notifications-service-hpa
  namespace: notifications
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: notifications-service
  minReplicas: 2
  maxReplicas: 5
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80

---
apiVersion: v1
kind: ConfigMap
metadata:
  name: notifications-config
  namespace: notifications
data:
  db.url: "jdbc:postgresql://postgres-service:5432/notifications_db"
  kafka.brokers: "kafka-cluster:9092"
  members.service.url: "http://members-service:8081/api/members/"

---
apiVersion: v1
kind: Secret
metadata:
  name: notifications-secrets
  namespace: notifications
type: Opaque
stringData:
  db.username: postgres
  db.password: change-me-in-production
```

### Deployment Commands

```bash
# Create namespace and deploy
kubectl apply -f k8s/deployment.yaml

# Check deployment status
kubectl get deployments -n notifications
kubectl get pods -n notifications
kubectl get svc -n notifications

# View logs
kubectl logs -n notifications deployment/notifications-service

# Tail logs
kubectl logs -n notifications deployment/notifications-service -f

# Port forward for testing
kubectl port-forward -n notifications svc/notifications-service 8080:80

# Scale up/down
kubectl scale deployment notifications-service -n notifications --replicas=5

# Update image
kubectl set image deployment/notifications-service \
  -n notifications \
  notifications-service=your-registry/notifications-service:1.1.0

# Rollback
kubectl rollout undo deployment/notifications-service -n notifications

# Monitor with kubectl top
kubectl top pods -n notifications
```

## AWS Deployment

### Using ECS (Elastic Container Service)

1. **Push image to ECR**:

```bash
# Create ECR repository
aws ecr create-repository --repository-name notifications-service

# Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Tag and push image
docker tag notifications-service:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/notifications-service:latest
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/notifications-service:latest
```

2. **Create RDS PostgreSQL instance**:

```bash
aws rds create-db-instance \
  --db-instance-identifier notifications-db \
  --db-instance-class db.t3.micro \
  --engine postgres \
  --master-username postgres \
  --master-user-password your-secure-password \
  --allocated-storage 20 \
  --vpc-security-group-ids sg-xxxxx
```

3. **Create ECS Task Definition**:

```json
{
  "family": "notifications-service",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "containerDefinitions": [
    {
      "name": "notifications-service",
      "image": "<account-id>.dkr.ecr.us-east-1.amazonaws.com/notifications-service:latest",
      "portMappings": [
        {
          "containerPort": 80,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "SPRING_DATASOURCE_URL",
          "value": "jdbc:postgresql://notifications-db.xxxxx.us-east-1.rds.amazonaws.com:5432/notifications_db"
        },
        {
          "name": "SPRING_DATASOURCE_USERNAME",
          "value": "postgres"
        },
        {
          "name": "SPRING_KAFKA_BOOTSTRAP_SERVERS",
          "value": "your-msk-brokers:9092"
        }
      ],
      "secrets": [
        {
          "name": "SPRING_DATASOURCE_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:account-id:secret:db-password"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/notifications-service",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

4. **Create ECS Service**:

```bash
aws ecs create-service \
  --cluster notifications-cluster \
  --service-name notifications-service \
  --task-definition notifications-service:1 \
  --desired-count 3 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-xxxxx,subnet-yyyyy],securityGroups=[sg-zzzzz],assignPublicIp=ENABLED}" \
  --load-balancers "targetGroupArn=arn:aws:elasticloadbalancing:us-east-1:account-id:targetgroup/notifications/xxxxx,containerName=notifications-service,containerPort=80"
```

## Environment Configuration

### Development

`application-dev.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/notifications_db
spring.jpa.hibernate.ddl-auto=create-drop
logging.level.root=DEBUG
```

### Staging

`application-staging.properties`:
```properties
spring.datasource.url=jdbc:postgresql://postgres-staging:5432/notifications_db
spring.jpa.hibernate.ddl-auto=validate
logging.level.root=INFO
management.endpoints.web.exposure.include=health,metrics,prometheus
```

### Production

`application-prod.properties`:
```properties
spring.datasource.url=jdbc:postgresql://postgres-prod:5432/notifications_db
spring.jpa.hibernate.ddl-auto=validate
spring.datasource.hikari.maximum-pool-size=20
logging.level.root=WARN
logging.level.notifications.microservice=INFO
management.endpoints.web.exposure.include=health,prometheus
server.error.include-message=never
server.error.include-stacktrace=never
```

### Running with Profiles

```bash
# Development
mvn spring-boot:run -Dspring-boot.run.arguments='--spring.profiles.active=dev'

# Staging
java -jar app.jar --spring.profiles.active=staging

# Production
java -jar app.jar --spring.profiles.active=prod
```

## Production Checklist

### Security

- [ ] Enable HTTPS/TLS on all endpoints
- [ ] Configure Spring Security for API authentication
- [ ] Use secrets manager (AWS Secrets Manager, HashiCorp Vault)
- [ ] Enable database encryption at rest
- [ ] Configure network policies/security groups
- [ ] Enable audit logging
- [ ] Rotate credentials regularly
- [ ] Use private VPC for databases

### Monitoring & Alerting

- [ ] Configure CloudWatch/Datadog agents
- [ ] Set up Prometheus alerts
- [ ] Configure Grafana dashboards
- [ ] Set up PagerDuty/Slack alerts
- [ ] Monitor CPU, memory, disk usage
- [ ] Monitor database connection pool
- [ ] Monitor Kafka consumer lag
- [ ] Set up log aggregation (ELK, CloudWatch Logs)

### Database

- [ ] Enable automated backups (daily minimum)
- [ ] Test restore procedure
- [ ] Monitor query performance
- [ ] Analyze slow queries
- [ ] Configure connection pooling
- [ ] Set up replication for HA
- [ ] Monitor disk space

### Application

- [ ] Health checks configured and working
- [ ] Graceful shutdown implemented
- [ ] Readiness probes configured
- [ ] Resource limits set (CPU, memory)
- [ ] Request timeouts configured
- [ ] Circuit breaker thresholds tuned
- [ ] Retry policies reviewed
- [ ] Error handling and fallbacks working

### Kafka

- [ ] Partition count aligned with throughput
- [ ] Replication factor set (minimum 3)
- [ ] Retention policy configured
- [ ] Consumer lag monitored
- [ ] DLQ topics created
- [ ] Broker monitoring enabled

### Deployment

- [ ] Blue-green or canary deployments configured
- [ ] Rollback procedure tested
- [ ] Database migration strategy defined
- [ ] Load testing performed
- [ ] Disaster recovery plan documented
- [ ] Change management process in place
- [ ] Post-deployment validation checklist created

### Documentation

- [ ] API documentation updated
- [ ] Runbooks created
- [ ] Troubleshooting guide created
- [ ] On-call rotation documented
- [ ] Escalation procedures defined

## Monitoring in Production

### Key Metrics to Watch

```
CPU Utilization > 80% → Scale up
Memory Usage > 85% → Increase heap or scale
DB Connection Pool > 90% → Check for connection leaks
Kafka Consumer Lag > 1000 messages → Increase consumers
Error Rate > 1% → Investigate errors
P95 Latency > 1s → Investigate performance
Circuit Breaker Open → Check downstream service
Failed Messages in DLQ → Manual investigation required
```

### CloudWatch Alarms

```bash
aws cloudwatch put-metric-alarm \
  --alarm-name notifications-error-rate \
  --alarm-description "Alert if error rate exceeds 1%" \
  --metric-name notifications.failed.total \
  --namespace spring_app \
  --statistic Sum \
  --period 300 \
  --threshold 50 \
  --comparison-operator GreaterThanThreshold \
  --alarm-actions arn:aws:sns:us-east-1:account-id:alerts
```

## Zero-Downtime Deployment

### Strategy: Rolling Update

```bash
# Kubernetes automatically handles rolling updates
kubectl set image deployment/notifications-service \
  notifications-service=new-image:tag \
  -n notifications --record

# Monitor rollout progress
kubectl rollout status deployment/notifications-service -n notifications

# If issues, rollback automatically
kubectl rollout undo deployment/notifications-service -n notifications
```

### Pre-deployment Tasks

1. Run database migrations
2. Validate new configuration
3. Run smoke tests
4. Check service dependencies
5. Notify stakeholders

### Post-deployment Validation

1. Health check passes
2. Metrics are being reported
3. No error rate spike
4. Database queries performing well
5. Consumer lag stable


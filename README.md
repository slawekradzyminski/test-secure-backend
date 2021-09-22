## Direct run

```commandline
./mvnw clean package spring-boot:repackage
java -jar target/jwt-auth-service-1.0.0.jar
```

## Docker run

```commandline
docker build --tag=jwt-auth-service:latest .
docker run -p4000:4000 jwt-auth-service:latest
```

## Verification

[Swagger](http://localhost:4000/swagger-ui.html)

[Database](http://localhost:4000/h2-console) login as root/root

## Existing users

- client/client (CLIENT role)
- admin/admin (ADMIN role)

## Prometheus & Graphana

[Article](https://stackabuse.com/monitoring-spring-boot-apps-with-micrometer-prometheus-and-grafana/)
[JVM Dashboard id](https://grafana.com/grafana/dashboards/4701)

Remember to change your local ip in prometheus.yml

```commandline
ifconfig | grep 192.168
```

Remember to change path to your prometheus.yml

```commandline
docker run -d -p 9090:9090 -v ~/IdeaProjects/test-secure-backend/src/main/resources/prometheus.yml:/etc/prometheus/prometheus.yml prom/prometheus
docker run -d -p 3000:3000 grafana/grafana
```

Docker cleanup

```commandline
docker stop $(docker ps -a -q) && docker rm $(docker ps -a -q)
```
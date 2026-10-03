# One Dockerfile builds any service.
#
#   docker build -f infra/docker/Dockerfile --build-arg SERVICE=ledger-service -t bank/ledger-service .
#
# Stage 1 compiles with the full JDK. Stage 2 keeps only the runtime and the jar, runs as a normal user
# and never as root.
FROM eclipse-temurin:21-jdk AS build
ARG SERVICE
WORKDIR /src
COPY . .
RUN ./mvnw -B -ntp -pl services/${SERVICE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG SERVICE
RUN useradd --system --uid 10001 --no-create-home app
COPY --from=build /src/services/${SERVICE}/target/${SERVICE}-*.jar /app/app.jar
USER 10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

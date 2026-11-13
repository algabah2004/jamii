# API de Jamii. Construire depuis apps/api :  docker build -t jamii-api apps/api
# 1. Compilation (JDK + Maven fourni par ./mvnw)
FROM eclipse-temurin:21-jdk AS compilation
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/api-*.jar /app.jar

# 2. Exécution (JRE seul, utilisateur sans privilège)
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 jamii
USER jamii
COPY --from=compilation /app.jar /app/app.jar
# 512 Mo sur l'offre de base : voir apps/api/README.md (« Déployer avec 512 Mo de mémoire »).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

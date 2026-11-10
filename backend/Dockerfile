# API FinanTrack: imagem de produção.
# Contexto de build: a pasta backend/ (docker build backend).

# --- Build: compila o JAR sem rodar os testes (o CI já os executa). ---
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /app

# Dependências primeiro: mudam pouco e ficam em cache entre builds.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp -q package -DskipTests \
    && cp target/finantrack-api-*.jar app.jar

# --- Execução: só o JRE, sem código-fonte nem Maven. ---
FROM eclipse-temurin:21-jre-noble
WORKDIR /app

RUN groupadd --system finantrack \
    && useradd --system --gid finantrack --no-create-home --shell /usr/sbin/nologin finantrack

COPY --from=build --chown=finantrack:finantrack /app/app.jar app.jar
USER finantrack

# Segredos e endereços chegam por variáveis de ambiente (ver .env.example); nada fica na imagem.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

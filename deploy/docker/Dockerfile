FROM eclipse-temurin:21-jre
WORKDIR /opt/aether
ARG AETHER_JAR=build/libs/Aether-AI-Server.jar
COPY ${AETHER_JAR} /opt/aether/Aether-AI-Server.jar
USER 10001:10001
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/opt/aether/Aether-AI-Server.jar"]
CMD ["--config", "/etc/aether/aether-server.yml"]

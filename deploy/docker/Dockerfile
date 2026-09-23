FROM eclipse-temurin:21-jre
WORKDIR /opt/aether
ARG AETHER_JAR=build/libs/Aether-Gateway.jar
COPY ${AETHER_JAR} /opt/aether/Aether-Gateway.jar
USER 10001:10001
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/opt/aether/Aether-Gateway.jar"]
CMD ["--config", "/etc/aether/aether-server.yml"]

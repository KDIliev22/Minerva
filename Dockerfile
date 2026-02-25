FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/minerva-1.0.0.jar app.jar
COPY natives ./natives
EXPOSE 4567 4568 6881/udp
ENTRYPOINT ["sh", "-c", "java -Djava.library.path=/app/natives/lib/x86_64 -jar app.jar"]
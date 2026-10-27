FROM eclipse-temurin:21-jdk AS build
WORKDIR /backend
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /backend/build/install/touchgrass-backend .
# Render Free hat 512 MB RAM und 0,1 CPU: halber Speicher für den Heap, schneller Start statt Spitzenleistung.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50 -XX:TieredStopAtLevel=1"
CMD ["/app/bin/touchgrass-backend"]

# The jar is platform-independent, so build it once on the native platform
# instead of under QEMU for every target architecture.
FROM --platform=$BUILDPLATFORM docker.io/library/maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package

FROM docker.io/library/eclipse-temurin:25-jre
COPY --from=build /build/target/wg2mqtt-1.0-SNAPSHOT.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY common/pom.xml common/pom.xml
COPY infrastructure/pom.xml infrastructure/pom.xml
COPY modules/identity/pom.xml modules/identity/pom.xml
COPY modules/driver/pom.xml modules/driver/pom.xml
COPY modules/location/pom.xml modules/location/pom.xml
COPY modules/pricing/pom.xml modules/pricing/pom.xml
COPY modules/dispatch/pom.xml modules/dispatch/pom.xml
COPY modules/trip/pom.xml modules/trip/pom.xml
COPY modules/booking/pom.xml modules/booking/pom.xml
COPY modules/payment/pom.xml modules/payment/pom.xml
COPY modules/rating/pom.xml modules/rating/pom.xml
COPY modules/notification/pom.xml modules/notification/pom.xml
COPY start/pom.xml start/pom.xml
RUN ./mvnw -q -B dependency:go-offline

COPY common/src common/src
COPY infrastructure/src infrastructure/src
COPY modules/identity/src modules/identity/src
COPY modules/driver/src modules/driver/src
COPY modules/location/src modules/location/src
COPY modules/pricing/src modules/pricing/src
COPY modules/dispatch/src modules/dispatch/src
COPY modules/trip/src modules/trip/src
COPY modules/booking/src modules/booking/src
COPY modules/payment/src modules/payment/src
COPY modules/rating/src modules/rating/src
COPY modules/notification/src modules/notification/src
COPY start/src start/src
RUN ./mvnw -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/start/target/start-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Ho_Chi_Minh", "-jar", "app.jar"]

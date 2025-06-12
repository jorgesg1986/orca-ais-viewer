# docker/Dockerfile

# ---- Build Stage ----
# This image provides SBT pre-installed.
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.7_6_1.11.2_2.13.16 AS build
WORKDIR /app

# Copy build definition files first to leverage Docker's layer caching.
# If your build.sbt or project/ files don't change, Docker can reuse this layer.
COPY build.sbt ./
COPY project/build.properties ./project/
COPY project/plugins.sbt ./project/

RUN sbt update
# Copy the rest of your application's source code.
COPY backend/src ./backend/src/

# Build the application using 'sbt assembly'.
RUN sbt compile
RUN sbt assembly

# Use a slim JRE (Java Runtime Environment) image for a smaller final footprint.
# We only need the JRE to run the compiled Scala application.
FROM alpine/java:21-jre
WORKDIR /app

# Expose the port.
EXPOSE 8088

# Copy the jar from the 'build' stage.
COPY --from=build /app/backend/target/scala-2.13/app.jar ./app.jar

# Command to run the application.
# The 'stage' command generates a startup script in the 'bin' directory
# named after your SBT project.
# IMPORTANT: Replace 'your-project-name' with the actual name of your SBT project.
# You can find this name in your 'build.sbt' file, typically defined as `name := "your-project-name"`.
ENTRYPOINT ["java", "-jar", "app.jar"]

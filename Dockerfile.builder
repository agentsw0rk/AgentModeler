FROM gradle:8.14-jdk21
WORKDIR /project
ENTRYPOINT ["gradle", "--no-daemon"]

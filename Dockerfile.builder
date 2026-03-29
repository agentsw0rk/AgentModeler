FROM gradle:8.10-jdk21
WORKDIR /project
ENTRYPOINT ["gradle", "--no-daemon"]

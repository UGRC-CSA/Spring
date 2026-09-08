.PHONY: compile test dm-preview
ifeq ($(OS),Windows_NT)
MVNW = ./mvnw.cmd
else
MVNW = ./mvnw
endif

compile:
	$(MVNW) clean compile

test:
	$(MVNW) test

dm-preview:
	$(MVNW) package -DskipTests
	java -Ddm.preview=true -jar target/spring-0.0.1-SNAPSHOT.jar --spring.profiles.active=dm-preview

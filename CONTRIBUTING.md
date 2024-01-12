# Contributing

There are two ways to consume or contribute to this project. Since this is a simple java application, you can install jdk 21 and make changes and compile.
1. Install intellij idea community edition
2. Open the build.gradle file as a project
3. Then to compile just run 

## After setup is done, how to run locally
1. ```./gradlew clean build```
2. ```java -jar build/libs/k3s-simplified-<version>-all.jar create --config ~/Projects/easystartup-account/private/java-simplified-k3s-test/cluster_config.yaml```

## Why not using GraalVM native image:
1. I did explore using a GraalVM native image
2. But it lead to too many issues with reflection and deserialization and I have to ensure that while running the GraalVM agent for every change that I explore all the application pathways for the agent to compute the pathways and allow reflections
3. I do not feel it is worth the effort in doing it, mainly because of additional bugs that can come up during runtime
4. Instead people can download a slightly larger package bundled with JRE itself which can avoid all these problems.
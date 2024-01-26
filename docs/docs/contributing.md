---
sidebar_position: 70
title: Contributing
---

# Contributing

There are two ways to consume or contribute to this project. Since this is a simple java application, you can install jdk 21 and make changes and compile.
1. Install intellij idea community edition
2. Open the build.gradle file as a project
3. Then to compile just run 

## After setup is done, how to run locally
1. ```./gradlew clean build```
2. ```java -jar build/libs/k3s-simplified-<version>.jar create --config ~/Projects/easystartup-io/k3s-simplified/cluster_config.yaml```

## Why am I not using GraalVM native image:
1. I did explore using a GraalVM native image
2. But it lead to too many issues with reflection and deserialization and I have to ensure that while running the GraalVM agent for every change that I explore all the application pathways for the agent to compute the pathways and allow reflections
3. I do not feel it is worth the effort in doing it, mainly because of additional bugs that can come up during runtime
4. Instead people can download a slightly larger package bundled with JRE itself which can avoid all these problems.

## To test release packages
1. There is a build pipeline which creates artifact when you push to `test-release` branch
2. You can push to that branch and check your github actions, you should be able to find the artifact to download and test where ever you want
3. The link_to_artifact_file should be output in the last step of github action pipeline
3. To download your modified k3s-simplified and run it just run 
    ```
    wget <link_to_your_artifact_file>
    tar -xf k3s-simplified.tar.gz
    cd k3s-simplified
    chmod +x install.sh
    sudo ./install.sh
    cd ..
    ```